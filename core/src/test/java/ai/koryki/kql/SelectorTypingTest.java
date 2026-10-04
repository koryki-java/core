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
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import ai.koryki.iql.query.Block;
import ai.koryki.iql.query.Query;
import ai.koryki.iql.query.Select;
import ai.koryki.iql.query.Source;
import ai.koryki.iql.typing.ExpressionTypeResolver;
import ai.koryki.iql.validate.SelectorValidator;
import ai.koryki.iql.validate.ValidateException;
import ai.koryki.iql.validate.Violation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.RuleContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What a selector chain does to a type, on the columns of the complex test bed: the same {@code @1}
 * is a list position on a list, a key on a map with integer keys and a field position on a struct,
 * and {@code @name} a field or a text key. Checked through the real chain: the KQL is mapped, the
 * type of the base is resolved in its scope, the steps are applied.
 */
class SelectorTypingTest {

    private static LinkResolver resolver;

    @BeforeAll
    static void readComplexCatalog() {
        resolver = ComplexService.resolver();
    }

    private record Typed(
            Query query,
            Map<Object, RuleContext> iqlToContext,
            IQLVisibilityContext visibility,
            Select root) {}

    private static Typed map(String kql) throws IOException {
        KQLReader reader = new KQLReader(kql, true);
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
        return new Typed(query, iqlToContext, visibility, selects.get(0));
    }

    /** The portable type of the one FETCH item, resolved in the scope of its select. */
    private static String typeOf(String expression) throws IOException {
        Typed t = map("FIND check_complex t FETCH " + expression);
        ExpressionTypeResolver types =
                new ExpressionTypeResolver(
                        resolver,
                        t.visibility().child(t.root()),
                        DuckdbBaseDialect.INSTANCE.getFunctionRenderer());
        TypeDescriptor type =
                types.resolve(SqlQueryRenderer.collectOut(t.root()).get(0).getExpression());
        return type.portable();
    }

    private static List<Violation> violations(String expression) throws IOException {
        Typed t = map("FIND check_complex t FETCH " + expression);
        SelectorValidator v =
                new SelectorValidator(
                        t.iqlToContext(),
                        DuckdbBaseDialect.INSTANCE.getFunctionRenderer(),
                        resolver,
                        t.visibility());
        new Walker().walk(t.query(), v);
        return v.collect();
    }

