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

package ai.koryki.databases.cases;

import static ai.koryki.databases.cases.GeneratedLists.at;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.databases.cases.GeneratedLists.Item;
import ai.koryki.databases.cases.GeneratedLists.Row;
import ai.koryki.kql.Engine;
import ai.koryki.kql.HeaderInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Questions about lists that are answered from the rows themselves: the same seeded rows ({@link
 * GeneratedLists}) are in every table of the shapes a dialect makes, and what an engine says is
 * compared with what the rows say. A position of a list, a position in a list of structs, what a
 * list says about itself, a membership, and a list made into rows, plain, outer and aggregated.
 *
 * <p>A dialect test extends this, makes its tables and says how to reach them. Every table has the
 * columns of {@link GeneratedListsCatalog}; a table may have more, which is the point of having
 * several: the fault of Oracle in {@code docs/NESTED.md} showed on one shape of table and not on
 * another.
 */
public abstract class GeneratedListsContract {

    /** The seed of the rows: fixed, so a failure can be run again. */
    protected static final long SEED = 20261017L;

    protected static final int COUNT = 60;

    protected final List<Row> data = GeneratedLists.rows(SEED, COUNT);

    /** The engine of this dialect over the catalog of the tables. */
    protected abstract Engine<HeaderInfo, ListWithSqlResult<HeaderInfo>> engine();

    /** The tables that hold the rows, one for each shape. */
    protected abstract List<String> tables();

    /** Whether the engine leaves a NULL element out of the rows of a list (Snowflake does). */
    protected boolean dropsNullElements() {
        return false;
    }

    private List<List<String>> rows(String kql) {
        ListWithSqlResult<HeaderInfo> result = engine().executeKQL(kql, ListWithSqlResult::new);
        return sorted(
                result.getRows().stream()
                        .map(
                                r ->
                                        r.stream()
                                                .map(v -> v == null ? null : String.valueOf(v))
                                                .toList())
                        .toList());
    }

    private static List<List<String>> sorted(List<List<String>> rows) {
        return rows.stream().sorted(Comparator.comparing(Object::toString)).toList();
    }

    private static List<String> row(Object... values) {
        return Arrays.stream(values).map(GeneratedLists::text).toList();
    }

    /**
     * Asks the question of every table, and expects the same answer of each. The tables that
     * answered wrongly are all named, with the first rows that differ: one shape of table may be
     * wrong where another is right.
     */
    private void everywhere(String question, List<List<String>> expected) {
        List<String> wrong = new ArrayList<>();
        List<List<String>> want = sorted(expected);
        for (String table : tables()) {
            List<List<String>> got = rows(question.replace("{t}", table));
            if (!want.equals(got)) {
                List<String> differing = new ArrayList<>();
                for (int i = 0;
                        i < Math.min(want.size(), got.size()) && differing.size() < 3;
                        i++) {
                    if (!want.get(i).equals(got.get(i))) {
                        differing.add("expected " + want.get(i) + ", was " + got.get(i));
                    }
                }
                wrong.add(
                        table
                                + " ("
                                + differing
                                + ", "
                                + want.size()
                                + " rows expected, "
                                + got.size()
                                + " came)");
            }
        }
        assertTrue(wrong.isEmpty(), question + " answered wrongly on " + wrong);
    }

    /** What the questions are worth depends on the rows having the shapes they are about. */
    @Test
    void theRowsHaveTheShapesThatMatter() {
        for (java.util.function.Function<Row, List<?>> list :
                List.<java.util.function.Function<Row, List<?>>>of(
                        Row::tags, Row::nums, Row::items)) {
            assertTrue(data.stream().anyMatch(r -> list.apply(r) == null), "a NULL list");
            assertTrue(
                    data.stream().anyMatch(r -> list.apply(r) != null && list.apply(r).isEmpty()),
                    "an empty list");
            assertTrue(
                    data.stream().anyMatch(r -> list.apply(r) != null && list.apply(r).size() > 1),
                    "a list of several");
        }
        assertTrue(
                data.stream().anyMatch(r -> r.tags() != null && r.tags().contains(null)),
                "a NULL element");
        assertTrue(
                data.stream().anyMatch(r -> r.tags() != null && r.tags().contains("\u65e5\u672c")),
                "text that is not ASCII");
        // the fault was an empty or NULL list after a row whose list had an element
        int firstWithElement = -1;
        for (int i = 0; i < data.size(); i++) {
            List<String> tags = data.get(i).tags();
            if (firstWithElement < 0 && tags != null && !tags.isEmpty()) {
                firstWithElement = i;
            } else if (firstWithElement >= 0 && (tags == null || tags.isEmpty())) {
                return;
            }
        }
        throw new AssertionError("an empty or NULL list after one with an element");
    }

    /**
     * One expression of each row, asked on its own: {@code FIND t FETCH t.id, expression}. Not
     * together with others: what Oracle did wrong in {@code docs/NESTED.md} depended on the
     * statement, and a query with a position from the end next to a position from the start was
     * right where the position from the start alone was not.
     */
    private void eachRow(String expression, java.util.function.Function<Row, Object> expected) {
        List<List<String>> rows = new ArrayList<>();
        for (Row r : data) {
            rows.add(row(r.id(), expected.apply(r)));
        }
        everywhere("FIND {t} t FETCH t.id, " + expression, rows);
    }

