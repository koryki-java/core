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

import java.io.IOException;
import java.util.Arrays;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Parse-only tests for the postfix selectors ({@code c.tags@1}, {@code s@name}, {@code m@'k'}) and
 * the lambda rules ({@code x -> x * 2}). Nothing here maps, types or renders; the point is that the
 * grammar accepts exactly these shapes and builds the tree the later phases will rely on.
 */
class SelectorLambdaGrammarTest {

    private static final String FIND = "FIND products p FETCH ";

    private static KQLParser.QueryContext parse(String kql) {
        try {
            // abort = true: a syntax error is an exception, not a recovered tree
            return new KQLReader(kql, true).getCtx();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String tree(String fetchExpression) {
        return parse(FIND + fetchExpression).toStringTree(Arrays.asList(KQLParser.ruleNames));
    }

    private static boolean has(ParseTree t, Class<?> type) {
        if (type.isInstance(t)) {
            return true;
        }
        for (int i = 0; i < t.getChildCount(); i++) {
            if (has(t.getChild(i), type)) {
                return true;
            }
        }
        return false;
    }

    // ---- selectors ----------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(
            strings = {
                "p.tags@1",
                "p.tags@-1",
                "p.tags@1:3",
                "p.tags@2:",
                "p.tags@:3",
                "p.tags@-2:-1",
                "p.s@name",
                "p.m@'Order Id'",
                "p.m@'k'@1",
                "p.s@a@b@2",
                "split(p.name, ',')@2",
                "(p.tags)@1",
                "p.a@1 + p.b@2",
                "-p.a@1",
            })
    void selectorsParse(String expression) {
        KQLParser.QueryContext q = parse(FIND + expression);
        assertTrue(has(q, KQLParser.SelectorContext.class), expression);
    }

    @Test
    void selectorBindsTighterThanThePrefixSign() {
        // -p.a@1 must be -(p.a@1): the selector sits inside the unary expression
        String t = tree("-p.a@1");
        int sign = t.indexOf("- ");
        int selector = t.indexOf("(selector");
        assertTrue(sign >= 0 && selector > sign, t);
        // the outer expression is the sign, not a selector applied to a negated field
        assertTrue(t.contains("(expression - (expression (expression (field"), t);
    }

    @Test
    void selectorBindsTighterThanMultiplication() {
        String t = tree("p.a@1 * 2");
        // the multiplication is the root: its left operand is the selector expression
        assertTrue(t.contains("(expression (expression (expression (field"), t);
    }

    @Test
    void selectorsChainLeftToRight() {
        String t = tree("p.m@'k'@1");
        assertEquals(2, count(t, "(selector"), t);
        assertTrue(t.indexOf("@'k'") < t.indexOf("@1"), t);
    }

    @ParameterizedTest
    @ValueSource(strings = {"p.tags@ 1", "p.tags@1 :3", "p.tags@", "p.tags@@1", "p.tags@1::"})
    void malformedSelectorsAreRejected(String expression) {
        assertThrows(RuntimeException.class, () -> parse(FIND + expression), expression);
    }

    // ---- lambdas ------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(
            strings = {
                "list_transform(p.tags, x -> x * 2)",
                "list_filter(p.tags, x -> x > 2 AND x < 9)",
                "list_transform(p.items, it -> it@price * it@qty)",
                "list_reduce(p.tags, (acc, x) -> acc + x)",
                "list_transform(p.tags, (x, i) -> x * i)",
                "list_transform(p.a, x -> list_transform(p.b, y -> x + y))",
                "list_transform(p.tags, x -> x + p.unit_price)",
                "list_filter(p.tags, x -> x@name LIKE 'A')",
                "list_transform(p.s, x -> x.price)",
            })
    void lambdasParse(String expression) {
        KQLParser.QueryContext q = parse(FIND + expression);
        assertTrue(has(q, KQLParser.LambdaContext.class), expression);
    }

    @Test
    void aLambdaIsAnArgument() {
        String t = tree("list_transform(p.tags, x -> x * 2)");
        assertTrue(t.contains("(argument (lambda"), t);
        assertTrue(t.contains("(lambda_params x)"), t);
    }

    @Test
    void aPrimarySelectorTokenIsAParameter() {
        String t = tree("list_transform(p.tags, x -> x)");
        assertTrue(t.contains("(param x)"), t);
        assertEquals(0, count(t, "(selector"), t);
    }

    @Test
    void aPostfixSelectorTokenAfterAParameterIsASelector() {
        String t = tree("list_transform(p.tags, x -> x@price)");
        assertTrue(t.contains("(param x)"), t);
        assertEquals(1, count(t, "(selector"), t);
    }

    @Test
    void twoParametersAreListed() {
        String t = tree("list_reduce(p.tags, (a, b) -> a + b)");
        assertTrue(t.contains("(lambda_params ( a , b ))"), t);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                // a lambda is no value of its own
                "x -> 1",
                "p.a + (x -> 1)",
                "(x -> 1) + 1",
                // malformed parameter lists
                "f(() -> 1)",
                "f((x,) -> 1)",
                "f((1) -> x)",
                "f(x ->)",
                "f(-> 1)",
                // the sigil of a parameter is gone: @x is a selector, and a selector needs a value
                "f(x -> @x)",
                "f((a, b) -> @a + @b)",
            })
    void malformedLambdasAreRejected(String expression) {
        assertThrows(RuntimeException.class, () -> parse(FIND + expression), expression);
    }

    @Test
    void aBareNameIsAParameterInTheTreeAndTheMapperSaysWhetherItIsAnAlias() {
        // f(p) parses as a parameter whether or not a lambda declares p: the grammar cannot know,
        // and the mapper turns it into the alias it always was where no lambda declares it
        KQLParser.QueryContext q = parse(FIND + "f(p)");

        assertTrue(has(q, KQLParser.ParamContext.class));
    }

    /** Existing shapes must still choose the old alternatives. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "p.a - 1",
                "p.a -1",
                "f(p.a)",
                "f(p.a > 1)",
                "f(p.a, 1)",
                "p.a * p.b",
            })
    void existingExpressionsAreUnchanged(String expression) {
        KQLParser.QueryContext q = parse(FIND + expression);
        assertTrue(!has(q, KQLParser.LambdaContext.class), expression);
        assertTrue(!has(q, KQLParser.SelectorContext.class), expression);
        assertTrue(!has(q, KQLParser.ParamContext.class), expression);
    }

    private static int count(String s, String needle) {
        int n = 0;
        for (int i = s.indexOf(needle); i >= 0; i = s.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }
}
