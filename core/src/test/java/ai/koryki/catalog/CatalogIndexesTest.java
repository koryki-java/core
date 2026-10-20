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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.koryki.catalog.schema.Index;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import ai.koryki.databases.cases.Fixtures;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The indexes of a table in a {@code db.json}: read, written back, copied. A table that says
 * nothing about its indexes must be written without the property, or every catalog on disk changes
 * the day this exists.
 */
class CatalogIndexesTest {

    private static final String WITH_INDEXES =
            """
            {
              "name" : "demo",
              "tables" : [ {
                "columns" : [ {
                  "name" : "attrs",
                  "namePos" : 0,
                  "nullable" : true,
                  "pkPos" : 0
                } ],
                "indexes" : [ {
                  "keys" : [ {
                    "as" : "TEXT",
                    "column" : "attrs",
                    "path" : [ "color" ]
                  } ],
                  "method" : "btree",
                  "name" : "idx_attrs_color"
                }, {
                  "keys" : [ {
                    "column" : "attrs"
                  } ],
                  "method" : "gin",
                  "name" : "idx_attrs_gin",
                  "opclass" : "jsonb_path_ops",
                  "unique" : false,
                  "where" : "attrs IS NOT NULL"
                } ],
                "name" : "t"
              } ]
            }""";

    @Test
    void indexesAreRead() {
        Table table = CatalogLoader.readSchemaJson(WITH_INDEXES).getTable("t").orElseThrow();

        List<Index> indexes = table.getIndexes();
        assertEquals(2, indexes.size());
        assertEquals("btree", indexes.get(0).getMethod());
        assertEquals(List.of("color"), indexes.get(0).getKeys().get(0).getPath());
        assertEquals("TEXT", indexes.get(0).getKeys().get(0).getAs());
        assertTrue(indexes.get(0).getKeys().get(0).hasPath());
        assertTrue(indexes.get(0).coversAllRows());
        assertEquals("jsonb_path_ops", indexes.get(1).getOpclass());
        assertFalse(indexes.get(1).getKeys().get(0).hasPath());
        assertFalse(indexes.get(1).coversAllRows());
        assertEquals(2, table.indexesOn("attrs").size());
        assertTrue(table.indexesOn("other").isEmpty());
    }

    @Test
    void aTableWithoutIndexesKnowsNothingAboutThem() {
        Schema schema =
                CatalogLoader.readSchemaJson(
                        "{\"name\":\"d\",\"tables\":[{\"name\":\"t\",\"columns\":[]}]}");

        Table table = schema.getTable("t").orElseThrow();
        assertNull(table.getIndexes());
        assertTrue(table.indexesOn("x").isEmpty());
    }

    @Test
    void anAbsentPropertyIsNotWrittenAsAnEmptyOne(@TempDir Path dir) throws IOException {
        Schema schema =
                CatalogLoader.readSchemaJson(
                        "{\"name\":\"d\",\"tables\":[{\"name\":\"t\",\"columns\":[]}]}");
        File out = dir.resolve("db.json").toFile();

        Util.write(schema, out);

        assertFalse(Files.readString(out.toPath()).contains("indexes"));
    }

    @Test
    void indexesSurviveAWriteAndARead(@TempDir Path dir) throws IOException {
        File out = dir.resolve("db.json").toFile();

        Util.write(CatalogLoader.readSchemaJson(WITH_INDEXES), out);

        Table table =
                CatalogLoader.readSchemaJson(Files.readString(out.toPath()))
                        .getTable("t")
                        .orElseThrow();
        assertEquals(2, table.getIndexes().size());
        assertEquals("idx_attrs_gin", table.getIndexes().get(1).getName());
        assertEquals("attrs IS NOT NULL", table.getIndexes().get(1).getWhere());
    }

    @Test
    void aCopyKeepsTheIndexes() {
        Schema copy = SchemaCopy.deepCopy(CatalogLoader.readSchemaJson(WITH_INDEXES));

        List<Index> indexes = copy.getTable("t").orElseThrow().getIndexes();
        assertNotNull(indexes);
        assertEquals(List.of("color"), indexes.get(0).getKeys().get(0).getPath());
        assertEquals("jsonb_path_ops", indexes.get(1).getOpclass());
    }

    /**
     * The catalog of the complex bed as the northwind repository ships it, written back as it is
     * read: the format of the indexes there is the format this writes, whether or not that checkout
     * has them yet.
     */
    @Test
    void theComplexCatalogOfPostgresqlIsWrittenAsItIsRead(@TempDir Path dir) throws IOException {
        Path file =
                Fixtures.root()
                        .resolve(
                                "catalog/src/main/resources/ai/koryki/postgresql/databases/complex/db.json");
        assumeTrue(Files.isRegularFile(file), "no catalog at " + file);
        String json = Files.readString(file);
        File out = dir.resolve("db.json").toFile();

        Util.write(CatalogLoader.readSchemaJson(json), out);

        assertEquals(json.strip(), Files.readString(out.toPath()).strip());
    }
}
