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
package ai.koryki.postgresql.complex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import ai.koryki.catalog.types.ConceptResolver;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeDescriptorParser;
import org.junit.jupiter.api.Test;

/**
 * The concepts of the PostgreSQL complex bed: composite types as structs, arrays, and jsonb. No
 * connection is needed.
 */
class ComplexConceptsTest {

    private static final TypeDescriptorParser PARSER = new TypeDescriptorParser() {};

    private static final Schema OLD = CatalogLoader.db(ComplexCatalogTest.DB);
    private static final Schema NEW = OLD;

    private static Column column(String name) {
        return NEW.getTables().get(0).getColumns().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void theConvertedSchemaPassesTheChecks() {
        new ConceptResolver(NEW).check(PARSER);
    }

    /**
     * Maps and lists stored as jsonb convert too: their concept is the container, stored as JSON.
     */
    @Test
    void everyColumnHasAConcept() {
        for (Table table : NEW.getTables()) {
            for (Column column : table.getColumns()) {
                assertNotNull(column.getConcept(), table.getName() + "." + column.getName());
            }
        }
    }

    @Test
    void aMapStoredAsJsonbIsAMapConceptWithTheEncodingJson() {
        Concept attrs = column("attrs").getConcept();
        assertEquals("MAP", attrs.getTypeFamily());
        assertEquals("JSONB", attrs.getDialectType());
        assertEquals("JSON", attrs.getTypeEncoding());
        assertEquals("VARCHAR", attrs.getEntries().get("key").getReference());
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
        assertNotNull(dims.getConcept());
        assertEquals(
                "ROW(w DOUBLE PRECISION, h DOUBLE PRECISION)", dims.getConcept().getDialectType());
    }
}
