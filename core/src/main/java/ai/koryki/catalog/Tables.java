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
package ai.koryki.catalog;

import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Index;
import ai.koryki.catalog.schema.IndexKey;
import ai.koryki.catalog.schema.Table;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * What can be asked of a {@link Table}. The table is a bean that holds what the catalog says; the
 * questions about it are here.
 */
public final class Tables {

    private Tables() {}

    /** The column of this exact name. */
    public static Optional<Column> column(Table table, String name) {
        return table.getColumns().stream().filter(c -> c.getName().equals(name)).findFirst();
    }

    /** The indexes with a key on this column, whatever the path; empty when none or not known. */
    public static List<Index> indexesOn(Table table, String column) {
        if (table.getIndexes() == null) {
            return List.of();
        }
        return table.getIndexes().stream()
                .filter(i -> i.getKeys().stream().anyMatch(k -> column.equals(k.getColumn())))
                .toList();
    }

    /**
     * Whether a join that compares these columns, each with one value, meets at most one row of the
     * table: they contain the whole primary key, or all the keys of a unique index that covers
     * every row and indexes plain columns.
     *
     * <p>Answers <em>false</em> when the catalog does not say: a table without a primary key and
     * with {@code indexes == null} is not known to have a key. A caller that wants the benefit of
     * the doubt asks {@link #hasKeyInformation} first. Names are compared without case.
     *
     * <p>For joins only. A unique key over nullable columns allows several rows with NULL, and none
     * of them is met by a comparison with {@code =}; for {@code GROUP BY} or {@code DISTINCT},
     * where NULLs are equal, that reasoning does not hold.
     */
    public static boolean isKey(Table table, Collection<String> columns) {
        Set<String> given = new HashSet<>();
        columns.forEach(c -> given.add(c.toLowerCase(Locale.ROOT)));

        Set<String> pk = new HashSet<>();
        for (Column c : table.getColumns()) {
            if (c.getPkPos() > 0) {
                pk.add(c.getName().toLowerCase(Locale.ROOT));
            }
        }
        if (!pk.isEmpty() && given.containsAll(pk)) {
            return true;
        }
        if (table.getIndexes() == null) {
            return false;
        }
        for (Index index : table.getIndexes()) {
            if (!Boolean.TRUE.equals(index.getUnique())
                    || index.getKeys().isEmpty()
                    || !Indexes.coversAllRows(index)
                    || index.getKeys().stream().anyMatch(Indexes::hasPath)) {
                continue;
            }
            if (index.getKeys().stream()
                    .allMatch(k -> given.contains(k.getColumn().toLowerCase(Locale.ROOT)))) {
                return true;
            }
        }
        return false;
    }

    /** Whether the catalog says anything about the keys of the table: a primary key or indexes. */
    public static boolean hasKeyInformation(Table table) {
        return table.getIndexes() != null
                || table.getColumns().stream().anyMatch(c -> c.getPkPos() > 0);
    }

    /**
     * The keys that tell the rows of a table apart, for a caller that needs a row's identity
     * ({@code DISTINCT} of it, a count of it): the primary key first, then each unique index that
     * covers every row, indexes plain columns and has only columns that are declared NOT NULL,
     * smallest first (by name among equals). A key that repeats the columns of an earlier one is
     * left out. Each key is the list of its column names, in key order.
     *
     * <p>Not {@link #isKey}: a join never meets a NULL, but {@code GROUP BY} and {@code DISTINCT}
     * treat NULLs as equal, so a unique key over nullable columns does not identify rows here.
     *
     * <p>Empty when the catalog states no such key; a table with duplicate rows has no identity to
     * be found in a catalog.
     */
    public static List<List<String>> identifying(Table table) {
        List<List<String>> keys = new ArrayList<>();
        List<String> primary =
                table.getColumns().stream()
                        .filter(c -> c.getPkPos() > 0)
                        .sorted(Comparator.comparingInt(Column::getPkPos))
                        .map(Column::getName)
                        .toList();
        if (!primary.isEmpty()) {
            keys.add(primary);
        }
        if (table.getIndexes() == null) {
            return keys;
        }
        table.getIndexes().stream()
                .filter(i -> Boolean.TRUE.equals(i.getUnique()))
                .filter(i -> !i.getKeys().isEmpty() && Indexes.coversAllRows(i))
                .filter(i -> i.getKeys().stream().noneMatch(Indexes::hasPath))
                .sorted(
                        Comparator.comparingInt((Index i) -> i.getKeys().size())
                                .thenComparing(i -> String.valueOf(i.getName())))
                .map(i -> i.getKeys().stream().map(IndexKey::getColumn).toList())
                .filter(columns -> allNotNull(table, columns))
                .filter(columns -> keys.stream().noneMatch(k -> sameColumns(k, columns)))
                .forEach(keys::add);
        return keys;
    }

    private static boolean allNotNull(Table table, List<String> columns) {
        return columns.stream()
                .allMatch(
                        c ->
                                table.getColumns().stream()
                                        .anyMatch(
                                                x ->
                                                        x.getName().equalsIgnoreCase(c)
                                                                && Boolean.FALSE.equals(
                                                                        x.getNullable())));
    }

    private static boolean sameColumns(List<String> a, List<String> b) {
        return lower(a).equals(lower(b));
    }

    private static Set<String> lower(List<String> columns) {
        Set<String> set = new HashSet<>();
        columns.forEach(c -> set.add(c.toLowerCase(Locale.ROOT)));
        return set;
    }
}