    // ---- lists --------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "t.tags@1|TEXT",
                "t.tags@-1|TEXT",
                "t.scores@2|INTEGER",
                "t.tags@1:2|ARRAY(TEXT)",
                "t.tags@2:|ARRAY(TEXT)",
                "t.tags@:2|ARRAY(TEXT)",
                // a slice of a fixed-size array is a list: its length depends on the bounds
                "t.vec@1:2|ARRAY(FLOAT)",
                "t.vec@1|FLOAT",
                "t.matrix@1|ARRAY(FLOAT,3)",
                "t.matrix@2@1|FLOAT",
                "t.tags@1:2@1|TEXT",
            })
    void listsGiveElementsAndLists(String expression, String expected) throws IOException {
        assertEquals(expected, typeOf(expression));
    }

    // ---- structs ------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "t.dims@w|FLOAT",
                "t.dims@'w'|FLOAT",
                // DuckDB field names are case-blind
                "t.dims@'W'|FLOAT",
                "t.dims@1|FLOAT",
                "t.dims@-1|FLOAT",
                "t.address@geo|STRUCT(lat:FLOAT,lon:FLOAT)",
                "t.address@geo@lat|FLOAT",
                "t.address@phones@1|TEXT",
                "t.address@phones|ARRAY(TEXT)",
                "t.odd@'Odd Name'|INTEGER",
                "t.odd@x|INTEGER",
                "t.odd@1|INTEGER",
            })
    void structsGiveFields(String expression, String expected) throws IOException {
        assertEquals(expected, typeOf(expression));
    }

    // ---- maps ---------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "t.attrs@color|TEXT",
                "t.attrs@'a key with spaces'|TEXT",
                "t.counts@a|INTEGER",
                // the same @1 that is a position on a list is a key on a map with integer keys
                "t.lookup@1|TEXT",
                "t.lookup@-1|TEXT",
            })
    void mapsGiveTheirValues(String expression, String expected) throws IOException {
        assertEquals(expected, typeOf(expression));
    }

    // ---- nesting ------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "t.items@1@sku|TEXT",
                "t.items@1@price|DECIMAL",
                "t.items@1@opts@1|TEXT",
                "t.items@2:3|ARRAY(STRUCT(sku:TEXT,qty:INTEGER,price:DECIMAL,opts:ARRAY(TEXT)))",
                "t.events@1@ts|TIMESTAMP",
                // list of maps (text keys) of structs holding a list: position, key, field,
                // position
                "t.deep@1@m@a@2|INTEGER",
                // ... and a map with integer keys to lists: a key, then a position
                "t.deep@1@m@b@1@1|TEXT",
            })
    void nestingFollowsEveryStep(String expression, String expected) throws IOException {
        assertEquals(expected, typeOf(expression));
    }

    @ParameterizedTest
    @ValueSource(strings = {"t.doc@a", "t.doc@a@b@1", "t.doc@1", "t.doc@'a key'"})
    void jsonStaysJson(String expression) throws IOException {
        assertEquals("JSON", typeOf(expression));
    }

    @Test
    void aBaseThatIsAnExpressionIsTypedToo() throws IOException {
        // coalesce keeps the structure of its branches: it used to rebuild the type from family and
        // name and hand back an ARRAY without an element
        assertEquals("TEXT", typeOf("coalesce(t.tags, t.tags)@1"));
        assertEquals(
                "ARRAY(STRUCT(sku:TEXT,qty:INTEGER,price:DECIMAL,opts:ARRAY(TEXT)))",
                typeOf("coalesce(t.items, t.items)"));
    }

    @Test
    void branchesOfDifferentStructuresDoNotMeet() {
        ValidateException e =
                assertThrows(
                        ValidateException.class,
                        () ->
                                KQLTranspiler.builder(
                                                "FIND check_complex t FETCH coalesce(t.tags, t.scores)",
                                                resolver)
                                        .functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer())
                                        .build()
                                        .getSql(
                                                new SqlQueryRenderer(
                                                        DuckdbBaseDialect.INSTANCE,
                                                        java.time.ZoneId.of("UTC"))));

        assertTrue(String.valueOf(e.getMessage()).contains("different structures"), e.getMessage());
    }

    // ---- what does not fit --------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "t.dims@z|no field 'z'",
                "t.dims@3|outside STRUCT(w:FLOAT,h:FLOAT)",
                "t.dims@-3|outside STRUCT(w:FLOAT,h:FLOAT)",
                "t.attrs@1|selects an integer key",
                "t.lookup@x|selects a text key",
                "t.tags@0|there is no position 0",
                "t.tags@0:2|there is no position 0",
                "t.tags@1:0|there is no position 0",
                "t.tags@name|by name",
                "t.nr@1|by position",
                "t.nr@name|by name",
                "t.tags@1@2|from TEXT",
                "t.dims@1:2|as a range",
                "t.attrs@1:2|as a range",
            })
    void aStepThatDoesNotFitIsAViolation(String expression, String message) throws IOException {
        List<Violation> violations = violations(expression);

        assertEquals(1, violations.size(), violations.toString());
        assertEquals("type", violations.get(0).getCategory());
        assertTrue(
                violations.get(0).getMessage().contains(message), violations.get(0).getMessage());
        assertTrue(violations.get(0).isError());
    }

    @Test
    void theFieldsAreNamedWhenOneIsMissing() throws IOException {
        String message = violations("t.dims@z").get(0).getMessage();

        assertTrue(message.contains("its fields are w, h"), message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"t.tags@1", "t.dims@w", "t.lookup@1", "t.doc@a@b@1"})
    void aStepThatFitsIsNoViolation(String expression) throws IOException {
        assertTrue(violations(expression).isEmpty());
    }

    @Test
    void theTranspilerStopsOnTheViolationBeforeItRenders() {
        ValidateException e =
                assertThrows(
                        ValidateException.class,
                        () ->
                                KQLTranspiler.builder(
                                                "FIND check_complex t FETCH t.dims@z", resolver)
                                        .functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer())
                                        .build()
                                        .getSql(
                                                new SqlQueryRenderer(
                                                        DuckdbBaseDialect.INSTANCE,
                                                        java.time.ZoneId.of("UTC"))));

        assertTrue(String.valueOf(e.getMessage()).contains("no field 'z'"), e.getMessage());
    }
}
