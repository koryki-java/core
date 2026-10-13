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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Schema;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The concept of a column, found by name, and what is said when there is none. */
class ConceptResolverTest {

    private static final Concept ADDRESS = new Concept();
    private static final Concept POSITION = new Concept();

    private static Schema schema() {
        Map<String, Concept> concepts = new LinkedHashMap<>();
        concepts.put("address", ADDRESS);
        concepts.put("position", POSITION);
        Schema schema = new Schema("s");
        schema.setConcepts(concepts);
        return schema;
    }

    private static Column column(String name, String concept) {
        Column column = new Column(name);
        column.setConcept(concept);
        return column;
    }

    @Test
    void aColumnFindsTheConceptItRefersTo() {
        ConceptResolver resolver = new ConceptResolver(schema());

        assertSame(ADDRESS, resolver.concept(column("billing", "address")));
        assertSame(POSITION, resolver.concept(column("where", "position")));
    }

    @Test
    void aNameFindsItsConcept() {
        assertSame(POSITION, new ConceptResolver(schema()).concept("position"));
    }

    @Test
    void aColumnWithoutAConceptIsSaidSo() {
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ConceptResolver(schema()).concept(column("billing", null)));

        assertEquals("column 'billing': it refers to no concept", e.getMessage());
    }

    @Test
    void aNameTheSchemaDoesNotHaveIsReportedWithTheColumnAndTheNamesItHas() {
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ConceptResolver(schema()).concept(column("billing", "adress")));

        assertEquals(
                "column 'billing': no concept 'adress'; the schema has address, position",
                e.getMessage());
    }

    @Test
    void aNameWithoutAColumnIsReportedWithoutOne() {
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ConceptResolver(schema()).concept("nope"));

        assertEquals("no concept 'nope'; the schema has address, position", e.getMessage());
    }

    @Test
    void aSchemaWithoutConceptsHasNoneToOffer() {
        IllegalArgumentException none =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ConceptResolver(new Schema("s")).concept("address"));
        Schema empty = new Schema("s");
        empty.setConcepts(new LinkedHashMap<>());
        IllegalArgumentException emptyMap =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ConceptResolver(empty).concept("address"));

        assertTrue(none.getMessage().endsWith("the schema has none"), none.getMessage());
        assertTrue(emptyMap.getMessage().endsWith("the schema has none"), emptyMap.getMessage());
    }
}
