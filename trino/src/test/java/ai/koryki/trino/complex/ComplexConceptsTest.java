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
package ai.koryki.trino.complex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import ai.koryki.catalog.types.ConceptResolver;
import ai.koryki.catalog.types.TypeDescriptorParser;
import org.junit.jupiter.api.Test;

/**
 * The concepts of the Trino complex bed: row types, arrays, native maps, and a JSON document. No
 * connection is needed.
 */
class ComplexConceptsTest {

    private static final String DB = "/ai/koryki/trino/databases/complex";

    private static final TypeDescriptorParser PARSER = new TypeDescriptorParser() {};

    private static final Schema OLD = CatalogLoader.db(DB);
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

    @Test
    void everyColumnHasAConcept() {
        for (Table table : NEW.getTables()) {
            for (Column column : table.getColumns()) {
                assertNotNull(column.getConcept(), table.getName() + "." + column.getName());
            }
        }
    }

    /** Trino has maps: a MAP concept is native, only the document is stored as JSON. */
    @Test
    void aNativeMapHasNoEncodingAndTheDocumentIsJson() {
        Concept attrs = column("attrs").getConcept();
        assertEquals("MAP", attrs.getTypeFamily());
        assertNull(attrs.getTypeEncoding());
        assertEquals(
                "MAP(TEXT,TEXT)",
                new ConceptResolver(NEW).descriptor(column("attrs"), PARSER).portable());

        Concept doc = column("doc").getConcept();
        assertEquals("STRUCT", doc.getTypeFamily());
        assertEquals("JSON", doc.getTypeEncoding());
    }

    @Test
    void theRowSpellingsStayWhereTheyAre() {
        assertNotNull(column("dims").getConcept());
        assertEquals("ROW(w DOUBLE, h DOUBLE)", column("dims").getConcept().getDialectType());
    }
}
