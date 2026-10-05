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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.antlr.RangeException;
import ai.koryki.databases.complex.duckdb.ComplexService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.SqlQueryRenderer;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * The rules that read a query for the sources it touches see through a selection and into a lambda:
 * {@code b.scores@1} is a value of {@code b}, and a lambda reads the columns it captures. Before
 * they did, {@code b.nr = a.scores@1} on an optional join was taken for a condition on {@code b}
 * alone and moved into its ON clause, where {@code b.nr = a.nr} is refused.
 */
class SelectionRulesTest {

    private static String sql(String kql) {
        return KQLTranspiler.builder(kql, ComplexService.resolver())
                .functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer())
                .build()
                .getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")));
    }

    private static final String OPTIONAL =
            "FIND check_complex a, VIA same_check + check_complex b ";

    @Test
    void aConditionOnASelectionOfTheOptionalTableMovesIntoTheJoin() {
        String sql = sql(OPTIONAL + "FILTER b.scores@1 > 1 FETCH a.nr, b.nr");

        assertTrue(sql.contains("LEFT OUTER JOIN"), sql);
        assertTrue(sql.contains("b.scores[1] > 1"), sql);
        assertFalse(sql.contains("WHERE"), sql);
    }

    @Test
    void aConditionOnTwoSourcesIsRefusedWhetherOrNotItUsesASelection() {
        RangeException plain =
                assertThrows(
                        RangeException.class,
                        () -> sql(OPTIONAL + "FILTER b.nr = a.nr FETCH a.nr, b.nr"));
        RangeException selected =
                assertThrows(
                        RangeException.class,
                        () -> sql(OPTIONAL + "FILTER b.nr = a.scores@1 FETCH a.nr, b.nr"));

        assertTrue(plain.getMessage().contains("filters the optional table"));
        assertTrue(selected.getMessage().contains("filters the optional table"));
        assertTrue(selected.getMessage().contains("`b.nr = a.scores@1`"), selected.getMessage());
    }

    @Test
    void aDateBetweenInsideALambdaIsRewrittenLikeAnyOther() {
        String sql =
                sql(
                        "FIND check_complex t FILTER list_filter(t.events, e -> e@day BETWEEN"
                                + " \"2026-01-01\" AND \"2026-12-31\")@1@ts > \"2000-01-01 00:00:00\" FETCH t.nr");

        assertFalse(sql.contains("BETWEEN"), sql);
        assertTrue(sql.contains(" < "), sql);
    }
}
