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
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.ConceptConverter;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import ai.koryki.databases.complex.duckdb.ComplexService;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * The resolver types a column that refers to a concept from the concept. The columns of the
 * converted bed have lost everything they declared themselves, so only the concepts can type them.
 */
class LinkResolverConceptTest {

    private static final Model MODEL = CatalogLoader.model(ComplexService.ROOT, Locale.ENGLISH);

    private static final Schema OLD = CatalogLoader.db(ComplexService.DB);

    /** The bed converted, its nested columns left with nothing but the reference. */
    private static Schema bare() {
        Schema schema = ConceptConverter.convert(OLD);
        for (Table table : schema.getTables()) {
            for (Column column : table.getColumns()) {
                if (column.getConceptRef() != null) {
                    column.setDialectType(null);
                    column.setFields(null);
                    column.setElement(null);
                    column.setKey(null);
                    column.setValue(null);
                    column.setLength(null);
                }
            }
        }
        return schema;
    }

    @Test
    void aColumnWithOnlyAConceptHasTheTypeItHadBefore() {
        Schema bare = bare();
        LinkResolver before = new LinkResolver(Locale.ENGLISH, OLD, MODEL, true);
        LinkResolver after = new LinkResolver(Locale.ENGLISH, bare, MODEL, true);

        int nested = 0;
        for (int t = 0; t < OLD.getTables().size(); t++) {
            for (int c = 0; c < OLD.getTables().get(t).getColumns().size(); c++) {
                Column old = OLD.getTables().get(t).getColumns().get(c);
                Column column = bare.getTables().get(t).getColumns().get(c);
                if (column.getConceptRef() != null) {
                    nested++;
                }
                assertEquals(
                        before.getTypeDescriptor(old),
                        after.getTypeDescriptor(column),
                        OLD.getTables().get(t).getName() + "." + old.getName());
            }
        }
        assertTrue(nested >= 15, "nested columns of the bed: " + nested);
    }

    @Test
    void aColumnWithoutAConceptIsTypedAsBefore() {
        Schema converted = ConceptConverter.convert(OLD);
        LinkResolver resolver = new LinkResolver(Locale.ENGLISH, converted, MODEL, true);
        Column nr = converted.getTables().get(0).getColumns().get(0);

        assertEquals(null, nr.getConceptRef());
        assertEquals("INTEGER", resolver.getTypeDescriptor(nr).portable());
    }
}
