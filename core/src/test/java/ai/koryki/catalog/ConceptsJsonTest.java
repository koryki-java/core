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
import static org.junit.jupiter.api.Assertions.assertNull;

import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Schema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/**
 * The concepts of a db.json: read as written, written without what is not there, and read back as
 * the same catalog. A column keeps its own fields beside the reference for now, so both forms are
 * in the file.
 */
class ConceptsJsonTest {

    private static final ObjectMapper EVERYTHING = new ObjectMapper();

    private static final String DB =
            """
            { "name": "complex",
              "tables": [ { "name": "orders", "columns": [
                { "name": "id", "typeFamily": "INTEGER", "dialectType": "INTEGER", "pkPos": 1 },
                { "name": "billing", "conceptRef": "address",
                  "typeFamily": "STRUCT", "dialectType": "STRUCT(street VARCHAR)" }
              ] } ],
              "relations": [],
              "concepts": {
                "address": { "label": "Address", "description": "A postal address",
                             "typeFamily": "STRUCT",
                             "entries": { "street": "VARCHAR", "geo": "position", "phones": "phone_list" } },
                "position": { "typeFamily": "STRUCT", "entries": { "lat": "latitude", "lon": "latitude" } },
                "latitude": { "label": "Latitude", "typeFamily": "FLOAT", "dialectType": "DOUBLE",
                              "unit": "deg" },
                "phone_list": { "typeFamily": "ARRAY", "arraySize": 3, "entries": { "element": "VARCHAR" } }
              } }
            """;

    private static String written(Schema schema, Path dir) throws IOException {
        Path file = dir.resolve("db.json");
        Util.write(schema, file.toFile());
        return Files.readString(file);
    }

    @Test
    void theConceptsAreReadAsWritten() {
        Schema schema = CatalogLoader.readSchemaJson(DB);

        assertEquals(
                List.of("address", "position", "latitude", "phone_list"),
                List.copyOf(schema.getConcepts().keySet()));
        Concept address = schema.getConcepts().get("address");
        assertEquals("Address", address.getLabel());
        assertEquals("A postal address", address.getDescription());
        assertEquals("STRUCT", address.getTypeFamily());
        assertEquals(
                List.of("street", "geo", "phones"), List.copyOf(address.getEntries().keySet()));
        assertEquals("position", address.getEntries().get("geo").getReference());
        assertEquals("deg", schema.getConcepts().get("latitude").getUnit());
        assertEquals(3, schema.getConcepts().get("phone_list").getArraySize());
    }

    @Test
    void aColumnCarriesTheNameOfItsConceptBesideItsOwnFields() {
        Schema schema = CatalogLoader.readSchemaJson(DB);

        Column billing = schema.getTables().get(0).getColumns().get(1);
        assertEquals("address", billing.getConceptRef());
        assertNull(billing.getConcept());
        assertNull(schema.getTables().get(0).getColumns().get(0).getConceptRef());
    }

    @Test
    void theOrderOfTheEntriesSurvivesTheWriting(@TempDir Path dir) throws IOException {
        Schema reread =
                CatalogLoader.readSchemaJson(written(CatalogLoader.readSchemaJson(DB), dir));

        assertEquals(
                List.of("street", "geo", "phones"),
                List.copyOf(reread.getConcepts().get("address").getEntries().keySet()));
        assertEquals(
                List.of("address", "position", "latitude", "phone_list"),
                List.copyOf(reread.getConcepts().keySet()));
    }

    @Test
    void theSchemaReadsBackTheSame(@TempDir Path dir) throws IOException {
        Schema original = CatalogLoader.readSchemaJson(DB);

        Schema reread = CatalogLoader.readSchemaJson(written(original, dir));

        assertEquals(
                EVERYTHING.writeValueAsString(original), EVERYTHING.writeValueAsString(reread));
    }

    @Test
    void whatIsNotThereIsNotWritten(@TempDir Path dir) throws IOException {
        String json = written(CatalogLoader.readSchemaJson(DB), dir);

        // the concept "position" has no label, and no unit, quantity or presentation at all
        assertFalse(json.contains("\"quantity\""), json);
        assertFalse(json.contains("\"presentation\""), json);
        assertEquals(1, json.split("\"arraySize\"", -1).length - 1, json);
    }

    @Test
    void aSchemaWithoutConceptsHasNoneAndWritesNone(@TempDir Path dir) throws IOException {
        Schema plain =
                CatalogLoader.readSchemaJson(
                        "{ \"name\": \"s\", \"tables\": [], \"relations\": [] }");

        assertNull(plain.getConcepts());
        assertFalse(written(plain, dir).contains("concepts"));
    }
}
