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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.databases.complex.duckdb.ComplexService;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Text that reaches the SQL through a selector or a lambda, for every dialect: the fifth way author
 * text can get into a statement (docs/INJECTION.md, path 6).
 *
 * <p>A selector names a struct field, a map key or a JSON member, and what it names is written into
 * the statement: as a text literal ({@code t.attrs['k']}), as part of a JSON path inside a literal
 * ({@code '$.a.b'}), as an identifier ({@code (t.dims).w}), or as a bare word in a path ({@code
 * a.b}). Each dialect uses its own mix of the four, so one key is escaped four ways across the
 * eight of them, and the only check that means anything is the one the other Hostile tests make:
 * read the rendered SQL the way the engine would, and look for the payload outside every literal
 * and quoted identifier.
 *
 * <p>The keys are the ones that break a naive rendering: a quote, a backslash (two engines read it
 * as an escape), a double quote and a bracket (the delimiters of a JSON path and of an identifier),
 * a {@code $} and a dot (path syntax), a statement terminator and a comment.
 */
class HostileSelectorTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    private enum Literal {
        STANDARD,
        BACKSLASH
    }

    private record Dialect(SqlDialect sql, Literal literal) {
        /**
         * SQL Server quotes an identifier in brackets; everywhere else a bracket is a subscript.
         */
        boolean brackets() {
            return sql instanceof MssqlDialect;
        }
    }

    private static final Map<String, Dialect> DIALECTS = dialects();

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
        return m;
    }

    private static LinkResolver resolver;

    private static LinkResolver resolver() {
        if (resolver == null) {
            resolver = ComplexService.resolver();
        }
        return resolver;
    }

    private static String sql(SqlDialect dialect, String fetch) {
        return KQLTranspiler.builder("FIND check_complex t FETCH " + fetch, resolver())
                .functions(dialect.getFunctionRenderer())
                .build()
                .getSql(new SqlQueryRenderer(dialect, UTC));
    }

    /**
     * The key as KQL writes it: a single-quoted name, {@code \'} for a quote. That is the only
     * escape KQL has: a backslash stands for itself, so it is not doubled. A backslash directly
     * before a quote is written as the backslash and then the escaped quote ({@code x\\'}), which
     * is exactly the case that lets a string leave its literal on an engine that reads a backslash
     * as an escape.
     */
    private static String kql(String key) {
        return "'" + key.replace("'", "\\'") + "'";
    }

    /** What an attacker puts in a key, and the word of it that must never be seen as SQL. */
    private static final Map<String, String> KEYS = keys();

    private static Map<String, String> keys() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("x' OR 1=1 --", "OR 1=1");
        m.put("x'); DROP TABLE check_complex; --", "DROP TABLE");
        m.put("x\\' OR 1=1 --", "OR 1=1");
        m.put("x\\", "unterminated");
        m.put("x\" OR 1=1 --", "OR 1=1");
        m.put("x\"]; DROP TABLE check_complex; --", "DROP TABLE");
        m.put("x'] || (SELECT 1) || ['", "SELECT 1");
        m.put("$.a') OR ('1'='1", "OR (");
        m.put("a.b", "unterminated");
        m.put("a b", "unterminated");
        m.put("]", "unterminated");
        m.put("\n; DROP TABLE check_complex; --", "DROP TABLE");
        return m;
    }

    // ---------------------------------------------------------------------- the three kinds

    @Test
    void aHostileMapKeyStaysInsideItsLiteralOnEveryDialect() {
        eachKey((name, d, key, word) -> render(name, d, "t.attrs@" + kql(key), word));
    }

    @Test
    void aHostileStructFieldStaysInsideItsQuotesOnEveryDialect() {
        // the name need not be a field: an unknown one is rendered as written, so it is as hostile
        // as a key, and every dialect that writes a field as an identifier must quote it
        eachKey((name, d, key, word) -> render(name, d, "t.dims@" + kql(key), word));
    }

    @Test
    void aHostileJsonMemberStaysInsideItsPathOnEveryDialect() {
        eachKey((name, d, key, word) -> render(name, d, "t.doc@" + kql(key), word));
    }

    @Test
    void aHostileKeyDeepInAChainStaysContained() {
        eachKey(
                (name, d, key, word) -> {
                    render(name, d, "t.doc@a@" + kql(key) + "@1", word);
                    render(name, d, "t.address@geo@" + kql(key), word);
                    render(name, d, "t.deep@1@" + kql(key) + "@b", word);
                });
    }

    @Test
    void aHostileKeyInAFunctionArgumentAndInAComparisonStaysContained() {
        eachKey(
                (name, d, key, word) -> {
                    render(name, d, "to_text(t.doc@" + kql(key) + ")", word);
                    render(name, d, "to_integer(t.doc@" + kql(key) + ") + 1", word);
                    render(name, d, "coalesce(t.attrs@" + kql(key) + ", 'none')", word);
                });
    }

    // ------------------------------------------------------------------- lambda parameters

    /**
     * A parameter is a bare word of the query, and the words that are reserved in SQL are legal
     * names for one. Rendered as a column alias or an identifier they must be quoted or renamed, or
     * the statement does not parse, which is the loud half of the failure; what this asserts is the
     * quiet half, that the word does not end up outside the quotes as keyword and reads as SQL.
     */
    @Test
    void aReservedWordAsAParameterNeverStandsBareAsAKeyword() {
        for (String word : List.of("select", "from", "where", "end", "order", "case", "union")) {
            for (Map.Entry<String, Dialect> e : DIALECTS.entrySet()) {
                String name = e.getKey();
                Dialect d = e.getValue();
                String sql;
                try {
                    sql =
                            sql(
                                    d.sql(),
                                    "list_transform(t.scores, " + word + " -> " + word + " * 2) r");
                } catch (KorykiaiException | UnsupportedOperationException refused) {
                    continue; // refusing is allowed; rendering something that reads as SQL is not
                }
                String skeleton = skeleton(name, sql, d);
                assertTrue(
                        !java.util.regex.Pattern.compile("(?i)(^|[^_A-Za-z0-9])" + word + "\\s*\\*")
                                .matcher(skeleton)
                                .find(),
                        name + ": the parameter '" + word + "' stands bare in:\n" + sql);
            }
        }
    }

    // ----------------------------------------------------------------------- honest keys

    @Test
    void honestKeysStillRenderOnEveryDialect() {
        for (Map.Entry<String, Dialect> e : DIALECTS.entrySet()) {
            Dialect d = e.getValue();
            // a dialect may refuse a kind of value it does not have (a map on SQLite), but a
            // plain key must never be what makes it refuse
            for (String fetch :
                    List.of(
                            "t.attrs@color c",
                            "t.dims@w w",
                            "t.doc@n n",
                            "t.attrs@'a key' k",
                            "t.doc@'a key' j")) {
                try {
                    sql(d.sql(), fetch);
                } catch (KorykiaiException | UnsupportedOperationException refused) {
                    assertTrue(
                            refused.getMessage().contains("cannot")
                                    || refused.getMessage().contains("not supported"),
                            e.getKey() + ": " + fetch + ": " + refused.getMessage());
                }
            }
        }
    }

    /**
     * The tests above accept a refusal, which would let them pass without testing anything if every
     * key were refused. Every dialect has JSON, so every one must render a hostile JSON member, and
     * what it writes is shown by this one: the key is in the statement, inside quotes.
     */
    @Test
    void aHostileJsonMemberIsRenderedNotRefusedOnEveryDialect() {
        for (Map.Entry<String, Dialect> e : DIALECTS.entrySet()) {
            String sql = sql(e.getValue().sql(), "t.doc@" + kql("x' OR 1=1 --") + " v");
            assertTrue(sql.contains("OR 1=1"), e.getKey() + ":\n" + sql);
        }
    }

    @Test
    void everyDialectIsCovered() {
        assertEquals(8, DIALECTS.size());
    }

    // ---------------------------------------------------------------------------- machinery

    private interface Check {
        void accept(String name, Dialect d, String key, String word);
    }

    private static void eachKey(Check check) {
        for (Map.Entry<String, Dialect> e : DIALECTS.entrySet()) {
            for (Map.Entry<String, String> k : KEYS.entrySet()) {
                check.accept(e.getKey(), e.getValue(), k.getKey(), k.getValue());
            }
        }
    }

    /**
     * Renders {@code fetch} and requires a refusal or a rendering in which the dangerous word of
     * the key is not part of the statement outside every literal and quoted identifier. A dialect
     * may refuse a key it cannot express; it may not write it as SQL.
     */
    private static void render(String name, Dialect d, String fetch, String word) {
        String sql;
        try {
            sql = sql(d.sql(), fetch);
        } catch (KorykiaiException | UnsupportedOperationException refused) {
            return;
        }
        String skeleton = skeleton(name + " " + fetch, sql, d);
        if (!"unterminated".equals(word) && skeleton.contains(word)) {
            fail(
                    name
                            + ": '"
                            + word
                            + "' left its literal or quotes for "
                            + fetch
                            + " - under this engine's reading it is SQL:\n"
                            + skeleton
                            + "\n\nfull SQL:\n"
                            + sql);
        }
    }

    /** The statement with every literal and quoted identifier emptied, read as the engine does. */
    private static String skeleton(String name, String sql, Dialect dialect) {
        Literal syntax = dialect.literal();
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c != '\'' && c != '"' && c != '`' && !(c == '[' && dialect.brackets())) {
                out.append(c);
                i++;
                continue;
            }
            char close = c == '[' ? ']' : c;
            boolean backslash = c == '\'' && syntax == Literal.BACKSLASH;
            int end = closing(sql, i, close, backslash);
            if (end < 0) {
                fail(
                        name
                                + ": unterminated "
                                + (c == '\'' ? "string literal" : "quoted identifier")
                                + " from offset "
                                + i
                                + " - this SQL does not parse:\n"
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
                i += 2;
                continue;
            }
            if (c == delimiter) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == delimiter) {
                    i += 2;
                    continue;
                }
                return i;
            }
            i++;
        }
        return -1;
    }
}
