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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Schema;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A container that says its dialect type as a spelling has to say what its concept says; the
 * column's spelling and the concept's own spelling are both held to it.
 */
class ConceptSpellingAgreementTest {

    private static final TypeDescriptorParser PARSER = new TypeDescriptorParser() {};

    private static Concept container(
            String family, String dialectType, Integer size, String... kv) {
        Concept concept = new Concept();
        concept.setTypeFamily(family);
        concept.setDialectType(dialectType);
        concept.setArraySize(size);
        Map<String, String> entries = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            entries.put(kv[i], kv[i + 1]);
        }
        concept.setEntries(entries);
        return concept;
    }

    private static TypeDescriptor type(Concept concept, String columnSpelling) {
        Schema schema = new Schema();
        schema.setConcepts(new LinkedHashMap<>(Map.of("k", concept)));
        Column column = new Column("c");
        column.setConceptRef("k");
        column.setDialectType(columnSpelling);
        return new ConceptResolver(schema).descriptor(column, PARSER);
    }

    private static String refused(Concept concept, String columnSpelling) {
        return assertThrows(IllegalArgumentException.class, () -> type(concept, columnSpelling))
                .getMessage();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "DOUBLE[3]",
                "double[3]",
                "DOUBLE [ 3 ]",
            })
    void anArraySpellingThatAgreesIsAccepted(String spelling) {
        type(container("ARRAY", null, 3, "element", "DOUBLE"), spelling);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "STRUCT(street VARCHAR, no INTEGER)",
                "struct(STREET varchar, No integer)",
                "STRUCT( street VARCHAR ,no INTEGER )"
            })
    void aStructSpellingThatAgreesIsAccepted(String spelling) {
        type(container("STRUCT", null, null, "street", "VARCHAR", "no", "INTEGER"), spelling);
    }

    @Test
    void aMapSpellingThatAgreesIsAccepted() {
        type(
                container("MAP", null, null, "key", "VARCHAR", "value", "INTEGER"),
                "MAP(VARCHAR, INTEGER)");
    }

    @Test
    void noSpellingAndAFlatNameAreNotCompared() {
        Concept array = container("ARRAY", null, null, "element", "VARCHAR");
        assertEquals(type(array, null), type(array, "JSON"));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "ARRAY|VARCHAR[]|DOUBLE[]",
                "ARRAY|VARCHAR[]|VARCHAR[3]",
                "ARRAY|VARCHAR[3]|VARCHAR[]",
                "ARRAY|VARCHAR[]|MAP(VARCHAR, VARCHAR)",
                "STRUCT|street VARCHAR|STRUCT(road VARCHAR)",
                "STRUCT|street VARCHAR|STRUCT(street VARCHAR, no INTEGER)",
                "STRUCT|street VARCHAR|STRUCT(street INTEGER)",
                "STRUCT|street VARCHAR|VARCHAR[]",
                "MAP|key VARCHAR,value INTEGER|MAP(VARCHAR, VARCHAR)",
                "MAP|key VARCHAR,value INTEGER|MAP(INTEGER, INTEGER)"
            })
    void aSpellingThatDisagreesIsRefused(String family, String entries, String spelling) {
        String[] kv = entriesOf(family, entries);
        Integer size = entries.equals("VARCHAR[3]") ? 3 : null;
        String message = refused(container(family, null, size, kv), spelling);

        assertTrue(message.startsWith("column 'c': its concept describes "), message);
        assertTrue(message.contains("dialectType '" + spelling + "' spells "), message);
    }

    /** {@code ARRAY|VARCHAR[3]} says the element and the size in one cell. */
    private static String[] entriesOf(String family, String entries) {
        if (family.equals("ARRAY")) {
            return new String[] {"element", entries.replaceAll("\\[\\d*\\]", "")};
        }
        if (family.equals("MAP")) {
            String[] parts = entries.split(",");
            return new String[] {"key", parts[0].split(" ")[1], "value", parts[1].split(" ")[1]};
        }
        return entries.contains(",")
                ? entries.split("[ ,]")
                : new String[] {entries.split(" ")[0], entries.split(" ")[1]};
    }

    @Test
    void theConceptsOwnSpellingIsHeldToItToo() {
        String message =
                refused(container("ARRAY", "VARCHAR[3]", null, "element", "VARCHAR"), null);

        assertTrue(
                message.contains(
                        "describes ARRAY(TEXT) but dialectType 'VARCHAR[3]' spells ARRAY(TEXT,3)"),
                message);
    }

    @Test
    void anEntryOfAnotherConceptIsHeldToItsOwnSpelling() {
        Concept inner = container("ARRAY", "VARCHAR[3]", null, "element", "VARCHAR");
        Schema schema = new Schema();
        schema.setConcepts(
                new LinkedHashMap<>(
                        Map.of(
                                "k",
                                container("STRUCT", null, null, "tags", "inner"),
                                "inner",
                                inner)));
        Column column = new Column("c");
        column.setConceptRef("k");

        String message =
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new ConceptResolver(schema).descriptor(column, PARSER))
                        .getMessage();

        assertTrue(message.contains("concept 'inner'"), message);
    }
}
