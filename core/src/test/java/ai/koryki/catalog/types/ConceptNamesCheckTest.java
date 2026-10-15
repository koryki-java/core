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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** A concept is not named like a type, or its name would stand in for the type in an entry. */
class ConceptNamesCheckTest {

    private static final TypeDescriptorParser PARSER = new TypeDescriptorParser() {};

    private static Schema schema(String... names) {
        Map<String, Concept> concepts = new LinkedHashMap<>();
        for (String name : names) {
            concepts.put(name, new Concept());
        }
        Schema schema = new Schema("s");
        schema.setConcepts(concepts);
        return schema;
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "varchar", "VARCHAR", "text", "Text", "integer", "double", "decimal",
                "DECIMAL(10,2)", "bigint", "timestamp", "date", "boolean", "struct", "array",
                "map", "json", "uuid", "interval", "float", "blob", "time"
            })
    void aNameThatIsATypeIsRefused(String name) {
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ConceptResolver(schema("fine", name)).checkNames(PARSER),
                        name);

        assertEquals(
                "concept '"
                        + name
                        + "': the name is a type, and an entry that says "
                        + name
                        + " would refer to the concept instead of the type; call it something"
                        + " else",
                e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "address",
                "position",
                "billing_address",
                "latitude",
                "unit_price",
                "money",
                "orders.order_id",
                "check_complex.tags",
                "phone_list",
                "Straße",
                "x1"
            })
    void aNameThatIsNoTypeIsFine(String name) {
        assertDoesNotThrow(() -> new ConceptResolver(schema(name)).checkNames(PARSER), name);
    }

    @Test
    void theFirstOffenderInTheOrderOfTheSchemaIsTheOneReported() {
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new ConceptResolver(schema("address", "text", "integer"))
                                        .checkNames(PARSER));

        assertEquals(
                "concept 'text': the name is a type, and an entry that says text would refer to"
                        + " the concept instead of the type; call it something else",
                e.getMessage());
    }

    @Test
    void aSchemaWithoutConceptsHasNoNamesToCheck() {
        assertDoesNotThrow(() -> new ConceptResolver(new Schema("s")).checkNames(PARSER));
    }
}
