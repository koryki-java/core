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
 * Parity: a column that refers to a scalar concept has the type of a column that declares the same
 * family and dialect type itself.
 */
class ConceptDescriptorTest {

    private static final TypeDescriptorParser PARSER = new TypeDescriptorParser() {};

    private static Concept concept(String family, String dialectType) {
        Concept concept = new Concept();
        concept.setTypeFamily(family);
        concept.setDialectType(dialectType);
        return concept;
    }

    private static Schema schema(Concept concept) {
        Schema schema = new Schema();
        schema.setConcepts(new LinkedHashMap<>());
        schema.getConcepts().put("k", concept);
        return schema;
    }

    private static Column viaConcept() {
        Column column = new Column("c");
        column.setConcept("k");
        return column;
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "INTEGER, INTEGER",
        "INTEGER, BIGINT",
        "INTEGER, SMALLINT",
        "TEXT, VARCHAR",
        "TEXT, VARCHAR(40)",
        "DECIMAL, 'DECIMAL(10,2)'",
        "FLOAT, DOUBLE",
        "BOOLEAN, BOOLEAN",
        "DATE, DATE",
        "TIME, TIME",
        "TIMESTAMP, TIMESTAMP",
        "UUID, UUID",
        "JSON, JSON",
        "BLOB, BLOB"
    })
    void aScalarConceptTypesLikeTheColumnItself(String family, String dialectType) {
        Column old = new Column("c");
        old.setTypeFamily(family);
        old.setDialectType(dialectType);

        TypeDescriptor expected = PARSER.parse(old);
        TypeDescriptor actual =
                new ConceptResolver(schema(concept(family, dialectType)))
                        .descriptor(viaConcept(), PARSER);

        assertEquals(expected, actual);
        assertEquals(expected.portable(), actual.portable());
    }

    @Test
    void aScalarConceptWithEntriesIsRefused() {
        Concept text = concept("TEXT", "VARCHAR");
        text.setEntries(new LinkedHashMap<>(Map.of("n", "INTEGER")));

        String message =
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new ConceptResolver(schema(text))
                                                .descriptor(viaConcept(), PARSER))
                        .getMessage();

        assertTrue(message.contains("which its family does not take"), message);
    }

    private static Schema schema(Map<String, Concept> concepts) {
        Schema schema = new Schema();
        schema.setConcepts(new LinkedHashMap<>(concepts));
        return schema;
    }

    private static Concept array(String element, Integer size) {
        Concept array = concept("ARRAY", null);
        array.setEntries(new LinkedHashMap<>(Map.of("element", element)));
        array.setArraySize(size);
        return array;
    }

    private static TypeDescriptor spelled(String dialectType, String family) {
        Column old = new Column("c");
        old.setTypeFamily(family);
        old.setDialectType(dialectType);
        return PARSER.parse(old);
    }

    private static TypeDescriptor viaConcept(Schema schema) {
        return new ConceptResolver(schema).descriptor(viaConcept(), PARSER);
    }

    @ParameterizedTest(name = "{0}, size {1}")
    @CsvSource(
            value = {
                "VARCHAR, null, VARCHAR[]",
                "INTEGER, null, INTEGER[]",
                "DOUBLE, 3, DOUBLE[3]",
                "VARCHAR[], null, VARCHAR[][]",
                "'DECIMAL(10,2)', null, 'DECIMAL(10,2)[]'"
            },
            nullValues = "null")
    void anArrayConceptTypesLikeItsSpelling(String element, Integer size, String spelling) {
        assertEquals(spelled(spelling, "ARRAY"), viaConcept(schema(array(element, size))));
    }

    @Test
    void theElementMayBeAScalarConcept() {
        Schema schema =
                schema(
                        Map.of(
                                "k", array("latitude", 3),
                                "latitude", concept("FLOAT", "DOUBLE")));

        assertEquals(spelled("DOUBLE[3]", "ARRAY"), viaConcept(schema));
    }

    @Test
    void theElementMayBeAnArrayConcept() {
        Schema schema =
                schema(
                        Map.of(
                                "k", array("row", null),
                                "row", array("INTEGER", 2)));

        assertEquals(spelled("INTEGER[2][]", "ARRAY"), viaConcept(schema));
    }

    private static Concept struct(String... namesAndValues) {
        Concept struct = concept("STRUCT", null);
        Map<String, String> entries = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            entries.put(namesAndValues[i], namesAndValues[i + 1]);
        }
        struct.setEntries(entries);
        return struct;
    }

    @ParameterizedTest(name = "{1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "street,VARCHAR|STRUCT(street VARCHAR)",
                "street,VARCHAR,no,INTEGER|STRUCT(street VARCHAR, no INTEGER)",
                "no,INTEGER,street,VARCHAR|STRUCT(no INTEGER, street VARCHAR)",
                "tags,VARCHAR[]|STRUCT(tags VARCHAR[])",
                "Odd Name,VARCHAR|STRUCT(\"Odd Name\" VARCHAR)"
            })
    void aStructConceptTypesLikeItsSpelling(String entries, String spelling) {
        String[] parts = entries.split(",");
        assertEquals(spelled(spelling, "STRUCT"), viaConcept(schema(struct(parts))));
    }

    @Test
    void theFieldsMayBeConcepts() {
        Schema schema =
                schema(
                        Map.of(
                                "k", struct("geo", "position", "phones", "phone_list"),
                                "position", struct("lat", "latitude", "lon", "latitude"),
                                "latitude", concept("FLOAT", "DOUBLE"),
                                "phone_list", array("VARCHAR", 3)));

        assertEquals(
                spelled("STRUCT(geo STRUCT(lat DOUBLE, lon DOUBLE), phones VARCHAR[3])", "STRUCT"),
                viaConcept(schema));
    }

    private static Concept map(String key, String value) {
        Concept map = concept("MAP", null);
        map.setEntries(new LinkedHashMap<>(Map.of("key", key, "value", value)));
        return map;
    }

    @ParameterizedTest(name = "{2}")
    @CsvSource({
        "VARCHAR, INTEGER, 'MAP(VARCHAR, INTEGER)'",
        "VARCHAR, VARCHAR[], 'MAP(VARCHAR, VARCHAR[])'",
        "INTEGER, 'STRUCT(a INTEGER)', 'MAP(INTEGER, STRUCT(a INTEGER))'"
    })
    void aMapConceptTypesLikeItsSpelling(String key, String value, String spelling) {
        assertEquals(spelled(spelling, "MAP"), viaConcept(schema(map(key, value))));
    }

    @Test
    void theKeyAndTheValueMayBeConcepts() {
        Schema schema =
                schema(
                        Map.of(
                                "k", map("label", "position"),
                                "label", concept("TEXT", "VARCHAR"),
                                "position", struct("lat", "DOUBLE", "lon", "DOUBLE")));

        assertEquals(
                spelled("MAP(VARCHAR, STRUCT(lat DOUBLE, lon DOUBLE))", "MAP"), viaConcept(schema));
    }

    private static Column member(String name, String family) {
        Column member = new Column(name);
        member.setTypeFamily(family);
        return member;
    }

    private static Column oldJson(Column... fields) {
        Column json = new Column("c");
        json.setTypeFamily("JSON");
        json.setDialectType("JSON");
        json.setFields(java.util.List.of(fields));
        return json;
    }

    private static Concept json(String... namesAndValues) {
        Concept json = concept("JSON", "JSON");
        Map<String, String> entries = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            entries.put(namesAndValues[i], namesAndValues[i + 1]);
        }
        json.setEntries(entries);
        return json;
    }

    @Test
    void aJsonConceptWithoutEntriesIsPlainJson() {
        assertEquals(spelled("JSON", "JSON"), viaConcept(schema(concept("JSON", "JSON"))));
    }

    @Test
    void aJsonConceptShapesLikeAColumnWithFields() {
        Schema schema =
                schema(
                        Map.of(
                                "k", json("n", "count", "color", "VARCHAR", "price", "money"),
                                "count", concept("INTEGER", "BIGINT"),
                                "money", concept("DECIMAL", "DECIMAL(10,2)")));
        Column old =
                oldJson(
                        member("n", "INTEGER"),
                        member("color", "TEXT"),
                        member("price", "DECIMAL"));

        assertEquals(PARSER.parse(old), viaConcept(schema));
    }

    @Test
    void theMembersMayBeContainers() {
        Schema schema =
                schema(
                        Map.of(
                                "k", json("tags", "tag_list", "dims", "size", "flags", "flag_map"),
                                "tag_list", array("VARCHAR", 3),
                                "size", struct("w", "DOUBLE", "h", "DOUBLE"),
                                "flag_map", map("VARCHAR", "BOOLEAN")));

        Column tags = new Column("tags");
        tags.setTypeFamily("ARRAY");
        tags.setElement(member("element", "TEXT"));
        Column dims = new Column("dims");
        dims.setTypeFamily("STRUCT");
        dims.setFields(java.util.List.of(member("w", "FLOAT"), member("h", "FLOAT")));
        Column flags = new Column("flags");
        flags.setTypeFamily("MAP");
        flags.setValue(member("value", "BOOLEAN"));

        assertEquals(PARSER.parse(oldJson(tags, dims, flags)), viaConcept(schema));
    }

    @Test
    void aJsonMemberOfAnotherJsonConceptIsItsShape() {
        Schema schema =
                schema(
                        Map.of(
                                "k", json("inner", "other"),
                                "other", json("n", "INTEGER")));
        Column inner = new Column("inner");
        inner.setTypeFamily("JSON");
        inner.setFields(java.util.List.of(member("n", "INTEGER")));

        assertEquals(PARSER.parse(oldJson(inner)), viaConcept(schema));
    }

    @Test
    void aJsonMemberOfAFamilyJsonCannotHoldIsRefused() {
        Schema schema = schema(Map.of("k", json("d", "DATE")));

        String message =
                assertThrows(IllegalArgumentException.class, () -> viaConcept(schema)).getMessage();

        assertTrue(message.contains("a JSON member can be declared"), message);
        assertTrue(message.contains(".d"), message);
    }

    @Test
    void aColumnWithoutConceptIsRefused() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ConceptResolver(schema(concept("INTEGER", "INTEGER")))
                                .descriptor(new Column("c"), PARSER));
    }
}
