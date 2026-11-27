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
import ai.koryki.catalog.schema.Table;
import java.util.Collection;
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
}
