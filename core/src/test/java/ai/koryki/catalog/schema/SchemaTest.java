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
package ai.koryki.catalog.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.Util;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The schema prefix as a catalog property: read from db.json, written back, copied. */
class SchemaTest {

    private static final String DB = "/ai/koryki/databases/northwind";

    /** The catalog the way a file gets it: through {@link Util#write}. */
    private static String written(Schema schema, Path dir) throws IOException {
        Path file = dir.resolve("db.json");
        Util.write(schema, file.toFile());
        return Files.readString(file);
    }

    @Test
    void aCatalogWithoutAPrefixHasNone() {
        assertNull(CatalogLoader.db(DB).getSchemaPrefix());
    }

    /**
     * Not written as {@code null}: a catalog that does not use the property should not gain a line
     * that means nothing to it. {@code Util.write} leaves out every null property, so this needs no
     * rule on the class -- and this test is what says so.
     */
    @Test
    void anUnsetPrefixIsNotInTheWrittenJson(@TempDir Path dir) throws IOException {
        assertFalse(written(CatalogLoader.db(DB), dir).contains("schemaPrefix"));
    }

    @Test
    void aSetPrefixSurvivesAWriteAndARead(@TempDir Path dir) throws IOException {
        Schema db = CatalogLoader.db(DB);
        db.setSchemaPrefix("sales");

        String json = written(db, dir);

        assertTrue(json.contains("\"schemaPrefix\" : \"sales\""), json);
        assertEquals("sales", CatalogLoader.readSchemaJson(json).getSchemaPrefix());
    }

    /**
     * A copy that drops the prefix would render the same catalog unqualified and no test of the
     * original would notice.
     */
    @Test
    void aDeepCopyKeepsThePrefix() {
        Schema db = CatalogLoader.db(DB);
        db.setSchemaPrefix("sales");

        assertEquals("sales", Schema.deepCopy(db).getSchemaPrefix());
    }

    @Test
    void aDeepCopyOfACatalogWithoutOneStillHasNone() {
        assertNull(Schema.deepCopy(CatalogLoader.db(DB)).getSchemaPrefix());
    }
}
