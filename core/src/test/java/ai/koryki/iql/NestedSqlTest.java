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
package ai.koryki.iql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.iql.NestedSql.FromEnd;
import ai.koryki.iql.NestedSql.Quoting;
import ai.koryki.iql.query.Selector;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What the dialects share when they render a selector chain. */
class NestedSqlTest {

    private static Selector index(int n) {
        Selector s = new Selector();
        s.setIndex(n);
        return s;
    }

    private static Selector id(String name) {
        Selector s = new Selector();
        s.setId(name);
        return s;
    }

    private static Selector name(String name) {
        Selector s = new Selector();
        s.setName(name);
        return s;
    }

    @Test
    void aReferenceIsAColumnAndNothingThatNeedsEvaluating() {
        assertTrue(NestedSql.isReference("t.tags"));
        assertTrue(NestedSql.isReference("LA0.X.price"));
        assertTrue(NestedSql.isReference("_l0_x"));
        assertTrue(NestedSql.isReference("t.\"Odd Name\""));
        assertFalse(NestedSql.isReference("coalesce(t.tags, t.tags)"));
        assertFalse(NestedSql.isReference("t.tags[1]"));
        assertFalse(NestedSql.isReference("random()"));
    }

    @Test
    void thePathOfEachPrefixIsAvailableForGuards() {
        List<String> paths =
                NestedSql.jsonPaths(
                        List.of(id("a"), id("b"), index(1)), "$", FromEnd.LAST, Quoting.DOT, "X");

        assertEquals(List.of("$", "$.a", "$.a.b", "$.a.b[0]"), paths);
    }

    @Test
    void thePathCountsFromZeroAndSaysWhereTheEndIs() {
        assertEquals(
                "$[last]",
                NestedSql.jsonPath(List.of(index(-1)), "$", FromEnd.LAST, Quoting.DOT, "X"));
        assertEquals(
                "$[last-2]",
                NestedSql.jsonPath(List.of(index(-3)), "$", FromEnd.LAST, Quoting.DOT, "X"));
        assertEquals(
                "$[#-1]",
                NestedSql.jsonPath(List.of(index(-1)), "$", FromEnd.HASH, Quoting.DOT, "X"));
        assertEquals(
                "$[4]", NestedSql.jsonPath(List.of(index(5)), "$", FromEnd.NONE, Quoting.DOT, "X"));
    }

    @Test
    void aDialectWithoutAPositionFromTheEndRefusesItByName() {
        KorykiaiException e =
                assertThrows(
                        KorykiaiException.class,
                        () ->
                                NestedSql.jsonPath(
                                        List.of(index(-1)),
                                        "$",
                                        FromEnd.NONE,
                                        Quoting.DOT,
                                        "Trino"));

        assertTrue(e.getMessage().contains("Trino"), e.getMessage());
        assertTrue(e.getMessage().contains("no position from the end"), e.getMessage());
    }

    @Test
    void aNameThatIsNoIdentifierIsQuotedTheWayTheDialectWritesIt() {
        List<Selector> steps = List.of(name("a b"), name("c\"d"));

        assertEquals(
                "$.\"a b\".\"c\\\"d\"",
                NestedSql.jsonPath(steps, "$", FromEnd.NONE, Quoting.DOT, "X"));
        assertEquals(
                "$[\"a b\"][\"c\\\"d\"]",
                NestedSql.jsonPath(steps, "$", FromEnd.NONE, Quoting.BRACKET, "X"));
    }

    @Test
    void aPathWithoutARootStartsWithTheName() {
        assertEquals(
                "a.b[0]",
                NestedSql.jsonPath(
                        List.of(id("a"), id("b"), index(1)), "", FromEnd.NONE, Quoting.DOT, "X"));
    }

    @Test
    void aFieldIsFoundByPositionByNameAndWithoutRegardToCase() {
        TypeDescriptor struct =
                TypeDescriptor.struct(List.of("w", "Odd Name"), List.of(leaf(), leaf()));

        assertEquals("w", NestedSql.fieldName(struct, index(1)));
        assertEquals("Odd Name", NestedSql.fieldName(struct, index(-1)));
        assertEquals("Odd Name", NestedSql.fieldName(struct, name("ODD NAME")));
        assertEquals("w", NestedSql.fieldName(struct, id("w")));
        assertEquals("nope", NestedSql.fieldName(struct, id("nope")));
    }

    private static TypeDescriptor leaf() {
        return new TypeDescriptor("INTEGER", null, ai.koryki.catalog.types.CoreTypeFamily.INTEGER);
    }
}
