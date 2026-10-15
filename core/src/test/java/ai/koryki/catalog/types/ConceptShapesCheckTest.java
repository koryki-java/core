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

/** The entries of a concept are the ones its family has, and a size belongs to an ARRAY. */
class ConceptShapesCheckTest {

    /** A schema of the one concept {@code c}: its family, size, and entries as {@code a=b c=d}. */
    private static Schema schema(String family, Integer size, String entries) {
        Concept concept = new Concept();
        concept.setTypeFamily(family);
        concept.setArraySize(size);
        if (entries != null) {
            Map<String, String> map = new LinkedHashMap<>();
            if (!entries.isEmpty()) {
                for (String pair : entries.split(" ")) {
                    String[] kv = pair.split("=", -1);
                    map.put(kv[0], kv[1]);
                }
            }
            concept.setEntries(Entries.of(map));
        }
        Map<String, Concept> concepts = new LinkedHashMap<>();
        concepts.put("c", concept);
        Schema schema = new Schema("s");
        schema.setConcepts(concepts);
        return schema;
    }

    private static void fine(String family, Integer size, String entries) {
        assertDoesNotThrow(
                () -> new ConceptResolver(schema(family, size, entries)).checkShapes(),
                family + " " + size + " " + entries);
    }

    private static void wrong(String family, Integer size, String entries, String problem) {
        assertEquals(
                "concept 'c': " + problem,
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new ConceptResolver(schema(family, size, entries))
                                                .checkShapes())
                        .getMessage());
    }

    @Test
    void anArrayHasTheEntryElement() {
        fine("ARRAY", null, "element=VARCHAR");
        fine("ARRAY", 3, "element=position");
        wrong("ARRAY", null, null, "an ARRAY has the entries 'element'; missing: 'element'");
        wrong("ARRAY", null, "", "an ARRAY has the entries 'element'; missing: 'element'");
        wrong(
                "ARRAY",
                null,
                "item=VARCHAR",
                "an ARRAY has the entries 'element'; missing: 'element'; not an entry of it: 'item'");
        wrong(
                "ARRAY",
                null,
                "element=VARCHAR other=VARCHAR",
                "an ARRAY has the entries 'element'; not an entry of it: 'other'");
    }

    @Test
    void aMapHasTheEntriesKeyAndValue() {
        fine("MAP", null, "key=VARCHAR value=INTEGER");
        wrong("MAP", null, "key=VARCHAR", "a MAP has the entries 'key', 'value'; missing: 'value'");
        wrong("MAP", null, "value=INTEGER", "a MAP has the entries 'key', 'value'; missing: 'key'");
        wrong(
                "MAP",
                null,
                "key=VARCHAR value=INTEGER extra=VARCHAR",
                "a MAP has the entries 'key', 'value'; not an entry of it: 'extra'");
    }

    @Test
    void aStructHasAtLeastOneEntryOfAnyName() {
        fine("STRUCT", null, "street=VARCHAR geo=position");
        // the names of ARRAY and MAP are only theirs: a struct may have a member called key
        fine("STRUCT", null, "key=VARCHAR element=VARCHAR value=VARCHAR");
        wrong("STRUCT", null, null, "a STRUCT has at least one entry");
        wrong("STRUCT", null, "", "a STRUCT has at least one entry");
    }

    @Test
    void jsonHasNoEntriesItsContentIsAContainerStoredAsJson() {
        fine("JSON", null, null);
        fine("JSON", null, "");
        wrong(
                "JSON",
                null,
                "n=INTEGER",
                "a JSON has no entries, not 'n'; its content is a STRUCT, ARRAY or MAP with the"
                        + " encoding JSON");
    }

    @Test
    void theEncodingJsonBelongsToAContainer() {
        for (String family : new String[] {"ARRAY", "STRUCT", "MAP"}) {
            Concept concept = new Concept();
            concept.setTypeFamily(family);
            concept.setTypeEncoding("JSON");
            concept.setEntries(
                    Entries.of(
                            family.equals("ARRAY")
                                    ? java.util.Map.of("element", "VARCHAR")
                                    : family.equals("MAP")
                                            ? java.util.Map.of("key", "VARCHAR", "value", "VARCHAR")
                                            : java.util.Map.of("a", "VARCHAR")));
            Schema schema = new Schema();
            schema.setConcepts(new java.util.LinkedHashMap<>(java.util.Map.of("c", concept)));
            new ConceptResolver(schema).checkShapes();
        }
        for (String family : new String[] {"TEXT", "JSON"}) {
            Concept concept = new Concept();
            concept.setTypeFamily(family);
            concept.setTypeEncoding("JSON");
            Schema schema = new Schema();
            schema.setConcepts(new java.util.LinkedHashMap<>(java.util.Map.of("c", concept)));
            assertEquals(
                    "concept 'c': the encoding JSON belongs to an ARRAY, a STRUCT or a MAP, not to"
                            + " a "
                            + family,
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> new ConceptResolver(schema).checkShapes())
                            .getMessage());
        }
    }

    @Test
    void aScalarHasNoEntries() {
        fine("TEXT", null, null);
        fine("INTEGER", null, "");
        wrong("TEXT", null, "a=VARCHAR", "a TEXT has no entries, not 'a'");
        wrong("DECIMAL", null, "a=VARCHAR b=VARCHAR", "a DECIMAL has no entries, not 'a', 'b'");
    }

    @Test
    void aSizeIsAPositiveSizeAndBelongsToAnArray() {
        fine("ARRAY", 1, "element=VARCHAR");
        wrong("ARRAY", 0, "element=VARCHAR", "an arraySize is a positive size, not 0");
        wrong("ARRAY", -2, "element=VARCHAR", "an arraySize is a positive size, not -2");
        wrong("STRUCT", 3, "a=VARCHAR", "an arraySize belongs to an ARRAY, not to a STRUCT");
        wrong("TEXT", 3, null, "an arraySize belongs to an ARRAY, not to a TEXT");
    }

    @Test
    void entriesNeedAFamilyToSayWhatTheyAre() {
        fine(null, null, null);
        fine(null, null, "");
        wrong(null, null, "a=VARCHAR", "it has entries or a size but no typeFamily");
        wrong(null, 3, null, "it has entries or a size but no typeFamily");
    }

    @Test
    void aFamilyThatDoesNotExistIsReported() {
        wrong("BLOB2", null, null, "Unknown TypeFamily: BLOB2");
    }

    @Test
    void aSchemaWithoutConceptsHasNoShapesToCheck() {
        assertDoesNotThrow(() -> new ConceptResolver(new Schema("s")).checkShapes());
    }
}
