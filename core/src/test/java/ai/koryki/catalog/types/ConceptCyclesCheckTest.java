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

import ai.koryki.catalog.Entries;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Schema;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A concept does not contain itself, however far round: a type is a finite tree. */
class ConceptCyclesCheckTest {

    /** {@code name, "entry=value entry=value", name, ...}: a schema in a few words. */
    private static Schema schema(String... namesAndEntries) {
        Map<String, Concept> concepts = new LinkedHashMap<>();
        for (int i = 0; i < namesAndEntries.length; i += 2) {
            Concept concept = new Concept();
            if (!namesAndEntries[i + 1].isEmpty()) {
                Map<String, String> entries = new LinkedHashMap<>();
                for (String pair : namesAndEntries[i + 1].split(" ")) {
                    String[] kv = pair.split("=", -1);
                    entries.put(kv[0], kv[1].isEmpty() ? null : kv[1]);
                }
                concept.setEntries(Entries.of(entries));
            }
            concepts.put(namesAndEntries[i], concept);
        }
        Schema schema = new Schema("s");
        schema.setConcepts(concepts);
        return schema;
    }

    private static String cycle(Schema schema) {
        return assertThrows(
                        IllegalArgumentException.class,
                        () -> new ConceptResolver(schema).checkCycles())
                .getMessage();
    }

    private static final String TAIL = "; a concept cannot contain itself, a type is finite";

    @Test
    void aConceptInsideItselfIsACycle() {
        assertEquals("concept cycle: node -> node" + TAIL, cycle(schema("node", "next=node")));
    }

    @Test
    void twoConceptsInsideEachOtherAreACycle() {
        assertEquals(
                "concept cycle: address -> position -> address" + TAIL,
                cycle(schema("address", "geo=position", "position", "home=address")));
    }

    @Test
    void aLongerWayRoundIsReportedInOrder() {
        assertEquals(
                "concept cycle: a -> b -> c -> a" + TAIL,
                cycle(schema("a", "x=b", "b", "x=c", "c", "x=a")));
    }

    @Test
    void theChainStartsAtTheConceptThatComesRoundNotAtWhereTheWayBegan() {
        // order -> address -> position -> address: order is not in the cycle
        assertEquals(
                "concept cycle: address -> position -> address" + TAIL,
                cycle(
                        schema(
                                "order",
                                "ship=address",
                                "address",
                                "geo=position",
                                "position",
                                "home=address")));
    }

    @Test
    void aConceptUsedTwiceIsSharedAndNoCycle() {
        // a diamond: a -> b -> d, a -> c -> d; and a concept used twice by one concept
        assertDoesNotThrow(
                () ->
                        new ConceptResolver(
                                        schema(
                                                "a", "left=b right=c",
                                                "b", "x=d",
                                                "c", "x=d",
                                                "d", "",
                                                "pair", "first=d second=d"))
                                .checkCycles());
    }

    @Test
    void spellingsAndMissingValuesAreNotFollowed() {
        assertDoesNotThrow(
                () ->
                        new ConceptResolver(schema("a", "x=VARCHAR y=DOUBLE[3] z=", "b", "p=a"))
                                .checkCycles());
    }

    @Test
    void aSchemaWithoutConceptsOrWithoutEntriesHasNoCycle() {
        assertDoesNotThrow(() -> new ConceptResolver(new Schema("s")).checkCycles());
        assertDoesNotThrow(() -> new ConceptResolver(schema("a", "", "b", "")).checkCycles());
    }
}
