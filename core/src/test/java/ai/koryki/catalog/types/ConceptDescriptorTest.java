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
    void aContainerConceptIsNotTypedYet() {
        Concept array = concept("ARRAY", null);
        array.setEntries(new LinkedHashMap<>(Map.of("element", "VARCHAR")));

        String message =
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new ConceptResolver(schema(array))
                                                .descriptor(viaConcept(), PARSER))
                        .getMessage();

        assertTrue(message.contains("only a scalar concept is typed so far"), message);
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
