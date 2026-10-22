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
package ai.koryki.postgresql.catalog;

import ai.koryki.catalog.schema.Index;
import ai.koryki.catalog.schema.IndexKey;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What PostgreSQL knows about the indexes of a table, as catalog {@link Index} entries.
 *
 * <p>Read from `pg_index` and `pg_get_indexdef`, so the catalog says what the database has and not
 * what someone remembers it has. An index is taken over only when every key of it is one this class
 * understands: a column, or one key of a jsonb column read as text ({@code ((attrs ->> 'color'))})
 * with or without a cast ({@code (((doc ->> 'n'))::integer)}). Any other expression leaves the
 * whole index out. That is the safe direction: an index the catalog does not know is one nothing is
 * rewritten for, and the query is as slow, or as fast, as it was.
 */
public final class PostgresqlIndexes {

    private PostgresqlIndexes() {}

    private static final String QUERY =
            "SELECT i.relname, am.amname, ix.indisunique, pg_get_expr(ix.indpred, ix.indrelid),"
                    + " pg_get_indexdef(ix.indexrelid)"
                    + " FROM pg_index ix"
                    + " JOIN pg_class i ON i.oid = ix.indexrelid"
                    + " JOIN pg_class t ON t.oid = ix.indrelid"
                    + " JOIN pg_am am ON am.oid = i.relam"
                    + " WHERE t.relname = ? AND t.relnamespace = current_schema()::regnamespace"
                    + " ORDER BY i.relname";

    /** The indexes of one table; empty when there are none or none can be described. */
    public static List<Index> read(Connection connection, String table) throws SQLException {
        List<Index> indexes = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(QUERY)) {
            statement.setString(1, table);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    parse(
                                    rows.getString(1),
                                    rows.getString(2),
                                    rows.getBoolean(3),
                                    rows.getString(4),
                                    rows.getString(5))
                            .ifPresent(indexes::add);
                }
            }
        }
        return indexes;
    }

    /**
     * Sets the indexes of every table of the schema that the database has any for. A table without
     * any is left alone: the catalog then says nothing about it, which is what it said before.
     */
    public static void apply(Connection connection, Schema schema) throws SQLException {
        for (Table table : schema.getTables()) {
            List<Index> indexes = read(connection, table.getName());
            if (!indexes.isEmpty()) {
                table.setIndexes(indexes);
            }
        }
    }

    /** One row of `pg_index` as an index; empty when a key of it is not understood. */
    static Optional<Index> parse(
            String name, String method, boolean unique, String predicate, String definition) {
        List<String> items = keyItems(definition);
        if (items == null || items.isEmpty()) {
            return Optional.empty();
        }
        Index index = new Index();
        index.setName(name);
        index.setMethod(method);
        if (unique) {
            index.setUnique(true);
        }
        if (predicate != null) {
            index.setWhere(predicate);
        }
        List<IndexKey> keys = new ArrayList<>();
        for (String item : items) {
            Optional<IndexKey> key = key(item, index);
            if (key.isEmpty()) {
                return Optional.empty();
            }
            keys.add(key.get());
        }
        index.setKeys(keys);
        return Optional.of(index);
    }

    private static final Pattern PLAIN =
            Pattern.compile(
                    "^(\\w+|\"[^\"]+\")(?:\\s+(\\w+))?(?:\\s+(?:ASC|DESC|NULLS\\s+\\w+))*$");
    private static final Pattern CAST = Pattern.compile("^(\\(.*\\))::([a-z_ ]+)$");
    private static final Pattern VALUE =
            Pattern.compile("^(\\w+|\"[^\"]+\") ->> '((?:[^']|'')*)'(?:::text)?$");
    private static final java.util.Set<String> ORDERING = java.util.Set.of("ASC", "DESC", "NULLS");

    private static Optional<IndexKey> key(String item, Index index) {
        String s = item.strip();
        if (!s.startsWith("(")) {
            Matcher plain = PLAIN.matcher(s);
            if (!plain.matches()) {
                return Optional.empty();
            }
            String opclass = plain.group(2);
            if (opclass != null && !ORDERING.contains(opclass.toUpperCase(java.util.Locale.ROOT))) {
                index.setOpclass(opclass);
            }
            return Optional.of(new IndexKey(unquote(plain.group(1)), null, null));
        }
        String inner = unwrap(s);
        String as = null;
        Matcher cast = CAST.matcher(inner);
        if (cast.matches()) {
            inner = unwrap(cast.group(1));
            as = cast.group(2).strip().toUpperCase(java.util.Locale.ROOT);
        }
        Matcher value = VALUE.matcher(inner);
        if (!value.matches()) {
            return Optional.empty();
        }
        return Optional.of(
                new IndexKey(
                        unquote(value.group(1)),
                        List.of(value.group(2).replace("''", "'")),
                        as != null ? as : "TEXT"));
    }

    private static String unquote(String identifier) {
        return identifier.startsWith("\"")
                ? identifier.substring(1, identifier.length() - 1).replace("\"\"", "\"")
                : identifier;
    }

    /** Strips the parentheses that wrap the whole text, as often as they do. */
    private static String unwrap(String s) {
        String t = s.strip();
        while (t.startsWith("(") && closing(t, 0) == t.length() - 1) {
            t = t.substring(1, t.length() - 1).strip();
        }
        return t;
    }

    /** The position of the parenthesis that closes the one at {@code open}; -1 when none does. */
    private static int closing(String s, int open) {
        int depth = 0;
        boolean quoted = false;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'') {
                quoted = !quoted;
            } else if (!quoted && c == '(') {
                depth++;
            } else if (!quoted && c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * The keys between the parentheses after {@code USING method}, split where a comma stands
     * alone.
     */
    private static List<String> keyItems(String definition) {
        int using = definition.indexOf(" USING ");
        if (using < 0) {
            return null;
        }
        int open = definition.indexOf('(', using);
        int close = open < 0 ? -1 : closing(definition, open);
        if (close < 0) {
            return null;
        }
        String keys = definition.substring(open + 1, close);
        List<String> items = new ArrayList<>();
        int depth = 0;
        boolean quoted = false;
        int start = 0;
        for (int i = 0; i < keys.length(); i++) {
            char c = keys.charAt(i);
            if (c == '\'') {
                quoted = !quoted;
            } else if (!quoted && c == '(') {
                depth++;
            } else if (!quoted && c == ')') {
                depth--;
            } else if (!quoted && depth == 0 && c == ',') {
                items.add(keys.substring(start, i).strip());
                start = i + 1;
            }
        }
        items.add(keys.substring(start).strip());
        return items;
    }
}
