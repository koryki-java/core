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
package ai.koryki.oracle.complex;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.ConceptConverter;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import ai.koryki.catalog.types.ConceptResolver;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeDescriptorParser;
import org.junit.jupiter.api.Test;

/**
 * The converter on the Oracle complex bed: composite types as structs, arrays, and JSON. No
 * connection is needed.
 */
class ConceptConverterTest {

    private static final TypeDescriptorParser PARSER = new TypeDescriptorParser() {};

    private static final Schema OLD = CatalogLoader.db(ComplexCatalogTest.DB);
    private static final Schema NEW = ConceptConverter.convert(OLD);

    @Test
    void everyConvertedColumnTypesAsItDidBefore() {
        ConceptResolver resolver = new ConceptResolver(NEW);
        for (int t = 0; t < OLD.getTables().size(); t++) {
            Table before = OLD.getTables().get(t);
            for (int c = 0; c < before.getColumns().size(); c++) {
                Column old = before.getColumns().get(c);
                Column column = NEW.getTables().get(t).getColumns().get(c);
                if (column.getConcept() != null) {
                    String where = before.getName() + "." + old.getName();
                    TypeDescriptor expected = PARSER.parse(old);
                    assertEquals(expected, resolver.descriptor(column, PARSER), where);
                }
            }
        }
    }

    @Test
    void theConvertedSchemaPassesTheChecks() {
        new ConceptResolver(NEW).check(PARSER);
    }

    /**
     * Maps and lists stored as JSON convert too: their concept is the container, stored as JSON.
     */
    @Test
    void everyNestedColumnHasAConcept() {
        for (Table table : NEW.getTables()) {
            for (Column column : table.getColumns()) {
                boolean nested =
                        column.getFields() != null
                                || column.getElement() != null
                                || column.getKey() != null
                                || column.getValue() != null;
                assertEquals(
                        nested,
                        column.getConcept() != null,
                        table.getName() + "." + column.getName());
            }
        }
    }

    @Test
    void aMapStoredAsJsonIsAMapConceptWithTheEncodingJson() {
        Concept attrs = NEW.getConcepts().get("check_complex_attrs");
        assertEquals("MAP", attrs.getTypeFamily());
        assertEquals("JSON", attrs.getDialectType());
        assertEquals("JSON", attrs.getTypeEncoding());
        assertEquals("VARCHAR", attrs.getEntries().get("key"));
        TypeDescriptor type =
                new ConceptResolver(NEW)
                        .descriptor(
                                NEW.getTables().get(0).getColumns().stream()
                                        .filter(c -> c.getName().equals("attrs"))
                                        .findFirst()
                                        .orElseThrow(),
                                PARSER);
        assertEquals("JSON(MAP(TEXT,TEXT))", type.portable());
    }

    @Test
    void theRowSpellingsStayWhereTheyAre() {
        Column dims =
                NEW.getTables().get(0).getColumns().stream()
                        .filter(c -> c.getName().equals("dims"))
                        .findFirst()
                        .orElseThrow();
        assertEquals("check_complex_dims", dims.getConcept());
        assertEquals("ROW(w BINARY_DOUBLE, h BINARY_DOUBLE)", dims.getDialectType());
    }
}
