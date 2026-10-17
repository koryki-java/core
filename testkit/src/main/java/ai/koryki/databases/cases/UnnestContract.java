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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.kql.Engine;
import ai.koryki.kql.HeaderInfo;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A list made into rows, {@code t unnest(t.items) i}, on one engine, over the complex bed: the
 * elements are the rows of the flatten views of the bed, which are checked against every dialect,
 * an empty or NULL list has no rows unless the join is the outer one, and a list may be what a
 * lambda made or a field of the rows of another.
 *
 * <p>The same rows on every engine. A dialect test extends this and says how to reach its database;
 * the bed and its views are created by the scripts of its Northwind module.
 */
public abstract class UnnestContract {

    /** The engine of this dialect over the catalog of the complex bed. */
    protected abstract Engine<HeaderInfo, ListWithSqlResult<HeaderInfo>> engine();

    protected final List<List<String>> rows(String kql) {
        ListWithSqlResult<HeaderInfo> result = engine().executeKQL(kql, ListWithSqlResult::new);
        return result.getRows().stream()
                .map(r -> r.stream().map(v -> v == null ? null : String.valueOf(v)).toList())
                .sorted(Comparator.comparing(Object::toString))
                .toList();
    }

    protected final String sql(String kql) {
        return engine().executeKQL(kql, ListWithSqlResult::new).getSql();
    }

    /** Rows as {@link #rows} answers them: by their text, NULL as null. */
    private static List<List<String>> sorted(List<String>... rows) {
        return java.util.Arrays.stream(rows)
                .sorted(Comparator.comparing(Object::toString))
                .toList();
    }

    private static List<String> row(String... values) {
        return java.util.Arrays.asList(values);
    }

    @Test
    void theElementsOfAListOfStructsAreTheRowsOfTheFlattenView() {
        // the rows of flatten_items.kql, which every dialect answers
        assertEquals(
                sorted(
                        row("1", "1", "A-1", "19.98"),
                        row("1", "2", "B-2", "24.50"),
                        row("3", "1", "C-3", null)),
                rows(
                        "FIND check_complex t, t unnest(t.items) i"
                                + " FETCH t.nr, i.ordinal, i.sku, i.qty * i.price total"));
    }

    @Test
    void theAnswerThatNeededAViewIsOneQuery() {
        // the rows of flatten_group.kql
        assertEquals(
                sorted(row("A-1", "1", "19.98"), row("B-2", "1", "24.50"), row("C-3", "1", null)),
                rows(
                        "FIND check_complex t, t unnest(t.items) i"
                                + " FETCH i.sku, count(i.sku) lines, sum(i.qty * i.price) revenue"));
    }

    @Test
    public void aListOfScalarsHasTheColumnValue() {
        assertEquals(
                sorted(
                        row("1", "1", "red"),
                        row("1", "2", "green"),
                        row("1", "3", "blue"),
                        row("3", "1", "x"),
                        row("3", "2", null),
                        row("3", "3", "z")),
                rows(
                        "FIND check_complex t, t unnest(t.tags) g"
                                + " FETCH t.nr, g.ordinal, g.value"));
    }

    @Test
    void temporalFieldsOfTheElementKeepTheirType() {
        // the rows of flatten_events.kql
        assertEquals(
                sorted(row("1", "1", "2026-05-17"), row("3", "1", "2026-01-01")),
                rows(
                        "FIND check_complex t, t unnest(t.events) e"
                                + " FETCH t.nr, e.ordinal, e.day"));
    }

    @Test
    void anEmptyOrNullListHasNoRowsAndTheOuterJoinKeepsTheParent() {
        List<String> inner =
                rows("FIND check_complex t, t unnest(t.items) i FETCH t.nr, i.sku").stream()
                        .map(r -> r.get(0))
                        .distinct()
                        .toList();
        List<List<String>> outer =
                rows("FIND check_complex t, t + unnest(t.items) i FETCH t.nr, i.sku");

        assertEquals(List.of("1", "3"), inner, "the parent with an empty list has no row");
        assertEquals(
                List.of("1", "2", "3"),
                outer.stream().map(r -> r.get(0)).distinct().toList(),
                "the outer join keeps every parent");
        assertTrue(
                outer.stream().anyMatch(r -> r.get(0).equals("2") && r.get(1) == null),
                "and gives it a NULL element: " + outer);
    }

    /** Public so that a dialect that cannot do it can say so instead (see the Trino test). */
    @Test
    public void aFilterOnTheRowsOfTheOuterJoinKeepsEveryParent() {
        List<List<String>> outer =
                rows(
                        "FIND check_complex t, t + unnest(t.items) i"
                                + " FILTER i.qty > 1 FETCH t.nr, i.sku");

        assertEquals(
                List.of(
                        java.util.Arrays.asList("1", "A-1"),
                        java.util.Arrays.asList("2", null),
                        java.util.Arrays.asList("3", null)),
                outer);
    }

    @Test
    void aFilterOnTheRowsOfTheInnerJoinKeepsTheMatchingElements() {
        assertEquals(
                List.of(List.of("1", "A-1")),
                rows(
                        "FIND check_complex t, t unnest(t.items) i"
                                + " FILTER i.qty > 1 FETCH t.nr, i.sku"));
    }

    @Test
    void theListMayBeWhatALambdaMade() {
        assertEquals(
                List.of(row("1", "A-1")),
                rows(
                        "FIND check_complex t,"
                                + " t unnest(list_filter(t.items, x -> x@qty > 1)) i"
                                + " FETCH t.nr, i.sku"));
    }

    @Test
    void theListOfAnElementIsReachedFromItsAlias() {
        assertEquals(
                List.of(List.of("1", "A-1", "x"), List.of("1", "A-1", "y")),
                rows(
                        "FIND check_complex t, t unnest(t.items) i, i unnest(i.opts) o"
                                + " FETCH t.nr, i.sku, o.value"));
    }

    @Test
    public void theOrdinalIsAskedOfTheDatabaseOnlyWhereTheQueryReadsIt() {
        String without = sql("FIND check_complex t, t unnest(t.items) i FETCH t.nr, i.sku");
        String with = sql("FIND check_complex t, t unnest(t.items) i FETCH t.nr, i.ordinal");

        assertTrue(!without.toUpperCase().contains("ORDINALITY"), without);
        assertTrue(with.toUpperCase().contains("ORDINALITY"), with);
    }
}
