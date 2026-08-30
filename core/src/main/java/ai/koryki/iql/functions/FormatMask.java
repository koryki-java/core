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
package ai.koryki.iql.functions;

import java.util.Collection;
import ai.koryki.antlr.KorykiaiException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The canonical KQL datetime format-mask vocabulary, and its translation into each dialect's
 * tokens (strftime {@code %}-codes, MySQL DATE_FORMAT codes, T-SQL date-part expressions).
 *
 * <p>The canonical masks are the <b>PostgreSQL template patterns</b> (which PostgreSQL inherits
 * from Oracle, so the two are the same family): {@code YYYY MM DD HH24 HH12 MI SS MON MONTH DY
 * DAY AM PM}. PostgreSQL, Oracle and Snowflake therefore need no translation at all — a KQL mask
 * is already valid there and passes straight through. Only the strftime and MySQL-style dialects
 * translate.
 *
 * <p>Operates on a <em>rendered</em> SQL string literal ({@code '...'}); anything else passes
 * through unchanged — a runtime format expression cannot be translated at transpile time.
 * Double-quoted sections ({@code "literal text"}) are embedded verbatim without token
 * substitution; unrecognised tokens and characters pass through unchanged. Single-pass
 * longest-match replaces the earlier sequential {@code String.replace} chains, which corrupted
 * literal text and depended on replacement order.
 */
public final class FormatMask {

    private FormatMask() {
    }

    /** strftime {@code %}-codes — DuckDB and SQLite. */
    public static final Map<String, String> STRFTIME = Map.ofEntries(
            Map.entry("HH24", "%H"),  Map.entry("HH12", "%I"),
            Map.entry("HH", "%I"),
            Map.entry("YYYY", "%Y"),  Map.entry("YY", "%y"),
            Map.entry("MM", "%m"),    Map.entry("DD", "%d"),
            Map.entry("MI", "%M"),    Map.entry("SS", "%S"),
            Map.entry("AM", "%p"),    Map.entry("PM", "%p"));

    /** MySQL {@code DATE_FORMAT}/{@code STR_TO_DATE} codes — MariaDB and Trino. */
    public static final Map<String, String> MYSQL = Map.ofEntries(
            Map.entry("HH24", "%H"),  Map.entry("HH12", "%h"),
            Map.entry("HH", "%h"),
            Map.entry("YYYY", "%Y"),  Map.entry("YY", "%y"),
            Map.entry("MM", "%m"),    Map.entry("DD", "%d"),
            Map.entry("MI", "%i"),    Map.entry("SS", "%s"),
            Map.entry("AM", "%p"),    Map.entry("PM", "%p"));

    /**
     * Name tokens that once existed and are now rejected.
     *
     * <p>Measured, they gave five different answers for the same day: {@code July} on
     * DuckDB/MariaDB/SQL Server, {@code JULY} padded to nine characters on PostgreSQL,
     * {@code JULI} on Oracle, {@code Juli} on Trino -- and an <em>empty</em> column on SQLite,
     * whose strftime knows neither {@code %B} nor {@code %A}. Oracle and Trino also answered in
     * the client's language rather than a fixed one.
     *
     * <p>A mask containing one of these tokens is therefore not portable. It is rejected instead of
     * silently producing five results; month names belong in the application, which knows the
     * language it writes in anyway.
     */
    public static final List<String> REJECTED = List.of("MONTH", "MON", "DAY", "DY");

    /** The canonical token names, shared by every vocabulary above. */
    public static final Collection<String> TOKENS = STRFTIME.keySet();

    /** True when {@code rendered} is a SQL string literal, i.e. translatable at transpile time. */
    public static boolean isLiteral(String rendered) {
        return rendered.startsWith("'") && rendered.endsWith("'") && rendered.length() >= 2;
    }

    /** The mask body of a rendered string literal, without its surrounding quotes. */
    public static String body(String rendered) {
        return rendered.substring(1, rendered.length() - 1);
    }

    public static String translate(String rendered, Map<String, String> tokens) {
        if (!isLiteral(rendered)) {
            return rendered;
        }
        StringBuilder out = new StringBuilder();
        scan(body(rendered), tokens.keySet(), token -> out.append(tokens.get(token)), out::append);
        return "'" + out + "'";
    }

    /**
     * Walks a mask body, reporting each recognised token and each run of literal text between
     * tokens. Shared by {@link #translate} and by dialects that <em>compile</em> a mask instead of
     * translating it — SQL Server emits a {@code CONCAT} of date-part expressions because Azure SQL
     * Edge has no {@code FORMAT} — so the longest-match rule and the quoted-literal handling have a
     * single definition.
     */
    public static void scan(String mask, Collection<String> tokens,
            Consumer<String> onToken, Consumer<String> onLiteral) {

        List<String> keys = tokens.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();

        StringBuilder literal = new StringBuilder();
        int i = 0;
        while (i < mask.length()) {
            char c = mask.charAt(i);
            if (c == '"') {
                // "literal text" — embed verbatim, tokens inside stay untranslated
                int end = mask.indexOf('"', i + 1);
                if (end < 0) {
                    literal.append(mask, i + 1, mask.length());
                    break;
                }
                literal.append(mask, i + 1, end);
                i = end + 1;
                continue;
            }
            for (String bad : REJECTED) {
                if (mask.regionMatches(i, bad, 0, bad.length())) {
                    throw new KorykiaiException("format mask token '" + bad + "' is not supported: it"
                            + " gives a different answer on every dialect and none at all on SQLite."
                            + " Use MM or DD for the number, and render the name in the application."
                            + " To keep the word as literal text, put it in double quotes.");
                }
            }
            String match = null;
            for (String k : keys) {
                if (mask.regionMatches(i, k, 0, k.length())) {
                    match = k;
                    break;
                }
            }
            if (match == null) {
                literal.append(c);
                i++;
                continue;
            }
            if (literal.length() > 0) {
                onLiteral.accept(literal.toString());
                literal.setLength(0);
            }
            onToken.accept(match);
            i += match.length();
        }
        if (literal.length() > 0) {
            onLiteral.accept(literal.toString());
        }
    }
}
