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
package ai.koryki.iql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.SchemaCopy;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import ai.koryki.databases.complex.duckdb.ComplexService;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * The resolver types a column from its concept, its own or the one it refers to. A column has
 * nothing else to type it by.
 */
class LinkResolverConceptTest {

    private static final Model MODEL = CatalogLoader.model(ComplexService.ROOT, Locale.ENGLISH);

    private static final Schema OWN = CatalogLoader.db(ComplexService.DB);

    @Test
    void everyColumnOfTheBedIsTypedFromItsOwnConcept() {
        LinkResolver resolver = new LinkResolver(Locale.ENGLISH, OWN, MODEL, true);

        int columns = 0;
        for (Table table : OWN.getTables()) {
            for (Column column : table.getColumns()) {
                assertNotNull(column.getConcept(), table.getName() + "." + column.getName());
                assertNotNull(
                        resolver.getTypeDescriptor(column),
                        table.getName() + "." + column.getName());
                columns++;
            }
        }
        assertTrue(columns > 20, "columns: " + columns);
        Column nr = OWN.getTables().get(0).getColumns().get(0);
        assertEquals("INTEGER", resolver.getTypeDescriptor(nr).portable());
    }

    /** The bed with the concept of every column moved into the schema, the column naming it. */
    private static Schema referenced() {
        Schema schema = SchemaCopy.deepCopy(OWN);
        schema.setConcepts(new java.util.LinkedHashMap<>());
        for (Table table : schema.getTables()) {
            for (Column column : table.getColumns()) {
                String name = table.getName() + "_" + column.getName() + "_concept";
                schema.getConcepts().put(name, column.getConcept());
                column.setConcept(null);
                column.setConceptRef(name);
            }
        }
        return schema;
    }

    @Test
    void aColumnThatRefersToAConceptIsTypedFromItAsOneWithItsOwn() {
        Schema referenced = referenced();
        LinkResolver own = new LinkResolver(Locale.ENGLISH, OWN, MODEL, true);
        LinkResolver refers = new LinkResolver(Locale.ENGLISH, referenced, MODEL, true);

        int columns = 0;
        for (int t = 0; t < OWN.getTables().size(); t++) {
            for (int c = 0; c < OWN.getTables().get(t).getColumns().size(); c++) {
                Column was = OWN.getTables().get(t).getColumns().get(c);
                Column is = referenced.getTables().get(t).getColumns().get(c);
                assertNotNull(is.getConceptRef());
                assertEquals(
                        own.getTypeDescriptor(was),
                        refers.getTypeDescriptor(is),
                        OWN.getTables().get(t).getName() + "." + was.getName());
                columns++;
            }
        }
        assertTrue(columns > 20, "columns: " + columns);
    }

    @Test
    void aColumnWithoutAConceptIsRefusedWhereTheResolverIsBuilt() {
        Schema schema = SchemaCopy.deepCopy(OWN);
        schema.getTables().get(0).getColumns().get(0).setConcept(null);

        IllegalArgumentException refused =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new LinkResolver(Locale.ENGLISH, schema, MODEL, true));

        assertEquals("column 'nr': it refers to no concept", refused.getMessage());
    }
}
