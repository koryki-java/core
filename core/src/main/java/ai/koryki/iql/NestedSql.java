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
package ai.koryki.iql;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.iql.query.Selector;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * What every dialect needs to render a selector chain and is the same in all of them: which field a
 * step names, which key, and the path of a step chain into JSON. The dialects differ in how the
 * result is written, not in what it means, so the meaning lives here once.
 */
public final class NestedSql {

    private NestedSql() {}

    private static final Pattern REFERENCE =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*(\\.([A-Za-z_][A-Za-z0-9_$]*|\"[^\"]+\"))*");

    /**
     * Whether SQL is a plain reference, a column or a column of a derived table, which can be
     * written as often as it is needed. Anything else is an expression, to be evaluated once.
     */
    public static boolean isReference(String sql) {
        return REFERENCE.matcher(sql).matches();
    }

    /**
     * The name of the field of a struct a step selects, as the type declares it: by position (from
     * the end when negative), or by name, found exactly and otherwise without regard to case, as
     * the names of a query are matched everywhere else; a name the type does not have is returned
     * as written.
     */
    public static String fieldName(TypeDescriptor struct, Selector step) {
        if (step.getIndex() != null) {
            int n = struct.fieldNames().size();
            int index = step.getIndex();
            return struct.fieldNames().get(index > 0 ? index - 1 : n + index);
        }
        String wanted = step.key();
        return struct.fieldNames().stream()
                .filter(f -> f.equals(wanted))
                .findFirst()
                .orElseGet(
                        () ->
                                struct.fieldNames().stream()
                                        .filter(f -> f.equalsIgnoreCase(wanted))
                                        .findFirst()
                                        .orElse(wanted));
    }

    /** The text key a step names: an identifier or a quoted name. */
    public static String key(Selector step) {
        return step.key();
    }

    /** A name as an identifier: bare where it is plain, double-quoted otherwise. */
    public static String identifier(String name, Pattern plain) {
        return plain.matcher(name).matches() ? name : "\"" + name.replace("\"", "\"\"") + "\"";
    }

    /** A SQL text literal for plain text, in the way the dialect writes text. */
    public static String literal(SqlDialect dialect, String text) {
        return dialect.textLiteral("'" + text.replace("'", "''") + "'");
    }

    /**
     * Evaluates an expression once where SQL has to name it several times. A list is read for its
     * size, for its elements and to see whether it is NULL, and writing the expression of a list
     * that many times grows the SQL with every nesting and evaluates a function in it as many times
     * as it appears: {@code random()} would answer differently each time. So an expression that is
     * no plain reference becomes one: a derived table of one row, the value in column {@code L},
     * which {@code over} then names as often as it wants.
     *
     * <p>A reference is returned to {@code over} as it is, and the SQL stays what it was.
     *
     * @param from the FROM clause of one row holding the value, with {@code {0}} for the value and
     *     {@code {1}} for the alias of the derived table, whose column is {@code L}
     * @param over the SQL that uses the value, given the SQL that names it
     */
    public static String bindOnce(
            String value, String from, java.util.function.Function<String, String> over) {
        if (isReference(value)) {
            return over.apply(value);
        }
        // the alias must not be taken by anything the value already says, which is where an
        // enclosing derived table of this kind would show
        int k = 1;
        while (Pattern.compile("(?i)\\bBO_" + k + "\\b").matcher(value).find()) {
            k++;
        }
        String alias = "BO_" + k;
        String[] bound = unbind(value);
        String source;
        if (bound != null) {
            // the value is a list made from a list made once itself: write the two as a chain of
            // derived tables, not as a subquery in a derived table, which not every engine accepts
            source = "FROM (SELECT " + bound[0] + " AS L " + bound[1] + ") " + alias;
        } else {
            source = from.replace("{1}", alias);
            int at = source.indexOf("{0}");
            source = source.substring(0, at) + value + source.substring(at + 3);
        }
        return "(SELECT " + over.apply(alias + ".L") + " " + source + ")";
    }

    /**
     * Takes apart what {@link #bindOnce} made, {@code (SELECT over FROM (SELECT value AS L) BO_k)},
     * into {@code over} and {@code FROM (SELECT value AS L) BO_k}; null for anything else.
     */
    static String[] unbind(String sql) {
        if (!sql.startsWith("(SELECT ") || !sql.endsWith(")")) {
            return null;
        }
        String body = sql.substring("(SELECT ".length(), sql.length() - 1);
        java.util.regex.Matcher tail = Pattern.compile("\\) BO_\\d+$").matcher(body);
        if (!tail.find()) {
            return null;
        }
        int close = tail.start();
        // the parenthesis that closes at 'close': a forward scan that knows text and identifiers
        java.util.ArrayDeque<Integer> open = new java.util.ArrayDeque<>();
        char quote = 0;
        for (int i = 0; i <= close; i++) {
            char c = body.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0; // a doubled quote ends and starts again, which comes out the same
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '(') {
                open.push(i);
            } else if (c == ')') {
                int start = open.isEmpty() ? -1 : open.pop();
                if (i == close) {
                    String before = body.substring(0, Math.max(start, 0));
                    if (start < 0 || !before.endsWith(" FROM ")) {
                        return null;
                    }
                    return new String[] {
                        before.substring(0, before.length() - " FROM ".length()),
                        "FROM " + body.substring(start)
                    };
                }
            }
        }
        return null;
    }

