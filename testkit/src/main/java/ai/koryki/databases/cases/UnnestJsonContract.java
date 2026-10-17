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

import ai.koryki.iql.validate.Violation;
import ai.koryki.kql.Engine;
import ai.koryki.kql.HeaderInfo;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A JSON array made into rows, {@code t unnest(t.doc@a@b) b}, on an engine that has no lists: the
 * catalog declares {@code .a.b[]} as an array of INTEGER in the JSON column {@code doc} of the
 * complex bed, and the rows are what {@code json_each}, {@code JSON_TABLE} or {@code OPENJSON} make
 * of it. The rows are those of the flatten view {@code check_complex_doc_a_b} of the bed.
 *
 * <p>A dialect test extends this and says how to reach its database. The bed and its view are
 * created by the scripts of its Northwind module.
 */
public abstract class UnnestJsonContract {

    /** The engine of this dialect over the catalog of the complex bed. */
    protected abstract Engine<HeaderInfo, ListWithSqlResult<HeaderInfo>> engine();

    protected final List<List<String>> rows(String kql) {
        ListWithSqlResult<HeaderInfo> result = engine().executeKQL(kql, ListWithSqlResult::new);
        return result.getRows().stream()
                .map(r -> r.stream().map(v -> v == null ? null : String.valueOf(v)).toList())
                .sorted(Comparator.comparing(Object::toString))
                .toList();
    }

    @SafeVarargs
    private static List<List<String>> sorted(List<String>... rows) {
        return Arrays.stream(rows).sorted(Comparator.comparing(Object::toString)).toList();
    }

    private static List<String> row(String... values) {
        return Arrays.asList(values);
    }

    @Test
    void theElementsOfTheArrayAreTheRowsOfTheFlattenView() {
        // the rows of flatten_json.kql
        assertEquals(
                sorted(row("1", "1", "5", "50"), row("1", "2", "6", "60")),
                rows(
                        "FIND check_complex t, t unnest(t.doc@a@b) b"
                                + " FETCH t.nr, b.ordinal, b.value, b.value * 10 tenfold"));
    }

    @Test
    void theAnswerThatNeededAViewIsOneQuery() {
        // the rows of flatten_json_group.kql
        assertEquals(
                sorted(row("2", "11", "6")),
                rows(
                        "FIND check_complex t, t unnest(t.doc@a@b) b"
                                + " FETCH count(b.value) elements, sum(b.value) total,"
                                + " max(b.value) biggest"));
    }

    @Test
    void aRowWithoutTheArrayHasNoRowsAndTheOuterJoinKeepsIt() {
        assertEquals(
                sorted(row("1"), row("1")),
                rows("FIND check_complex t, t unnest(t.doc@a@b) b FETCH t.nr"));
        assertEquals(
                sorted(row("1", "5"), row("1", "6"), row("2", null), row("3", null)),
                rows("FIND check_complex t, t + unnest(t.doc@a@b) b FETCH t.nr, b.value"));
    }

    @Test
    void aFilterOnTheRowsKeepsTheMatchingElements() {
        assertEquals(
                sorted(row("1", "6")),
                rows(
                        "FIND check_complex t, t unnest(t.doc@a@b) b"
                                + " FILTER b.value > 5 FETCH t.nr, b.value"));
    }

    /** Public so that a dialect whose outer join takes no condition can say so instead. */
    @Test
    public void aFilterOnTheRowsOfTheOuterJoinKeepsEveryParent() {
        assertEquals(
                sorted(row("1", "6"), row("2", null), row("3", null)),
                rows(
                        "FIND check_complex t, t + unnest(t.doc@a@b) b"
                                + " FILTER b.value > 5 FETCH t.nr, b.value"));
    }

    @Test
    void jsonThatTheCatalogDoesNotDeclareAsAnArrayIsRefused() {
        List<Violation> found =
                engine().validateKQL("FIND check_complex t, t unnest(t.doc) b FETCH b.value");

        assertEquals(1, found.size(), found.toString());
        assertTrue(
                found.get(0).getMessage().contains("this JSON is not declared as one"),
                found.get(0).getMessage());
    }
}
