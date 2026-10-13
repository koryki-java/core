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
package ai.koryki.mariadb.complex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.ConceptConverter;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import ai.koryki.catalog.types.ConceptResolver;
import ai.koryki.catalog.types.TypeDescriptorParser;
import org.junit.jupiter.api.Test;

/** The converter on the MariaDB complex bed: one JSON document. No connection is needed. */
class ConceptConverterTest {

    private static final TypeDescriptorParser PARSER = new TypeDescriptorParser() {};

    private static final Schema OLD = CatalogLoader.db(ComplexCatalogTest.DB);
    private static final Schema NEW = ConceptConverter.convert(OLD);

    private static Column column(Schema schema, String name) {
        return schema.getTables().get(0).getColumns().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void everyConvertedColumnTypesAsItDidBefore() {
        ConceptResolver resolver = new ConceptResolver(NEW);
        int converted = 0;
        for (int t = 0; t < OLD.getTables().size(); t++) {
            Table before = OLD.getTables().get(t);
            for (int c = 0; c < before.getColumns().size(); c++) {
                Column column = NEW.getTables().get(t).getColumns().get(c);
                if (column.getConcept() != null) {
                    converted++;
                    assertEquals(
                            PARSER.parse(before.getColumns().get(c)),
                            resolver.descriptor(column, PARSER),
                            before.getName() + "." + column.getName());
                }
            }
        }
        assertEquals(
                OLD.getTables().stream().mapToInt(t -> t.getColumns().size()).sum(), converted);
    }

    @Test
    void theConvertedSchemaPassesTheChecks() {
        new ConceptResolver(NEW).check(PARSER);
    }

    @Test
    void theDocumentIsAStructStoredAsJson() {
        Concept doc = column(NEW, "doc").getConcept();
        assertEquals("STRUCT", doc.getTypeFamily());
        assertEquals("JSON", doc.getTypeEncoding());
        assertEquals("JSON", doc.getDialectType());
        assertEquals(
                "JSON(STRUCT(n:INTEGER,a:STRUCT(b:ARRAY(INTEGER)),s:TEXT,b:BOOLEAN,d:DECIMAL))",
                new ConceptResolver(NEW).descriptor(column(NEW, "doc"), PARSER).portable());
    }

    @Test
    void aScalarColumnHasItsOwnConcept() {
        assertEquals("INTEGER", column(NEW, "nr").getConcept().getTypeFamily());
        assertNull(column(NEW, "nr").getConceptRef());
    }
}
