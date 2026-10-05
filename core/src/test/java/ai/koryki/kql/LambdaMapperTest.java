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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.IQLQueryMapper;
import ai.koryki.iql.IQLReader;
import ai.koryki.iql.IQLSerializer;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.Visitor;
import ai.koryki.iql.Walker;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
import ai.koryki.iql.query.Lambda;
import ai.koryki.iql.query.Query;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Lambda arguments from the grammar into the bean, through IQL and back, and out again as KQL.
 * Mapping only: what the parameters are typed as, and the SQL, are for the stages after.
 */
class LambdaMapperTest {

    private static LinkResolver resolver;

    @BeforeAll
    static void readNorthwind() {
        resolver = NorthwindService.resolver();
    }

    private static Query bean(String kql) throws IOException {
        KQLReader reader = new KQLReader(kql, true);
        return new KQLQueryMapper(resolver, reader.getCtx(), reader.getDescription()).toBean();
    }

    private static String iql(Query query) {
        return new IQLSerializer(query).toString();
    }

    private static Query fromIql(String iql) throws IOException {
        IQLReader reader = new IQLReader(iql, true);
        return new IQLQueryMapper(reader.getCtx(), reader.getDescription()).toScript();
    }

    private static List<Expression> expressions(Query query) {
        List<Expression> all = new ArrayList<>();
        new Walker()
                .walk(
                        query,
                        new Visitor() {
                            @Override
                            public boolean visit(Deque<Object> deque, Expression expression) {
                                all.add(expression);
                                return true;
                            }
                        });
        return all;
    }

    private static Lambda lambda(String call) throws IOException {
        return expressions(bean("FIND products p FETCH " + call)).stream()
                .map(Expression::getLambda)
                .filter(l -> l != null)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void oneParameterAndAValueBody() throws IOException {
        Lambda l = lambda("list_transform(p.tags, x -> upper(@x))");

        assertEquals(List.of("x"), l.getParameters());
        assertNull(l.getBody().getLogical());
        assertEquals("upper", l.getBody().getFunction().getFunc());
    }

    @Test
    void severalParametersInTheirOrder() throws IOException {
        Lambda l = lambda("list_reduce(p.scores, (acc, x) -> @acc + @x)");

        assertEquals(List.of("acc", "x"), l.getParameters());
        assertEquals("add", l.getBody().getFunction().getFunc());
        assertEquals("acc", l.getBody().getFunction().getArguments().get(0).getParameter());
    }

    @Test
    void aPredicateBodyIsALogicalExpression() throws IOException {
        Lambda l = lambda("list_filter(p.scores, x -> @x > 2 AND @x < 9)");

        assertNotNull(l.getBody().getLogical());
    }

    @Test
    void aComparisonAloneIsAPredicateToo() throws IOException {
        assertNotNull(lambda("list_filter(p.scores, x -> @x > 2)").getBody().getLogical());
    }

    @Test
    void aSelectorOnAParameterIsASelection() throws IOException {
        Lambda l = lambda("list_transform(p.items, it -> @it@price * 2)");

        Expression price = l.getBody().getFunction().getArguments().get(0);
        assertEquals("it", price.getSelection().getBase().getParameter());
    }

    @Test
    void lambdasNest() throws IOException {
        Lambda outer = lambda("list_transform(p.a, x -> list_transform(@x, y -> @y + 1))");

        Expression inner = outer.getBody().getFunction().getArguments().get(1);
        assertNotNull(inner.getLambda());
        assertEquals(List.of("y"), inner.getLambda().getParameters());
    }

    @Test
    void theCallItselfIsAnOrdinaryFunction() throws IOException {
        Function f =
                expressions(bean("FIND products p FETCH list_transform(p.tags, x -> @x)")).stream()
                        .map(Expression::getFunction)
                        .filter(fn -> fn != null)
                        .findFirst()
                        .orElseThrow();

        assertEquals("list_transform", f.getFunc());
        assertEquals(2, f.getArguments().size());
        assertNull(f.getArguments().get(0).getLambda());
        assertNotNull(f.getArguments().get(1).getLambda());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "list_transform(p.tags, x -> upper(@x))",
                "list_transform(p.tags, x -> @x)",
                "list_reduce(p.scores, (acc, x) -> @acc + @x)",
                "list_filter(p.scores, x -> @x > 2 AND @x < 9)",
                "list_filter(p.scores, x -> NOT @x = 3)",
                "list_filter(p.items, it -> @it@price > #min)",
                "list_transform(p.items, it -> @it@price * 2)",
                "list_transform(p.a, x -> list_transform(@x, y -> @y + 1))",
                "list_transform(p.tags, (x, i) -> @x)",
                "coalesce(list_transform(p.tags, x -> @x), p.tags)@1",
                "list_transform(p.tags, x -> @x)@1:2",
            })
    void iqlWrittenAndReadBackIsTheSameIql(String expression) throws IOException {
        String once = iql(bean("FIND products p FETCH " + expression));

        String twice = iql(fromIql(once));

        assertEquals(once, twice);
    }

    @Test
    void theIqlWritesTheLambdaWithItsArrow() throws IOException {
        String iql = iql(bean("FIND products p FETCH list_reduce(p.s, (a, b) -> @a + @b)"));

        assertTrue(iql.contains("list_reduce(p.s, (a, b) -> add(@a, @b))"), iql);
    }

    @Test
    void theIqlTreeHoldsTheSameLambda() throws IOException {
        Lambda kql = lambda("list_transform(p.tags, x -> upper(@x))");
        Lambda viaIql =
                expressions(
                                fromIql(
                                        iql(
                                                bean(
                                                        "FIND products p FETCH list_transform(p.tags,"
                                                                + " x -> upper(@x))"))))
                        .stream()
                        .map(Expression::getLambda)
                        .filter(l -> l != null)
                        .findFirst()
                        .orElseThrow();

        assertEquals(kql.getParameters(), viaIql.getParameters());
        assertEquals(
                kql.getBody().getFunction().getFunc(), viaIql.getBody().getFunction().getFunc());
    }

    @Test
    void theWalkerReachesTheBody() throws IOException {
        List<String> functions = new ArrayList<>();
        new Walker()
                .walk(
                        bean("FIND products p FETCH list_transform(p.tags, x -> upper(@x))"),
                        new Visitor() {
                            @Override
                            public boolean visit(Deque<Object> deque, Function function) {
                                functions.add(function.getFunc());
                                return true;
                            }
                        });

        assertEquals(List.of("list_transform", "upper"), functions);
    }

    private static String reformat(String kql) {
        KQLTranspiler t = KQLTranspiler.builder(kql, resolver).build();
        return new KQLFormatter(t.getCtx(), t.getDescription()).format();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "list_transform(p.product_name, x -> upper(@x))",
                "list_reduce(p.product_name, (a, b) -> @a + @b)",
                "list_filter(p.product_name, x -> @x > 2 AND @x < 9)",
            })
    void theFormatterKeepsTheLambdaAndIsStable(String call) {
        String out = reformat("FIND products p\nFETCH " + call);

        assertTrue(out.contains("->"), out);
        assertEquals(out, reformat(out));
    }
}
