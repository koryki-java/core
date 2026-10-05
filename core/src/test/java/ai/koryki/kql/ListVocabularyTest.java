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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.databases.complex.duckdb.ComplexService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.SqlQueryRenderer;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** What a list answers about itself, and how it is asked: functions, and IN over a list column. */
class ListVocabularyTest {

    private static String sql(String kql) {
        return KQLTranspiler.builder(kql, ComplexService.resolver())
                .functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer())
                .build()
                .getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")));
    }

    private static String refusal(String kql) {
        RuntimeException e = assertThrows(RuntimeException.class, () -> sql(kql));
        return String.valueOf(e.getMessage());
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "list_size(t.tags)|len(t.tags)",
                "list_contains(t.tags, 'red')|list_contains(t.tags, 'red')",
                "list_sum(t.scores)|list_sum(t.scores)",
                "list_min(t.scores)|list_min(t.scores)",
                "list_max(t.scores)|list_max(t.scores)",
                "list_avg(t.scores)|list_avg(t.scores)",
            })
    void theFunctionsRenderInDuckdbsForm(String expression, String expected) {
        String sql = sql("FIND check_complex t FETCH " + expression);

        assertTrue(sql.contains(expected), sql);
    }

    @Test
    void aValueInAListColumnIsListContains() {
        String sql = sql("FIND check_complex t FILTER 'red' IN t.tags FETCH t.nr");

        assertTrue(sql.contains("list_contains(t.tags, 'red')"), sql);
    }

    @Test
    void aValueInAListFromAFunctionToo() {
        String sql =
                sql(
                        "FIND check_complex t"
                                + " FILTER 'RED' IN list_transform(t.tags, x -> upper(x)) FETCH t.nr");

        assertTrue(sql.contains("list_contains(list_transform("), sql);
    }

    @Test
    void theValueMeetsTheElementsSoAnotherTypeIsRefused() {
        String message = refusal("FIND check_complex t FILTER 1 IN t.tags FETCH t.nr");

        assertTrue(message.contains("do not share a common type"), message);
    }

    @Test
    void anInListOfLiteralsIsUnchanged() {
        String sql = sql("FIND check_complex t FILTER t.nr IN (1, 2) FETCH t.nr");

        assertTrue(sql.contains("t.nr IN (1, 2)"), sql);
    }

    @Test
    void theLengthOfAListHasAnotherName() {
        String message = refusal("FIND check_complex t FETCH t.nr, length(t.tags) n");

        assertTrue(message.contains("list_size(list)"), message);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "list_sort(t.tags)|list_sort(t.tags, 'ASC', 'NULLS LAST')",
                "list_sort(t.tags, 'desc')|list_sort(t.tags, 'DESC', 'NULLS LAST')",
                "list_distinct(t.tags)|list_sort(list_distinct(t.tags), 'ASC', 'NULLS LAST')",
                "list_concat(t.scores, t.scores)|CASE WHEN t.scores IS NULL AND t.scores IS NULL THEN NULL ELSE list_concat(t.scores, t.scores) END",
                "list_any(t.scores, x -> x > 5)|len(list_filter(t.scores, _l0_x -> _l0_x > 5)) > 0",
                "list_all(t.scores, x -> x > 5)|len(list_filter(t.scores, _l0_x -> _l0_x > 5)) = len(t.scores)",
                "list(1, 2)|list_value(1, 2)",
                "list_agg(t.nr)|list(t.nr) FILTER (WHERE t.nr IS NOT NULL)",
            })
    void theShapingFunctionsRenderInDuckdbsForm(String expression, String expected) {
        String sql = sql("FIND check_complex t FETCH " + expression);

        assertTrue(sql.contains(expected), sql);
    }

    @Test
    void theOrderOfAListIsAscOrDesc() {
        String message = refusal("FIND check_complex t FETCH list_sort(t.tags, 'sideways') s");

        assertTrue(message.contains("'asc' or 'desc'"), message);
    }

    @Test
    void anAggregateOfASelectionGroupsLikeAnAggregateOfAColumn() {
        String grouped = sql("FIND check_complex t FETCH t.nr, sum(t.scores@2) s");
        String whole = sql("FIND check_complex t FETCH list_sort(list_agg(t.tags@1)) first_tags");

        assertTrue(grouped.replaceAll("\\s+", " ").contains("GROUP BY t.nr"), grouped);
        assertTrue(!whole.contains("GROUP BY"), whole);
    }
}
