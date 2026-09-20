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
package ai.koryki.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.kql.KQLTranspiler;
import ai.koryki.mariadb.iql.MariadbDialect;
import ai.koryki.mssql.iql.MssqlDialect;
import ai.koryki.oracle.iql.OracleDialect;
import ai.koryki.postgresql.iql.PostgreSqlDialect;
import ai.koryki.snowflake.iql.SnowflakeDialect;
import ai.koryki.sqlite.iql.SqliteDialect;
import ai.koryki.trino.iql.TrinoDialect;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Values SQL must not take as SQL, rendered for every dialect — the literal-side twin of {@link
 * HostileIdentifierTest}.
 *
 * <p><b>Why a second test and not more cases in that one.</b> An identifier that is quoted wrongly
 * is usually a syntax error or a column nobody has: loud, and caught by the first query that runs.
 * A <em>literal</em> that is escaped wrongly is a query that still runs and answers the wrong
 * question — a filter that matched everything rather than nothing. The blast radius is different,
 * so the test is separate and the assertions are stronger.
 *
 * <p><b>Why it cannot be one shared expectation.</b> The renderer's escape — doubling the quote —
 * is the one all eight engines accept, but it is not sufficient everywhere, because the engines do
 * not agree on what <em>else</em> is an escape inside a literal. MariaDB/MySQL and Snowflake also
 * read a backslash; the other six do not. So the same rendered bytes are one literal on six engines
 * and two on two of them, and the only assertion that means anything is one made with the engine's
 * own reading in hand. {@link Literal} is that reading, per dialect.
 *
 * <p>That is what makes this test the one that fails if {@code MariadbDialect.textLiteral} or
 * {@code SnowflakeDialect.textLiteral} is deleted: the payload leaves the literal under those two
 * engines' rules and under nobody else's. See {@code docs/INJECTION.md}, barrier B2.
 */
class HostileLiteralTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    /**
     * How an engine reads what is between two single quotes.
     *
     * <p>Both accept a doubled quote. The difference is the backslash, and it is not a matter of
     * taste: on MySQL/MariaDB it is governed by {@code NO_BACKSLASH_ESCAPES}, which is off by
     * default, and Snowflake documents backslash escape sequences in string constants outright.
     */
    private enum Literal {
        /** {@code ''} and nothing else — the SQL standard. */
        STANDARD,
        /** {@code ''} and {@code \x} both. */
        BACKSLASH
    }

    /** Every dialect, with the way its engine lexes a string literal. */
    private static final Map<String, Dialect> DIALECTS = dialects();

    private record Dialect(SqlDialect sql, Literal literal) {}

    private static Map<String, Dialect> dialects() {
        LinkedHashMap<String, Dialect> m = new LinkedHashMap<>();
        m.put("duckdb", new Dialect(DuckdbBaseDialect.INSTANCE, Literal.STANDARD));
        m.put("sqlite", new Dialect(SqliteDialect.INSTANCE, Literal.STANDARD));
        m.put("postgresql", new Dialect(PostgreSqlDialect.INSTANCE, Literal.STANDARD));
        m.put("trino", new Dialect(TrinoDialect.INSTANCE, Literal.STANDARD));
        m.put("oracle", new Dialect(OracleDialect.INSTANCE, Literal.STANDARD));
        m.put("mssql", new Dialect(MssqlDialect.INSTANCE, Literal.STANDARD));
        m.put("mariadb", new Dialect(MariadbDialect.INSTANCE, Literal.BACKSLASH));
        m.put("snowflake", new Dialect(SnowflakeDialect.INSTANCE, Literal.BACKSLASH));
        return Map.copyOf(m);
    }

    private static LinkResolver resolver;

    private static LinkResolver resolver() {
        if (resolver == null) {
            resolver = NorthwindService.resolver();
        }
        return resolver;
    }

    private static String sql(SqlDialect dialect, String kql) {
        return KQLTranspiler.builder(kql, resolver())
                .functions(dialect.getFunctionRenderer())
                .build()
                .getSql(new SqlQueryRenderer(dialect, UTC));
    }

    // ---------------------------------------------------------------- the literal path

    @Test
    void anEscapedQuoteIsOneLiteralOnEveryDialect() {
        eachDialect(
                (name, d) -> {
                    String sql =
                            sql(
                                    d.sql(),
                                    "FIND customers c FILTER c.company_name = 'O\\'Brien'"
                                            + " FETCH c.city");
                    assertContained(name, sql, d.literal(), "Brien");
                });
    }

    @Test
    void theTautologyStaysInsideTheLiteralOnEveryDialect() {
        eachDialect(
                (name, d) -> {
                    String sql =
                            sql(
                                    d.sql(),
                                    "FIND customers c FILTER c.company_name = 'x\\' OR 1=1 --'"
                                            + " FETCH c.city");
                    assertContained(name, sql, d.literal(), "OR 1=1", "--");
                });
    }

    @Test
    void aStatementTerminatorStaysInsideTheLiteralOnEveryDialect() {
        eachDialect(
                (name, d) -> {
                    String sql =
                            sql(
                                    d.sql(),
                                    "FIND customers c FILTER c.company_name = 'x\\'; DROP TABLE"
                                            + " orders; --' FETCH c.city");
                    assertContained(name, sql, d.literal(), "DROP", ";");
                });
    }

    /**
     * The case the two backslash engines exist in this test for.
     *
     * <p>The KQL {@code 'x\\' OR 1=1 -- '} holds the value {@code x\' OR 1=1 -- } and renders,
     * before any dialect adjustment, as {@code 'x\'' OR 1=1 -- '}. Six engines read that as one
     * literal. MariaDB and Snowflake read {@code \'} as an escaped quote, take the next one as the
     * closing delimiter, and find {@code OR 1=1} waiting outside it — which is a tautology, not a
     * name. Their {@code textLiteral} doubles the backslash so the same value survives their lexing
     * too.
     */
    @Test
    void aBackslashBeforeTheEscapedQuoteDoesNotReopenTheLiteral() {
        eachDialect(
                (name, d) -> {
                    String sql =
                            sql(
                                    d.sql(),
                                    "FIND customers c FILTER c.company_name = 'x\\\\' OR 1=1 -- '"
                                            + " FETCH c.city");
                    assertContained(name, sql, d.literal(), "OR 1=1", "--");
                });
    }

    /**
     * The mechanism behind the case above, asserted directly so that a dialect which stops doubling
     * fails here with a message naming what it stopped doing, rather than only through the payload
     * test.
     */
    @Test
    void theEnginesThatReadBackslashesDoubleThem() {
        // The KQL literal holds two backslashes (KQL's only escape is \', so a backslash before
        // anything else is itself). Counted rather than matched against an escaped Java string:
        // four levels of escaping in one assertion is how the expectation gets written wrong.
        eachDialect(
                (name, d) -> {
                    String sql =
                            sql(
                                    d.sql(),
                                    "FIND customers c FILTER c.company_name = 'C:\\\\temp' FETCH"
                                            + " c.city");
                    int expected = d.literal() == Literal.BACKSLASH ? 4 : 2;
                    assertEquals(
                            expected,
                            sql.chars().filter(ch -> ch == '\\').count(),
                            name
                                    + " should write "
                                    + expected
                                    + " backslashes for the two the value holds, but wrote:\n"
                                    + sql);
                });
    }

    /** A literal reached through a set operand and through LIKE, which render by other paths. */
    @Test
    void inAndLikeOperandsAreEscapedOnEveryDialect() {
        eachDialect(
                (name, d) -> {
                    assertContained(
                            name,
                            sql(
                                    d.sql(),
                                    "FIND customers c FILTER c.company_name IN ('safe', 'x\\' OR"
                                            + " 1=1 --') FETCH c.city"),
                            d.literal(),
                            "OR 1=1");
                    assertContained(
                            name,
                            sql(
                                    d.sql(),
                                    "FIND customers c FILTER c.company_name LIKE '%\\' OR 1=1 --%'"
                                            + " FETCH c.city"),
                            d.literal(),
                            "OR 1=1");
                });
    }

    // ------------------------------------------------------- the read-back path (docs: path 3)

    /**
     * {@code to_interval}'s unit is read back out of its rendered literal by the dialects that
     * cannot express the unit as a value. Whatever a dialect does with a unit it does not know, the
     * author's text must not end up as SQL — so each is allowed to refuse, and is held to
     * containment if it does not.
     *
     * <p>Stated as "refuse or contain" rather than "refuse", because the right answer genuinely
     * differs: DuckDB shape-checks the word, Oracle re-uses the quoted literal, and a dialect that
     * renders the unit through a template never unquotes it at all.
     */
    @Test
    void aHostileIntervalUnitIsRefusedOrContainedOnEveryDialect() {
        eachDialect(
                (name, d) ->
                        refusedOrContained(
                                name,
                                d,
                                "FIND orders o FETCH to_interval(1, 'DAY, (SELECT company_name FROM"
                                        + " customers LIMIT 1) AS leaked')",
                                "SELECT company_name",
                                "leaked"));
    }

    /**
     * The same for {@code date_trunc}, the other function whose unit several dialects read back.
     */
    @Test
    void aHostileDateTruncUnitIsRefusedOrContainedOnEveryDialect() {
        eachDialect(
                (name, d) ->
                        refusedOrContained(
                                name,
                                d,
                                "FIND orders o FETCH date_trunc('day, (SELECT company_name FROM"
                                        + " customers LIMIT 1) AS leaked', o.order_date)",
                                "SELECT company_name",
                                "leaked"));
    }

    /** An honest unit still renders, so the checks above did not simply turn the functions off. */
    @Test
    void honestUnitsStillRenderOnEveryDialect() {
        eachDialect(
                (name, d) -> {
                    sql(d.sql(), "FIND orders o FETCH to_interval(30, 'DAY') due");
                    sql(d.sql(), "FIND orders o FETCH date_trunc('month', o.order_date) m");
                });
    }

    @Test
    void everyDialectIsCovered() {
        // A dialect added without a line here would be silently untested.
        assertEquals(8, DIALECTS.size());
    }

    // ---------------------------------------------------------------------------- machinery

    /**
     * Renders {@code kql} and requires either a refusal at transpile time or a rendering in which
     * none of {@code payload} reached the statement.
     */
    private static void refusedOrContained(String name, Dialect d, String kql, String... payload) {
        String sql;
        try {
            sql = sql(d.sql(), kql);
        } catch (KorykiaiException | UnsupportedOperationException refused) {
            // Both spellings count, and the difference is not meaningful here: DuckDB's shape
            // check raises KorykiaiException while the date_trunc switches raise
            // UnsupportedOperationException. What matters to this test is that no SQL was built
            // from the text at all.
            return;
        }
        assertContained(name, sql, d.literal(), payload);
    }

    /**
     * The statement with every string literal emptied, read the way {@code syntax} says the engine
     * reads one — then the assertion that nothing of {@code payload} is left standing in it.
     *
     * <p>Quoted identifiers are skipped as well, so a payload that legitimately appears inside one
     * (it never does here) would not be mistaken for an escape.
     */
    private static void assertContained(
            String name, String sql, Literal syntax, String... payload) {
        String skeleton = skeleton(name, sql, syntax);
        for (String p : payload) {
            if (skeleton.contains(p)) {
                fail(
                        name
                                + ": '"
                                + p
                                + "' left its literal — under this engine's reading it is part of"
                                + " the statement:\n"
                                + skeleton
                                + "\n\nfull SQL:\n"
                                + sql);
            }
        }
    }

    private static String skeleton(String name, String sql, Literal syntax) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c != '\'' && c != '"' && c != '`' && c != '[') {
                out.append(c);
                i++;
                continue;
            }
            char close = c == '[' ? ']' : c;
            // Backslash escapes apply inside string literals only; no engine honours them inside a
            // quoted identifier.
            boolean backslash = c == '\'' && syntax == Literal.BACKSLASH;
            int end = closing(sql, i, close, backslash);
            if (end < 0) {
                fail(
                        name
                                + ": unterminated "
                                + (c == '\'' ? "string literal" : "quoted identifier")
                                + " from offset "
                                + i
                                + " — this SQL does not parse:\n"
                                + sql);
            }
            out.append(c).append(close);
            i = end + 1;
        }
        return out.toString();
    }

    private static int closing(String sql, int open, char delimiter, boolean backslash) {
        int i = open + 1;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (backslash && c == '\\' && i + 1 < sql.length()) {
                i += 2; // the backslash takes the next character with it
                continue;
            }
            if (c == delimiter) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == delimiter) {
                    i += 2; // a doubled delimiter is one escaped character, not the end
                    continue;
                }
                return i;
            }
            i++;
        }
        return -1;
    }

    private static void eachDialect(java.util.function.BiConsumer<String, Dialect> check) {
        DIALECTS.forEach(check);
    }
}
