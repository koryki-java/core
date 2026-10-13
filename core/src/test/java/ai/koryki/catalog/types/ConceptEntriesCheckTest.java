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
package ai.koryki.catalog.types;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Schema;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** An entry of a concept refers to a concept of the schema or to a type spelling, or to nothing. */
class ConceptEntriesCheckTest {

    private static final TypeDescriptorParser PARSER = new TypeDescriptorParser() {};

    private static Concept withEntries(String... pairs) {
        Concept concept = new Concept();
        Map<String, String> entries = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            entries.put(pairs[i], pairs[i + 1]);
        }
        concept.setEntries(entries);
        return concept;
    }

    private static Schema schema(Object... namesAndConcepts) {
        Map<String, Concept> concepts = new LinkedHashMap<>();
        for (int i = 0; i < namesAndConcepts.length; i += 2) {
            concepts.put((String) namesAndConcepts[i], (Concept) namesAndConcepts[i + 1]);
        }
        Schema schema = new Schema("s");
        schema.setConcepts(concepts);
        return schema;
    }

    @Test
    void entriesThatNameConceptsAreFine() {
        Schema schema =
                schema(
                        "address",
                        withEntries("street", "street", "geo", "position"),
                        "position",
                        withEntries("lat", "latitude", "lon", "latitude"),
                        "latitude",
                        new Concept(),
                        "street",
                        new Concept());

        assertDoesNotThrow(() -> new ConceptResolver(schema).checkEntries(PARSER));
    }

    @Test
    void entriesThatAreTypeSpellingsAreFine() {
        Schema schema =
                schema(
                        "address",
                        withEntries(
                                "street", "VARCHAR",
                                "price", "DECIMAL(10,2)",
                                "vec", "DOUBLE[3]",
                                "geo", "STRUCT(lat DOUBLE, lon DOUBLE)",
                                "tags", "VARCHAR[]"));

        assertDoesNotThrow(() -> new ConceptResolver(schema).checkEntries(PARSER));
    }

    @Test
    void aNameThatIsNoConceptAndNoSpellingIsDangling() {
        Schema schema =
                schema(
                        "address",
                        withEntries("street", "VARCHAR", "geo", "positon"),
                        "position",
                        new Concept());

        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ConceptResolver(schema).checkEntries(PARSER));

        assertEquals(
                "concept 'address': entry 'geo' refers to 'positon', which is neither a concept"
                        + " nor a type spelling; the schema has address, position",
                e.getMessage());
    }

    @Test
    void anEntryWithoutAValueRefersToNothing() {
        Schema schema = schema("address", withEntries("street", null));

        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ConceptResolver(schema).checkEntries(PARSER));

        assertEquals(
                "concept 'address': entry 'street' refers to nothing, which is neither a concept"
                        + " nor a type spelling; the schema has address",
                e.getMessage());
    }

    @Test
    void aBrokenSpellingIsDangling() {
        for (String broken : new String[] {"STRUCT(lat DOUBLE", "DOUBLE[", "", "  "}) {
            Schema schema = schema("address", withEntries("x", broken));

            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ConceptResolver(schema).checkEntries(PARSER),
                    "'" + broken + "'");
        }
    }

    @Test
    void conceptsWithoutEntriesAndSchemasWithoutConceptsHaveNothingToCheck() {
        assertDoesNotThrow(
                () -> new ConceptResolver(schema("a", new Concept())).checkEntries(PARSER));
        assertDoesNotThrow(() -> new ConceptResolver(new Schema("s")).checkEntries(PARSER));
    }
}
