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

import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Schema;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code Util.write}: JSON without the properties that are {@code null}.
 *
 * <p>The rule is only safe if nothing is lost by it, and that is what the round trip holds: a
 * catalog written this way must read back as the catalog it was. It is checked against the shared
 * Northwind catalog, in both languages, because that is the one this project ships and tests
 * against.
 */
class UtilWriteTest {

    private static final String DB = "/ai/koryki/databases/northwind";
    private static final String MODEL = DB + "/model";

    /**
     * Writes every property, null or not -- the yardstick a lean file has to read back equal to.
     */
    private static final ObjectMapper EVERYTHING = new ObjectMapper();

    private static String written(Object value, Path dir) throws IOException {
        Path file = dir.resolve("out.json");
        Util.write(value, file.toFile());
        return Files.readString(file);
    }

    @Test
    void aNullPropertyIsNotWritten(@TempDir Path dir) throws IOException {
        Schema schema = new Schema("n", null, null);

        String json = written(schema, dir);

        assertTrue(json.contains("\"name\""), json);
        assertFalse(json.contains("\"label\""), json);
        assertFalse(json.contains("\"comment\""), json);
        assertFalse(json.contains("\"description\""), json);
    }

    /** Properties are skipped, values are not: a null inside a list is data. */
    @Test
    void aNullInsideAListIsKept(@TempDir Path dir) throws IOException {
        String json = written(Arrays.asList("a", null, "b"), dir);

        assertEquals(3, EVERYTHING.readTree(json).size(), json);
        assertTrue(EVERYTHING.readTree(json).get(1).isNull(), json);
    }

    @Test
    void theSchemaOfACatalogReadsBackTheSame(@TempDir Path dir) throws IOException {
        Schema original = CatalogLoader.db(DB);

        Schema reread = CatalogLoader.readSchemaJson(written(original, dir));

        assertEquals(
                EVERYTHING.writeValueAsString(original), EVERYTHING.writeValueAsString(reread));
    }

    @Test
    void theModelOfACatalogReadsBackTheSameInEveryLanguage(@TempDir Path dir) throws IOException {
        for (Locale locale : new Locale[] {Locale.ENGLISH, Locale.GERMAN}) {
            Model original = CatalogLoader.model(MODEL, locale);

            Model reread =
                    CatalogLoader.readModelJson(
                            new ByteArrayInputStream(
                                    written(original, dir).getBytes(StandardCharsets.UTF_8)));

            assertEquals(
                    EVERYTHING.writeValueAsString(original),
                    EVERYTHING.writeValueAsString(reread),
                    locale.getLanguage());
        }
    }

    /** The point of leaving them out. */
    @Test
    void theLeanFileIsSmallerThanTheOneWithEverything(@TempDir Path dir) throws IOException {
        Schema original = CatalogLoader.db(DB);

        assertTrue(
                written(original, dir).length()
                        < EVERYTHING
                                .writerWithDefaultPrettyPrinter()
                                .writeValueAsString(original)
                                .length());
    }
}