    @Test
    void theElementOfAListAtAPosition() {
        eachRow("t.tags@1", r -> at(r.tags(), 1));
        eachRow("t.tags@2", r -> at(r.tags(), 2));
        eachRow("t.tags@-1", r -> at(r.tags(), -1));
        eachRow("t.nums@1", r -> at(r.nums(), 1));
        eachRow("t.nums@3", r -> at(r.nums(), 3));
        eachRow("t.nums@-2", r -> at(r.nums(), -2));
    }

    @Test
    void positionsAskedForTogether() {
        List<List<String>> expected = new ArrayList<>();
        for (Row r : data) {
            expected.add(
                    row(
                            r.id(),
                            at(r.tags(), 1),
                            at(r.tags(), 2),
                            at(r.tags(), -1),
                            at(r.nums(), 1),
                            at(r.nums(), -2)));
        }
        everywhere(
                "FIND {t} t FETCH t.id, t.tags@1, t.tags@2, t.tags@-1, t.nums@1, t.nums@-2",
                expected);
    }

    private static Object field(Item item, boolean sku) {
        return item == null ? null : sku ? item.sku() : item.qty();
    }

    @Test
    void aFieldOfTheElementOfAListOfStructs() {
        eachRow("t.items@1@sku", r -> field(at(r.items(), 1), true));
        eachRow("t.items@2@sku", r -> field(at(r.items(), 2), true));
        eachRow("t.items@1@qty", r -> field(at(r.items(), 1), false));
        eachRow("t.items@-1@qty", r -> field(at(r.items(), -1), false));
    }

    @Test
    void whatAListSaysOfItself() {
        List<List<String>> expected = new ArrayList<>();
        for (Row r : data) {
            expected.add(
                    row(
                            r.id(),
                            GeneratedLists.size(r.tags()),
                            GeneratedLists.sum(r.nums()),
                            GeneratedLists.min(r.nums()),
                            GeneratedLists.max(r.nums())));
        }
        everywhere(
                "FIND {t} t FETCH t.id, list_size(t.tags), list_sum(t.nums), list_min(t.nums),"
                        + " list_max(t.nums)",
                expected);
    }

    @Test
    void theRowsWhoseListHasAnElement() {
        List<List<String>> expected = new ArrayList<>();
        for (Row r : data) {
            if (r.tags() != null && r.tags().contains("a")) {
                expected.add(row(r.id()));
            }
        }
        everywhere("FIND {t} t FILTER list_contains(t.tags, 'a') FETCH t.id", expected);
    }

    /** The elements of a list as rows: every element of every list that is there. */
    private List<List<String>> elements(boolean outer) {
        List<List<String>> expected = new ArrayList<>();
        for (Row r : data) {
            if (r.tags() == null || r.tags().isEmpty()) {
                if (outer) {
                    expected.add(row(r.id(), null));
                }
                continue;
            }
            for (String tag : r.tags()) {
                if (tag != null || !dropsNullElements()) {
                    expected.add(row(r.id(), tag));
                }
            }
        }
        return expected;
    }

    @Test
    void theElementsOfEveryListAreRows() {
        everywhere("FIND {t} t, t unnest(t.tags) g FETCH t.id, g.value", elements(false));
    }

    @Test
    void theOuterFormKeepsTheRowsWhoseListIsNullOrEmpty() {
        everywhere("FIND {t} t, t + unnest(t.tags) g FETCH t.id, g.value", elements(true));
    }

    @Test
    void aSumOverTheElementsOfTheListsOfARow() {
        List<List<String>> expected = new ArrayList<>();
        for (Row r : data) {
            if (r.nums() != null && !r.nums().isEmpty()) {
                expected.add(row(r.id(), GeneratedLists.sum(r.nums())));
            }
        }
        everywhere(
                "FIND {t} t, t unnest(t.nums) n FETCH t.id, sum(n.value) total",
                dropsNullElements() ? withoutAllNull(expected) : expected);
    }

    /**
     * An engine that leaves NULL elements out has no row for a list of nothing else, and so no
     * answer for it.
     */
    private List<List<String>> withoutAllNull(List<List<String>> expected) {
        return expected.stream().filter(r -> r.get(1) != null).toList();
    }

    @Test
    void theElementsOfAListOfStructsAreGroupedByAField() {
        Map<String, int[]> groups = new TreeMap<>(Comparator.nullsFirst(Comparator.naturalOrder()));
        Map<String, Boolean> sawQty =
                new TreeMap<>(Comparator.nullsFirst(Comparator.naturalOrder()));
        for (Row r : data) {
            if (r.items() == null) {
                continue;
            }
            for (Item item : r.items()) {
                int[] g = groups.computeIfAbsent(item.sku(), k -> new int[2]);
                if (item.sku() != null) {
                    g[0]++;
                }
                if (item.qty() != null) {
                    g[1] += item.qty();
                    sawQty.put(item.sku(), true);
                }
            }
        }
        List<List<String>> expected = new ArrayList<>();
        groups.forEach(
                (sku, g) ->
                        expected.add(
                                row(sku, g[0], sawQty.containsKey(sku) ? (Object) g[1] : null)));
        everywhere(
                "FIND {t} t, t unnest(t.items) i"
                        + " FETCH i.sku, count(i.sku) n, sum(i.qty) total",
                expected);
    }
}
