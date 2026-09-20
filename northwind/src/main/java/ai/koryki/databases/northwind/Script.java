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
package ai.koryki.databases.northwind;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class Script {

    public static void executeScript(Connection connection, String script)
            throws IOException, SQLException {
        List<String> stmts = statements(script);

        try (Statement stmt = connection.createStatement()) {
            int idx = 0;
            for (String s : stmts) {
                long start = System.currentTimeMillis();
                try {
                    stmt.execute(s);
                    System.out.println(
                            "true " + idx++ + " " + (System.currentTimeMillis() - start));
                } catch (SQLException e) {
                    throw new RuntimeException(s, e);
                }
            }
        }
    }

    public static List<String> statements(String resource) throws IOException {
        try (InputStream in = Script.class.getResourceAsStream(resource)) {
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return splitStatements(sql);
        }
    }

    /**
     * Splits a SQL script into individual statements, correctly handling:
     *
     * <ul>
     *   <li>{@code --} line comments (standard SQL, PostgreSQL, MySQL)
     *   <li>{@code #} line comments (MySQL)
     *   <li>{@code /* … *}{@code /} block comments (standard SQL, MySQL, PostgreSQL)
     *   <li>{@code '…'} single-quoted string literals ({@code ''} escape)
     *   <li>{@code "…"} double-quoted identifiers ({@code ""} escape)
     *   <li>{@code `…`} backtick-quoted identifiers (MySQL, {@code ``} escape)
     *   <li>{@code $tag$…$tag$} dollar-quoted strings (PostgreSQL)
     * </ul>
     *
     * Note: MySQL backslash escapes inside strings ({@code \'}, {@code \\}) are not handled; use
     * {@code ''} quoting in scripts intended to run through this method.
     */
    static List<String> splitStatements(String sql) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int i = 0;
        int len = sql.length();

        while (i < len) {
            char c = sql.charAt(i);

            if (c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
                // -- line comment: discard to end of line
                i += 2;
                while (i < len && sql.charAt(i) != '\n') {
                    i++;
                }

            } else if (c == '#') {
                // MySQL # line comment: discard to end of line
                i++;
                while (i < len && sql.charAt(i) != '\n') {
                    i++;
                }

            } else if (c == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
                // /* … */ block comment: discard entirely
                i += 2;
                while (i + 1 < len && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
                    i++;
                }
                i += 2;

            } else if (c == '\'') {
                // Single-quoted string: copy verbatim, '' is the escape sequence
                i = copyQuoted(sql, i, len, '\'', current);

            } else if (c == '"') {
                // Double-quoted identifier/string: copy verbatim, "" is the escape sequence
                i = copyQuoted(sql, i, len, '"', current);

            } else if (c == '`') {
                // MySQL backtick-quoted identifier: copy verbatim, `` is the escape sequence
                i = copyQuoted(sql, i, len, '`', current);

            } else if (c == '$') {
                // PostgreSQL dollar-quoted string: $tag$…$tag$ — copy verbatim
                // Distinguish from positional parameters ($1, $2) by requiring a closing $
                int tagEnd = i + 1;
                while (tagEnd < len
                        && (Character.isLetterOrDigit(sql.charAt(tagEnd))
                                || sql.charAt(tagEnd) == '_')) {
                    tagEnd++;
                }
                if (tagEnd < len && sql.charAt(tagEnd) == '$') {
                    String tag = sql.substring(i, tagEnd + 1); // e.g. "$$" or "$body$"
                    current.append(tag);
                    i = tagEnd + 1;
                    int closeIdx = sql.indexOf(tag, i);
                    if (closeIdx == -1) {
                        // Unclosed dollar-quote: treat remainder as content
                        current.append(sql, i, len);
                        i = len;
                    } else {
                        current.append(sql, i, closeIdx + tag.length());
                        i = closeIdx + tag.length();
                    }
                } else {
                    // Positional parameter or bare $: treat as regular character
                    current.append(c);
                    i++;
                }

            } else if (c == ';') {
                String stmt = current.toString().trim();
                if (!stmt.isEmpty()) {
                    result.add(stmt);
                }
                current.setLength(0);
                i++;

            } else {
                current.append(c);
                i++;
            }
        }

        String remaining = current.toString().trim();
        if (!remaining.isEmpty()) {
            result.add(remaining);
        }

        return result;
    }

    /**
     * Copies a quoted token (delimited by {@code quote}) into {@code out}, handling doubled-quote
     * escaping.
     */
    private static int copyQuoted(String sql, int i, int len, char quote, StringBuilder out) {
        out.append(quote);
        i++;
        while (i < len) {
            char ch = sql.charAt(i);
            out.append(ch);
            i++;
            if (ch == quote) {
                if (i < len && sql.charAt(i) == quote) {
                    out.append(quote);
                    i++;
                } else {
                    break;
                }
            }
        }
        return i;
    }
}
