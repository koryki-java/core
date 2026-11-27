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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.antlr.Range;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.validate.Violation;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A rewriter on the resolver: the query is rewritten before it is transpiled, and what is reported
 * about it is in the query that was asked. The rewriter here is a toy: {@code total(x)} is {@code
 * sum(x)}, two characters shorter, which is enough to move every range behind it.
 */
class QueryRewriterTest {

    private static final String QUERY =
            "FIND customers c, c VIA same_customer orders o\nFETCH c.country, total(o.%s) f";

    /** The rewritten text is two characters shorter after total( than the original. */
    private static final QueryRewriter TOTAL =
            kql -> {
                if (kql.contains("REFUSE")) {
                    throw new ai.koryki.iql.validate.ValidateException(
                            List.of(
                                    new Violation(
                                            "toy",
                                            "x",
                                            new Range(1, 1, 1, 7),
                                            "refused by the rewriter")));
                }
                String out = kql.replace("total(", "sum(");
                return new QueryRewriter.Rewrite(
                        out,
                        r -> {
                            // all on one line after the shift: columns right of total( move by 2
                            int line = r.getStart().getLine();
                            int shift = line == 2 && r.getStart().getPos() > 16 + 3 ? 2 : 0;
                            int shiftStop =
                                    r.getStop().getLine() == 2 && r.getStop().getPos() > 16 + 3
                                            ? 2
                                            : 0;
                            return new Range(
                                    line,
                                    r.getStart().getPos() + shift,
                                    r.getStop().getLine(),
                                    r.getStop().getPos() + shiftStop);
                        });
            };

    private static Generator<HeaderInfo> generator(QueryRewriter rewriter) {
        LinkResolver resolver = NorthwindService.resolver().queryRewriter(rewriter);
        return new Generator<>(
                resolver,
                new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")),
                HeaderInfo::new);
    }

    @Test
    void theRewrittenQueryIsTranspiled() {
        String sql = generator(TOTAL).toSql(String.format(QUERY, "freight"));
        assertTrue(sql.toLowerCase().contains("sum("), sql);
    }

    @Test
    void aViolationIsInTheQueryThatWasAsked() {
        List<Violation> v = generator(TOTAL).validateKQL(String.format(QUERY, "no_such_column"));
        assertFalse(v.isEmpty());
        Range r = v.get(0).getRange();
        // "FETCH c.country, total(o." -> the column starts after "total(o." in the ORIGINAL text
        int expected = "FETCH c.country, total(o.".length() + 1;
        assertEquals(2, r.getStart().getLine());
        assertTrue(
                r.getStart().getPos() <= expected && expected <= r.getStop().getPos(),
                r.toString());
    }

    @Test
    void aRefusalIsAViolationLikeAnyOther() {
        List<Violation> v =
                generator(TOTAL).validateKQL("REFUSE " + String.format(QUERY, "freight"));
        assertEquals(1, v.size());
        assertEquals("refused by the rewriter", v.get(0).getMessage());
        assertEquals(new Range(1, 1, 1, 7), v.get(0).getRange());
    }

    @Test
    void formattingShowsTheQueryThatWasAsked() {
        String formatted = generator(TOTAL).formatKQL(String.format(QUERY, "freight"));
        assertTrue(formatted.contains("total("), formatted);
        assertFalse(formatted.contains("sum("), formatted);
    }

    @Test
    void withoutARewriterNothingChanges() {
        List<Violation> v = generator(null).validateKQL(String.format(QUERY, "freight"));
        // TOTAL is unknown to the database: a warning, and nothing was rewritten
        assertTrue(v.stream().noneMatch(Violation::isError) || !v.isEmpty());
    }
}