    /** How a JSON path of a dialect names the end of a list. */
    public enum FromEnd {
        /** Not at all: a position from the end is refused. */
        NONE,
        /** {@code [last]}, {@code [last-1]}. */
        LAST,
        /** {@code [#-1]}, {@code [#-2]}. */
        HASH
    }

    /** How a name that is not an identifier is written in a JSON path. */
    public enum Quoting {
        /** {@code .&quot;a b&quot;}. */
        DOT,
        /** {@code [&quot;a b&quot;]}. */
        BRACKET
    }

    private static final Pattern JSON_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * The JSON path of a chain of steps, one entry per prefix: entry {@code k} is the path of the
     * first {@code k} steps, the last one is the whole chain. Positions count from 0 in the path
     * where they count from 1 in a query. A dialect that guards each step by the type of what it is
     * applied to needs the prefixes; the others only the last.
     *
     * @param root {@code $}, or empty for a path without one
     * @param dialect named in the message of a refusal
     */
    public static List<String> jsonPaths(
            List<Selector> steps, String root, FromEnd end, Quoting quoting, String dialect) {
        List<String> paths = new ArrayList<>();
        StringBuilder path = new StringBuilder(root);
        paths.add(path.toString());
        for (Selector step : steps) {
            if (step.getIndex() != null) {
                int n = step.getIndex();
                if (n > 0) {
                    path.append('[').append(n - 1).append(']');
                } else if (end == FromEnd.NONE) {
                    throw new KorykiaiException(
                            "a JSON path in "
                                    + dialect
                                    + " has no position from the end: "
                                    + IQLSerializer.selector(step));
                } else if (end == FromEnd.LAST) {
                    path.append(n == -1 ? "[last]" : "[last-" + (-n - 1) + "]");
                } else {
                    path.append("[#-").append(-n).append(']');
                }
            } else {
                String name = key(step);
                if (JSON_NAME.matcher(name).matches()) {
                    path.append(path.length() > 0 ? "." : "").append(name);
                } else {
                    String quoted = "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
                    if (quoting == Quoting.BRACKET) {
                        path.append('[').append(quoted).append(']');
                    } else {
                        path.append(path.length() > 0 ? "." : "").append(quoted);
                    }
                }
            }
            paths.add(path.toString());
        }
        return paths;
    }

    /** The whole path of a chain: see {@link #jsonPaths}. */
    public static String jsonPath(
            List<Selector> steps, String root, FromEnd end, Quoting quoting, String dialect) {
        List<String> paths = jsonPaths(steps, root, end, quoting, dialect);
        return paths.get(paths.size() - 1);
    }

    /**
     * The JSON path of one member of an object, {@code $.name} for a plain name and {@code
     * $."name"} for any other, with the backslash and the quote escaped; the path is text, still to
     * be written as a literal with {@link #literal}.
     */
    public static String jsonMemberPath(String name) {
        if (JSON_NAME.matcher(name).matches()) {
            return "$." + name;
        }
        return "$.\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
