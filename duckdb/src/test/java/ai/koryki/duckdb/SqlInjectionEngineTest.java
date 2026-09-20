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
package ai.koryki.duckdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.databases.northwind.duckdb.NorthwindDuckdb;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.jdbc.ListResult;
import ai.koryki.kql.Engine;
import ai.koryki.kql.EngineBuilder;
import ai.koryki.kql.HeaderInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The injection tests that {@code core} can only half answer, finished against a running engine.
 *
 * <p>{@code core} asserts what the transpiler <em>writes</em>. That is the right assertion for a
 * renderer, but it takes one thing on faith: that the SQL it calls safe is read by a database the
 * way the tests read it. The step from "the payload is inside a literal" to "the database treats it
 * as data" is the step DuckDB takes here.
 *
 * <p>The distinction each case rests on is the one an injection either crosses or does not:
 *
 * <ul>
 *   <li>a tautology that stays data matches no company name, so the answer is <b>no rows</b> — an
 *       injection that lands makes it <b>every row</b>, and the two are not confusable;
 *   <li>a DDL payload that stays data leaves the table it names still standing;
 *   <li>a payload that reaches the statement changes the shape of the answer — a column that is not
 *       one of the query's own.
 * </ul>
 *
 * <p>The database is a temp copy of the Northwind file, taken by this class and used by nothing
 * else, so a payload that <em>did</em> land would damage only this copy.
 */
public class SqlInjectionEngineTest {

    private static Engine<HeaderInfo, ListWithSqlResult<HeaderInfo>> engine;

    @BeforeAll
    static void engineOverItsOwnCopyOfNorthwind() throws IOException {
        Path db = Files.createTempFile("koryki-injection-", ".duckdb");
        engine =
                EngineBuilder.headers(
                                new DuckdbDatabase<ListWithSqlResult<HeaderInfo>>(
                                        "ai/koryki/databases/northwind",
                                        DuckdbDatabase.fromResource(NorthwindDuckdb.DUCKDB, db),
                                        ZoneId.of("UTC")),
                                NorthwindService.resolver(),
                                new SqlQueryRenderer(ZoneId.of("UTC")))
                        .valueFormat(new StableFormat(Locale.ROOT))
                        .build();
        db.toFile().deleteOnExit();
    }

    /**
     * The reference point: the same query with a name that exists. Without it, "no rows" below
     * would also be what a query that simply cannot match anything returns, and the tests would
     * pass on a broken engine.
     */
    @Test
    void anHonestFilterFindsItsRow() {
        assertEquals(1, rows("FIND customers c FILTER c.company_name = 'Around the Horn'").size());
    }

    /**
     * The canonical payload. Landed, it makes the WHERE true for every row — 91 customers instead
     * of none — so the row count alone separates "escaped" from "escaped correctly".
     */
    @Test
    void theTautologyMatchesNothingBecauseItIsAName() {
        assertEquals(List.of(), rows("FIND customers c FILTER c.company_name = 'x\\' OR 1=1 --'"));
    }

    /** The same through a set operand and through LIKE, which render by different paths. */
    @Test
    void theTautologyMatchesNothingThroughInAndLikeEither() {
        assertEquals(
                List.of(),
                rows(
                        "FIND customers c FILTER c.company_name IN ('x\\' OR 1=1 --', 'y\\' OR 1=1"
                                + " --')"));
        assertEquals(
                List.of(), rows("FIND customers c FILTER c.company_name LIKE 'x\\' OR 1=1 --'"));
    }

    /**
     * A DDL payload, answered by the table it tries to drop. The assertion is deliberately made
     * after the query rather than on its result: a statement that ran would have left no trace in
     * the rows.
     */
    @Test
    void aDropPayloadLeavesTheTableStanding() {
        assertEquals(
                List.of(),
                rows("FIND customers c FILTER c.company_name = 'x\\'; DROP TABLE orders; --'"));

        assertFalse(
                engine.runSql("SELECT count(*) FROM orders").getRows().isEmpty(),
                "the orders table did not survive the query");
    }

    /**
     * The description path: a carriage return inside the query's leading comment, which DuckDB
     * reads as the end of a {@code --} comment. Landed, the injected {@code SELECT} answers a
     * column named {@code injected}; contained, the answer has the query's own columns.
     */
    @Test
    void aCarriageReturnInTheLeadingCommentDoesNotBecomeAStatement() {
        ListWithSqlResult<HeaderInfo> r =
                engine.executeKQL(
                        "/* note\rSELECT 42 AS injected -- */ FIND customers c FILTER"
                                + " c.customer_id = 'AROUT' FETCH c.company_name",
                        ListWithSqlResult::new);

        assertEquals(1, r.getInfos().size(), "the answer has a column the query did not ask for");
        assertEquals(1, r.getRows().size());
        assertTrue(
                r.getRows().get(0).get(0).toString().contains("Around the Horn"),
                "the query answered something else: " + r.getRows());
    }

    /**
     * The unit of {@code to_interval} is the one argument a dialect reads back out of its literal
     * and writes into the SQL unquoted. This payload used to <em>run</em>: it turned the query into
     * its own UNION arm and answered company names instead of intervals, with the trailing {@code
     * --} swallowing the generated FROM.
     *
     * <p>It is now refused at transpile time, and the assertion says transpile time rather than
     * just "it threw". A malformed payload also throws — as a {@code SQLException} wrapped on its
     * way back from the driver — and a test that accepted either would go on passing if the check
     * were removed and the payload merely mistyped. A refusal with no cause and the function's name
     * in it can only have come from the transpiler.
     */
    /**
     * A payload that <em>ran</em>, rather than one that merely broke the syntax: it adds a second
     * output column of its own, filled from a table the query never mentions. Chosen that way on
     * purpose — a payload that only produces a parse error would let this test pass for the wrong
     * reason on the day the check is removed.
     */
    private static final String EXFILTRATION =
            "FIND orders o FETCH to_interval(1, 'DAY, (SELECT company_name FROM customers LIMIT 1)"
                    + " AS leaked')";

    @Test
    void aHostileIntervalUnitNeverReachesTheDatabase() {
        KorykiaiException e =
                assertThrows(
                        KorykiaiException.class,
                        () -> engine.executeKQL(EXFILTRATION, ListWithSqlResult::new));

        assertNull(e.getCause(), "this came back from the database, not from the transpiler: " + e);
        assertTrue(
                e.getMessage() != null && e.getMessage().contains("to_interval"),
                "the refusal should name the function: " + e.getMessage());
    }

    /** A known unit still runs, so the check above did not simply turn the function off. */
    @Test
    void anHonestIntervalUnitStillRuns() {
        ListResult<HeaderInfo> r =
                engine.executeKQL(
                        "FIND orders o FILTER o.order_id = 10248 FETCH o.order_date +"
                                + " to_interval(30, 'DAY') due",
                        ListWithSqlResult::new);
        assertEquals(1, r.getRows().size());
    }

    private static List<List<Object>> rows(String kql) {
        return engine.executeKQL(kql + " FETCH c.customer_id", ListWithSqlResult::new).getRows();
    }
}
