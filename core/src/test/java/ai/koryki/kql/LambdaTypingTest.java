/*
 * Copyright 2025-2026 Johannes Zemlin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package ai.koryki.kql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.databases.complex.duckdb.ComplexService;
import ai.koryki.iql.BlockLeadingSourceCollector;
import ai.koryki.iql.BlockRegistryCollector;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.IQLVisibilityContext;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SelectScopeCollector;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.Visitor;
import ai.koryki.iql.Walker;
import ai.koryki.iql.functions.StandardFunctions;
import ai.koryki.iql.query.Block;
import ai.koryki.iql.query.Query;
import ai.koryki.iql.query.Select;
import ai.koryki.iql.query.Source;
import ai.koryki.iql.typing.ExpressionTypeResolver;
import ai.koryki.iql.validate.ValidateException;
import ai.koryki.iql.validate.Validator;
import ai.koryki.iql.validate.Violation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.RuleContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Lambda arguments on the complex test bed: what the parameters are typed as, what the call
 * returns, and what is refused. The same {@code x} is an INTEGER over a list of integers and a
 * struct over a list of structs, so the types are the point; the engine tests show the values.
 */
class LambdaTypingTest {

    private static LinkResolver resolver;

    @BeforeAll
    static void readComplexCatalog() {
        resolver = ComplexService.resolver();
    }

    private record Mapped(
            Query query,
            Map<Object, RuleContext> iqlToContext,
            Map<String, Source> leading,
            IQLVisibilityContext visibility,
            Select root) {}

    private static Mapped map(String expression) throws IOException {
        KQLReader reader = new KQLReader("FIND check_complex t FETCH " + expression, true);
        KQLQueryMapper mapper =
                new KQLQueryMapper(resolver, reader.getCtx(), reader.getDescription());
        Query query = mapper.toBean();
        Map<Object, RuleContext> iqlToContext = mapper.getIqlToContext();
        Map<Object, Map<String, Source>> scopes =
                Walker.apply(query, new SelectScopeCollector(iqlToContext));
        Map<String, Source> leading = Walker.apply(query, new BlockLeadingSourceCollector());
        Map<String, Block> blocks = Walker.apply(query, new BlockRegistryCollector());
        IQLVisibilityContext visibility = new IQLVisibilityContext(blocks, leading, scopes);
        List<Select> selects = new ArrayList<>();
        new Walker()
                .walk(
                        query,
                        new Visitor() {
                            @Override
                            public boolean visit(Deque<Object> deque, Select select) {
                                selects.add(select);
                                return true;
                            }
                        });
        return new Mapped(query, iqlToContext, leading, visibility, selects.get(0));
    }

    private static String typeOf(String expression) throws IOException {
        Mapped m = map(expression);
        ExpressionTypeResolver types =
                new ExpressionTypeResolver(
                        resolver,
                        m.visibility().child(m.root()),
                        DuckdbBaseDialect.INSTANCE.getFunctionRenderer());
        TypeDescriptor type =
                types.resolve(SqlQueryRenderer.collectOut(m.root()).get(0).getExpression());
        return type.portable();
    }

    private static List<Violation> violations(String expression) throws IOException {
        Mapped m = map(expression);
        return new Validator(
                        m.query(),
                        resolver,
                        m.leading(),
                        m.iqlToContext(),
                        DuckdbBaseDialect.INSTANCE.getFunctionRenderer(),
                        m.visibility())
                .validate();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "list_transform(t.tags, x -> upper(x))|ARRAY(TEXT)",
                "list_transform(t.scores, x -> x * 2)|ARRAY(INTEGER)",
                // the second parameter is the position
                "list_transform(t.scores, (x, i) -> x * i)|ARRAY(INTEGER)",
                // the parameter has the type of the element: a struct, and a step selects from it
                "list_transform(t.items, it -> it@price)|ARRAY(DECIMAL)",
                "list_transform(t.items, it -> it@opts)|ARRAY(ARRAY(TEXT))",
                "list_transform(t.events, e -> e@day)|ARRAY(DATE)",
                "list_transform(t.matrix, row -> row@1)|ARRAY(FLOAT)",
                "list_filter(t.scores, x -> x > 10)|ARRAY(INTEGER)",
                // a filter of a fixed-size array is a list
                "list_filter(t.vec, x -> x > 1)|ARRAY(FLOAT)",
                "list_filter(t.items, it -> it@qty > 1)|ARRAY(STRUCT(sku:TEXT,qty:INTEGER,price:DECIMAL,opts:ARRAY(TEXT)))",
                "list_reduce(t.scores, (a, b) -> a + b)|INTEGER",
                "list_reduce(t.tags, (a, b) -> a + b)|TEXT",
                "list_transform(t.items, it -> list_transform(it@opts, o -> upper(o)))|ARRAY(ARRAY(TEXT))",
                // a lambda inside a lambda sees both
                "list_transform(t.scores, x -> list_transform(t.scores, y -> x + y))|ARRAY(ARRAY(INTEGER))",
                // and the result is a list like any other
                "list_transform(t.tags, x -> x)@1|TEXT",
                "list_transform(t.items, it -> it@sku)@2:3|ARRAY(TEXT)",
            })
    void theCallReturnsWhatTheLambdaReturns(String expression, String expected) throws IOException {
        assertEquals(expected, typeOf(expression));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "list_transform(t.tags, x -> y)|is not a parameter",
                "list_transform(t.tags, (x, x) -> x)|twice",
                "list_transform(t.items, x -> list_transform(x@opts, x -> x))|hides",
                "list_transform(t.tags, (a, b, c) -> a)|1 or 2 parameters",
                "list_reduce(t.scores, x -> x)|2 parameters",
                "upper(x -> x)|takes no lambda",
                "x|is not a parameter",
                "t.tags@1 + x|is not a parameter",
                "list_transform(t.scores, x -> sum(x))|aggregate sum",
                "list_transform(t.scores, x -> row_number() OVER (ORDER t.nr))|window function",
                "list_transform(t.scores, x -> (FIND check_complex u FETCH count(u)))|subselect",
            })
    void aLambdaThatIsWrongIsAViolation(String expression, String message) throws IOException {
        List<Violation> violations = violations(expression);

        assertTrue(
                violations.stream().anyMatch(v -> v.getMessage().contains(message)),
                () ->
                        message
                                + " not in "
                                + violations.stream().map(Violation::getMessage).toList());
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "list_transform(t.nr, x -> x)",
                "list_transform(t.tags, 'x')",
                "list_filter(t.tags, 'x')",
            })
    void anArgumentOfTheWrongKindIsRefused(String expression) {
        Assertions.assertThrows(
                ValidateException.class,
                () ->
                        KQLTranspiler.builder("FIND check_complex t FETCH " + expression, resolver)
                                .functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer())
                                .build()
                                .getSql(
                                        new SqlQueryRenderer(
                                                DuckdbBaseDialect.INSTANCE,
                                                java.time.ZoneId.of("UTC"))),
                expression);
    }

    @Test
    void theStandardCatalogRejectsTheListFunctionsAndDuckdbAcceptsThem() {
        assertTrue(StandardFunctions.registry().lookup("list_transform", 2).isUnsupported());
        assertTrue(StandardFunctions.registry().lookup("list_filter", 2).isUnsupported());
        assertTrue(StandardFunctions.registry().lookup("list_reduce", 2).isUnsupported());
    }
}
