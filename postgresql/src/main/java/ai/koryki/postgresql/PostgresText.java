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
package ai.koryki.postgresql;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the text PostgreSQL writes for a composite value and for an array inside one. The driver
 * hands a composite over as a {@code PGobject} holding {@code (1.5,"Main St 1","{a,b}")}: no
 * structure, because the JDBC types of a composite are not known to it.
 *
 * <p>The rules, from the documentation of record and array input: fields and elements are separated
 * by commas; one in double quotes may contain commas, parentheses, braces and blanks, with {@code
 * ""} or a backslash for a quote and a backslash for a backslash; an unquoted empty record field
 * and an unquoted {@code NULL} array element are NULL, while a quoted empty string is a string.
 */
final class PostgresText {

    private PostgresText() {}

    /** The fields of {@code (a,"b c",,d)}; a NULL field is null. */
    static List<String> record(String text) {
        String t = text.strip();
        if (t.length() < 2 || t.charAt(0) != '(' || t.charAt(t.length() - 1) != ')') {
            throw new IllegalArgumentException("not a record literal: " + text);
        }
        List<String> fields = new ArrayList<>();
        int[] pos = {1};
        int end = t.length() - 1;
        while (true) {
            fields.add(field(t, pos, end, ','));
            if (pos[0] >= end) {
                break;
            }
            pos[0]++; // the comma
        }
        return fields;
    }

    /**
     * The elements of {@code {a,"b",NULL,{c,d}}}: a String, null, or for an inner dimension a List.
     */
    static List<Object> array(String text) {
        String t = text.strip();
        int open = t.indexOf('{');
        if (open < 0) {
            throw new IllegalArgumentException("not an array literal: " + text);
        }
        int[] pos = {open};
        return list(t, pos);
    }

    private static List<Object> list(String t, int[] pos) {
        List<Object> out = new ArrayList<>();
        pos[0]++; // the opening brace
        if (t.charAt(pos[0]) == '}') {
            pos[0]++;
            return out;
        }
        while (true) {
            if (t.charAt(pos[0]) == '{') {
                out.add(list(t, pos));
            } else {
                boolean quoted = t.charAt(pos[0]) == '"';
                String value = field(t, pos, t.length(), ',', '}');
                out.add(!quoted && "NULL".equalsIgnoreCase(value) ? null : value);
            }
            char c = t.charAt(pos[0]);
            pos[0]++;
            if (c == '}') {
                return out;
            }
        }
    }

    /** One field up to a separator at the same level; null if empty and unquoted. */
    private static String field(String t, int[] pos, int end, char... separators) {
        if (pos[0] < end && t.charAt(pos[0]) == '"') {
            StringBuilder b = new StringBuilder();
            pos[0]++;
            while (pos[0] < end) {
                char c = t.charAt(pos[0]++);
                if (c == '\\') {
                    b.append(t.charAt(pos[0]++));
                } else if (c == '"') {
                    if (pos[0] < end && t.charAt(pos[0]) == '"') {
                        b.append('"');
                        pos[0]++;
                    } else {
                        break;
                    }
                } else {
                    b.append(c);
                }
            }
            return b.toString();
        }
        int start = pos[0];
        while (pos[0] < end && !isSeparator(t.charAt(pos[0]), separators)) {
            pos[0]++;
        }
        String raw = t.substring(start, pos[0]);
        return raw.isEmpty() ? null : raw;
    }

    private static boolean isSeparator(char c, char[] separators) {
        for (char s : separators) {
            if (c == s) {
                return true;
            }
        }
        return false;
    }
}
