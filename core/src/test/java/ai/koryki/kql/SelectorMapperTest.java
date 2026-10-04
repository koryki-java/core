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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.antlr.RangeException;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.IQLQueryMapper;
import ai.koryki.iql.IQLReader;
import ai.koryki.iql.IQLSerializer;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.Visitor;
import ai.koryki.iql.Walker;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
import ai.koryki.iql.query.Query;
import ai.koryki.iql.query.Selection;
import ai.koryki.iql.query.Selector;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Selectors ({@code c.tags@1}, {@code @'a key'}) and parameters ({@code @x}) from the grammar into
 * the bean, through IQL and back, and out again as KQL. Mapping only: what a step means for a type,
 * and its SQL, come later, so the tests use plausible column names and never resolve or render.
 */
class SelectorMapperTest {

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

    /** The expressions of the FETCH items, outermost only (the ones whose parent is an Out). */
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

    private static Expression fetched(String expression) throws IOException {
        return expressions(bean("FIND products p FETCH " + expression)).get(0);
    }

    private static List<String> texts(List<Selector> selectors) {
        return selectors.stream().map(IQLSerializer::selector).toList();
    }

    private static Selector token(String token) {
        return IQLQueryMapper.toSelector(token);
    }

    // ---- the token ----------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(
            strings = {
                "@1",
                "@-1",
                "@1:3",
                "@2:",
                "@:3",
                "@-2:-1",
                "@name",
                "@x1y",
                "@'Order Id'",
                "@'it\\'s'",
            })
    void aTokenReadsAndWritesItselfBack(String token) {
        assertEquals(token, IQLSerializer.selector(token(token)));
    }

    @Test
    void eachWayOfWritingFillsItsOwnMember() {
        Selector position = token("@-1");
        assertEquals(-1, position.getIndex());
        assertNull(position.getFrom());
        assertNull(position.getTo());
        assertNull(position.getId());
        assertNull(position.getName());

        Selector openEnd = token("@2:");
        assertNull(openEnd.getIndex());
        assertEquals(2, openEnd.getFrom());
        assertNull(openEnd.getTo());

        Selector openStart = token("@:3");
        assertNull(openStart.getIndex());
        assertNull(openStart.getFrom());
        assertEquals(3, openStart.getTo());

        Selector id = token("@name");
        assertEquals("name", id.getId());
        assertNull(id.getName());

        Selector quoted = token("@'name'");
        assertEquals("name", quoted.getName());
        assertNull(quoted.getId());
    }

    @Test
    void aQuotedNameIsUnescapedAndWrittenEscapedAgain() {
        Selector s = token("@'it\\'s'");

        assertEquals("it's", s.getName());
        assertEquals("@'it\\'s'", IQLSerializer.selector(s));
    }

    @Test
    void aLeadingZeroIsNormalised() {
        assertEquals("@1", IQLSerializer.selector(token("@01")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "@", "@99999999999", "@1:99999999999"})
    void notASelectorOrOutOfRangeIsRefused(String token) {
        assertThrows(KorykiaiException.class, () -> IQLQueryMapper.toSelector(token));
    }

    // ---- the bean -----------------------------------------------------------------------------

    @Test
    void aChainIsOneNodeBaseFirst() throws IOException {
        Expression e = fetched("p.m@'k'@1");

        Selection selection = e.getSelection();
        assertNotNull(selection);
        assertEquals("m", selection.getBase().getField().getName());
        assertEquals(List.of("@'k'", "@1"), texts(selection.getSelectors()));
        assertNull(e.getField(), "the selection is the expression, not its base");
    }

    @Test
    void theBaseCanBeAnyExpression() throws IOException {
        Selection selection = fetched("lower(p.product_name)@2").getSelection();

        assertEquals("lower", selection.getBase().getFunction().getFunc());
    }

    @Test
    void aParenthesisedBaseKeepsItsParentheses() throws IOException {
        Selection selection = fetched("(p.unit_price)@1").getSelection();

        assertTrue(selection.getBase().isParenthesized());
    }

    @Test
    void aSelectorBindsTighterThanTheSignAndTheOperators() throws IOException {
        Expression negated = fetched("-p.unit_price@1");
        assertEquals("negate", negated.getFunction().getFunc());
        assertNotNull(negated.getFunction().getArguments().get(0).getSelection());

        Expression product = fetched("p.unit_price@1 * 2");
        assertEquals("multiply", product.getFunction().getFunc());
        assertNotNull(product.getFunction().getArguments().get(0).getSelection());
        assertNull(product.getFunction().getArguments().get(1).getSelection());
    }

    @Test
    void aSelectorIsAnArgumentLikeAnyValue() throws IOException {
        Expression e = fetched("coalesce(p.tags@1, 'none')");

        assertNotNull(e.getFunction().getArguments().get(0).getSelection());
    }

    @Test
    void aParameterIsNamedWithoutItsSigil() throws IOException {
        Expression e = fetched("@x");

        assertEquals("x", e.getParameter());
        assertNull(e.getSelection());
    }

    @Test
    void aSelectorOnAParameterIsASelection() throws IOException {
        Selection selection = fetched("@it@price").getSelection();

        assertEquals("it", selection.getBase().getParameter());
        assertEquals(List.of("@price"), texts(selection.getSelectors()));
    }

    // ---- through IQL and back -----------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(
            strings = {
                "p.tags@1",
                "p.tags@-1",
                "p.tags@1:3",
                "p.tags@2:",
                "p.tags@:3",
                "p.s@name",
                "p.m@'Order Id'",
                "p.m@'it\\'s'@1",
                "p.s@a@b@2",
                "(p.tags)@1",
                "lower(p.name)@2",
                "-p.a@1",
                "p.a@1 + p.b@2",
                "coalesce(p.tags@1, 'none')",
                "@x",
                "@it@price",
            })
    void iqlWrittenAndReadBackIsTheSameIql(String expression) throws IOException {
        String once = iql(bean("FIND products p FETCH " + expression));

        String twice = iql(fromIql(once));

        assertEquals(once, twice);
    }

    @Test
    void theIqlWritesTheStepsAttached() throws IOException {
        String iql = iql(bean("FIND products p FETCH p.m@'k'@1:2"));

        assertTrue(iql.contains("p.m@'k'@1:2"), iql);
    }

    @Test
    void theIqlTreeHoldsTheSameSelection() throws IOException {
        Expression kql = fetched("p.s@a@2");
        Expression viaIql = expressions(fromIql(iql(bean("FIND products p FETCH p.s@a@2")))).get(0);

        assertEquals(
                texts(kql.getSelection().getSelectors()),
                texts(viaIql.getSelection().getSelectors()));
        assertEquals(
                kql.getSelection().getBase().getField().getName(),
                viaIql.getSelection().getBase().getField().getName());
    }

    // ---- the visitor reaches into the base ----------------------------------------------------

    @Test
    void theWalkerVisitsTheFunctionsInsideABase() throws IOException {
        List<String> functions = new ArrayList<>();
        new Walker()
                .walk(
                        bean("FIND products p FETCH lower(p.product_name)@2"),
                        new Visitor() {
                            @Override
                            public boolean visit(Deque<Object> deque, Function function) {
                                functions.add(function.getFunc());
                                return true;
                            }
                        });

        assertEquals(List.of("lower"), functions);
    }

    // ---- KQL out again ------------------------------------------------------------------------

    private static String reformat(String kql) {
        KQLTranspiler t = KQLTranspiler.builder(kql, resolver).build();
        return new KQLFormatter(t.getCtx(), t.getDescription()).format();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "p.product_name@1",
                "p.product_name@-1",
                "p.product_name@1:3",
                "p.product_name@'a key'",
                "p.product_name@a@b",
                "p.unit_price@1 * 2",
            })
    void theFormatterKeepsTheStepsAndIsStable(String expression) {
        String out = reformat("FIND products p\nFETCH " + expression);

        assertTrue(out.contains(expression.replace("p.product_name", "p.product_name")), out);
        assertEquals(out, reformat(out));
    }

    // ---- what is not mapped yet ---------------------------------------------------------------

    @Test
    void aLambdaIsRefusedWithAPositionNotWithAnNpe() {
        for (String lambda : List.of("x -> @x", "(a, b) -> @a + @b")) {
            RangeException e =
                    assertThrows(
                            RangeException.class,
                            () ->
                                    bean(
                                            "FIND products p FETCH list_transform(p.tags, "
                                                    + lambda
                                                    + ")"),
                            lambda);
            assertTrue(e.getMessage().contains("lambda"), e.getMessage());
        }
    }
}
