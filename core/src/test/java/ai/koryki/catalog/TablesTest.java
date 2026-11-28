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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Index;
import ai.koryki.catalog.schema.IndexKey;
import ai.koryki.catalog.schema.Table;
import java.util.List;
import org.junit.jupiter.api.Test;

/** When columns pin down at most one row: the primary key, or a unique index over all rows. */
class TablesTest {

    private static Table table(String... pk) {
        Table t = new Table("t");
        for (String name : List.of("id", "a", "b", "doc")) {
            Column c = new Column();
            c.setName(name);
            c.setPkPos(List.of(pk).indexOf(name) + 1);
            t.getColumns().add(c);
        }
        return t;
    }

    private static Index unique(String... columns) {
        Index i = new Index();
        i.setUnique(true);
        i.setKeys(java.util.Arrays.stream(columns).map(c -> new IndexKey(c, null, null)).toList());
        return i;
    }

    @Test
    void thePrimaryKeyIsAKey() {
        Table t = table("id");
        assertTrue(Tables.isKey(t, List.of("id")));
        assertTrue(Tables.isKey(t, List.of("ID", "a")), "more columns than the key, and no case");
        assertFalse(Tables.isKey(t, List.of("a")));
    }

    @Test
    void aCompositeKeyNeedsAllItsColumns() {
        Table t = table("id", "a");
        assertFalse(Tables.isKey(t, List.of("id")));
        assertTrue(Tables.isKey(t, List.of("id", "a")));
    }

    @Test
    void aUniqueIndexIsAKey() {
        Table t = table("id");
        t.setIndexes(List.of(unique("a", "b")));
        assertTrue(Tables.isKey(t, List.of("a", "b")));
        assertFalse(Tables.isKey(t, List.of("a")), "part of a composite unique key");
    }

    @Test
    void anIndexThatIsNotUniqueOrNotWholeIsNoKey() {
        Table t = table("id");
        Index plain = unique("a");
        plain.setUnique(false);
        Index unknown = unique("b");
        unknown.setUnique(null);
        Index partial = unique("doc");
        partial.setWhere("deleted = false");
        t.setIndexes(List.of(plain, unknown, partial));
        assertFalse(Tables.isKey(t, List.of("a")));
        assertFalse(Tables.isKey(t, List.of("b")));
        assertFalse(Tables.isKey(t, List.of("doc")), "a partial index holds only some rows");
    }

    @Test
    void aKeyInsideAColumnIsNoKeyOfTheColumn() {
        Table t = table("id");
        Index byPath = new Index();
        byPath.setUnique(true);
        byPath.setKeys(List.of(new IndexKey("doc", List.of("color"), "TEXT")));
        t.setIndexes(List.of(byPath));
        assertFalse(Tables.isKey(t, List.of("doc")));
    }

    @Test
    void whatTheCatalogDoesNotSayIsNotAKey() {
        Table none = table();
        assertFalse(Tables.isKey(none, List.of("id", "a", "b", "doc")));
        assertFalse(Tables.hasKeyInformation(none));

        Table knownToHaveNone = table();
        knownToHaveNone.setIndexes(List.of());
        assertFalse(Tables.isKey(knownToHaveNone, List.of("id")));
        assertTrue(Tables.hasKeyInformation(knownToHaveNone));

        assertTrue(Tables.hasKeyInformation(table("id")));
    }

    private static Table notNull(Table t, String... names) {
        for (String n : names) {
            t.getColumns().stream()
                    .filter(c -> c.getName().equals(n))
                    .forEach(c -> c.setNullable(false));
        }
        return t;
    }

    @Test
    void theIdentityIsThePrimaryKeyThenTheUniqueKeysOfColumnsThatAreNotNull() {
        Table t = notNull(table("id"), "a", "b");
        t.setIndexes(List.of(unique("a", "b"), unique("b")));
        assertEquals(
                List.of(List.of("id"), List.of("b"), List.of("a", "b")), Tables.identifying(t));
    }

    @Test
    void aUniqueKeyOverANullableColumnIsNoIdentity() {
        Table t = table();
        t.setIndexes(List.of(unique("a")));
        assertEquals(List.of(), Tables.identifying(t), "NULLs are equal in a DISTINCT");
        notNull(t, "a");
        assertEquals(List.of(List.of("a")), Tables.identifying(t));
    }

    @Test
    void aKeyThatRepeatsAnEarlierOneIsLeftOut() {
        Table t = notNull(table("id"), "id");
        t.setIndexes(List.of(unique("id")));
        assertEquals(List.of(List.of("id")), Tables.identifying(t));
    }

    @Test
    void aTableThatStatesNoKeyHasNoIdentity() {
        Table t = notNull(table(), "a");
        assertEquals(List.of(), Tables.identifying(t));
        Index partial = unique("a");
        partial.setWhere("deleted = false");
        Index inside = new Index();
        inside.setUnique(true);
        inside.setKeys(List.of(new IndexKey("a", List.of("x"), "TEXT")));
        t.setIndexes(List.of(partial, inside));
        assertEquals(List.of(), Tables.identifying(t));
    }
}
