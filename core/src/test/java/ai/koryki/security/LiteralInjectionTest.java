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
package ai.koryki.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.antlr.KorykiaiException;
import org.junit.jupiter.api.Test;

/**
 * A KQL string literal must arrive in the SQL as <em>one</em> SQL string literal — whatever the
 * author wrote inside it.
 *
 * <p><b>Why this is the load-bearing test of the whole project.</b> {@code JdbcDatabase} hands the
 * rendered query to {@code Connection.prepareStatement(sql)} and binds nothing: there is not a
 * single {@code ?} in anything this transpiler emits, because a KQL literal is a literal in the
 * SQL, not a parameter. So the usual answer to injection — "use a prepared statement" — is not
 * available here. The escaping in the renderer <em>is</em> the boundary, and these tests are what
 * stands behind it.
 *
 * <p>Two mechanisms do the work, and both are covered here because either alone would be a single
 * point of failure:
 *
 * <ol>
 *   <li>the lexer: {@code SQ_STRING : SINGLE_QUOTE ('\\\'' | .)*? SINGLE_QUOTE} ends the token at
 *       the first unescaped quote, so a bare {@code '} cannot even be written into the middle of a
 *       literal — it is a syntax error long before a renderer sees it;
 *   <li>the renderer: {@code SqlSelectRenderer} turns KQL's backslash escape into SQL's doubled
 *       quote, which is the one escape all eight engines agree on.
 * </ol>
 *
 * @see SqlSkeleton for what "stayed inside the literal" is asserted against
 */
public class LiteralInjectionTest {

    /** The payload every injection cheat sheet opens with. */
    private static final String TAUTOLOGY = "' OR 1=1 --";

    @Test
    void anEscapedQuoteBecomesADoubledQuoteAndNoBackslashSurvives() {
        String sql = Kql.sql("FIND customers c FILTER c.company_name = 'O\\'Brien' FETCH c.city");

        assertTrue(sql.contains("'O''Brien'"), sql);
        // A backslash escape is KQL's spelling, not SQL's. Left in place it would close the literal
        // early on the six engines that do not read backslashes — and open one on MariaDB and
        // Snowflake, which do.
        assertFalse(sql.contains("\\'"), "a KQL backslash escape reached the SQL:\n" + sql);
    }

    @Test
    void theTautologyStaysInsideTheLiteral() {
        String sql =
                Kql.sql("FIND customers c FILTER c.company_name = 'x\\' OR 1=1 --' FETCH c.city");

        SqlSkeleton.assertContained(sql, "OR 1=1", "--");
        assertTrue(sql.contains("'x'' OR 1=1 --'"), sql);
    }

    @Test
    void aStatementTerminatorStaysInsideTheLiteral() {
        String sql =
                Kql.sql(
                        "FIND customers c FILTER c.company_name = 'x\\'; DROP TABLE orders; --'"
                                + " FETCH c.city");

        SqlSkeleton.assertContained(sql, "DROP", ";");
    }

    /**
     * The set operand list is its own rendering path ({@code {0} IN ({1*})}), so it gets its own
     * case: an escape applied to the first operand and forgotten on the rest is exactly the shape
     * of bug a single-operand test misses.
     */
    @Test
    void everyOperandOfAnInSetIsEscaped() {
        String sql =
                Kql.sql(
                        "FIND customers c FILTER c.company_name IN ('safe', 'x\\' OR 1=1 --',"
                                + " 'y\\'; DROP TABLE orders; --') FETCH c.city");

        SqlSkeleton.assertContained(sql, "OR 1=1", "DROP", "--");
    }

    @Test
    void aLikePatternIsEscaped() {
        String sql =
                Kql.sql(
                        "FIND customers c FILTER c.company_name LIKE '%\\' OR 1=1 --%' FETCH"
                                + " c.city");

        SqlSkeleton.assertContained(sql, "OR 1=1", "--");
    }

    /**
     * A literal in the projection, not the filter. {@code FETCH} renders through a different method
     * than {@code FILTER}; both end at {@code SqlSelectRenderer.toSqlUnparenthesized}, and this is
     * what pins that they still do.
     */
    @Test
    void aLiteralInTheProjectionIsEscapedToo() {
        String sql = Kql.sql("FIND customers c FETCH c.city, 'x\\'; DROP TABLE orders; --' tag");

        SqlSkeleton.assertContained(sql, "DROP", ";");
    }

    /**
     * The lexer's half of the defence. A quote that is not escaped ends the token, so the payload
     * is never a literal to begin with and the query does not parse — which is the right answer,
     * and a better one than escaping it would be.
     */
    @Test
    void aBareQuoteCannotBeWrittenIntoTheMiddleOfALiteral() {
        KorykiaiException e =
                assertThrows(
                        KorykiaiException.class,
                        () ->
                                Kql.sql(
                                        "FIND customers c FILTER c.company_name = 'x"
                                                + TAUTOLOGY
                                                + "' FETCH c.city"));
        assertTrue(e.getMessage() != null && !e.getMessage().isBlank(), "no message on " + e);
    }

    /**
     * A backslash immediately before the escaped quote — the shape that turns a doubled quote back
     * into a way out on an engine that reads backslashes.
     *
     * <p>The KQL {@code 'x\\' OR 1=1 -- '} is a literal whose value is {@code x\' OR 1=1 -- }, and
     * it renders as {@code 'x\'' OR 1=1 -- '}. On DuckDB, PostgreSQL, Oracle, SQLite, Trino and SQL
     * Server that is one literal: a backslash is an ordinary character there, so the {@code ''} is
     * an escaped quote and the closing quote is the last one. On MySQL/MariaDB and on Snowflake it
     * would not be — {@code \'} is <em>their</em> escape, which would make the following {@code '}
     * close the literal and leave {@code OR 1=1} standing as SQL. That is why {@code
     * MariadbDialect.textLiteral} and {@code SnowflakeDialect.textLiteral} double every backslash,
     * and why this case is written out rather than left to the general one above. See {@code
     * docs/INJECTION.md}, barrier B2.
     */
    @Test
    void aBackslashBeforeTheEscapedQuoteDoesNotReopenTheLiteral() {
        String sql =
                Kql.sql(
                        "FIND customers c FILTER c.company_name = 'x\\\\' OR 1=1 -- ' FETCH"
                                + " c.city");

        SqlSkeleton.assertContained(sql, "OR 1=1", "--");
        assertTrue(sql.contains("'x\\'' OR 1=1 -- '"), sql);
    }

    /**
     * A doubled quote is not KQL's escape — {@code ''} is an empty literal followed by text. The
     * SQL-shaped payload therefore fails to parse as KQL, and the SQL-shaped escape does not
     * quietly become a way in.
     */
    @Test
    void anSqlStyleDoubledQuoteIsNotAKqlEscape() {
        assertThrows(
                KorykiaiException.class,
                () ->
                        Kql.sql(
                                "FIND customers c FILTER c.company_name = 'x'' OR 1=1 --' FETCH"
                                        + " c.city"));
    }

    /**
     * A unicode escape is not KQL's escape either: backslash-u-0-0-2-7 is six characters of text,
     * not a quote. Worth stating, because a renderer that decoded such escapes before quoting would
     * hand the payload a quote the lexer never saw — and quote it as if it were data.
     */
    @Test
    void aUnicodeEscapeIsPlainTextNotAQuote() {
        String sql =
                Kql.sql(
                        "FIND customers c FILTER c.company_name = 'x\\u0027 OR 1=1 --' FETCH"
                                + " c.city");

        SqlSkeleton.assertContained(sql, "OR 1=1", "--");
    }
}
