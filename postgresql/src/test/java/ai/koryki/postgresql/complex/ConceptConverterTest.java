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

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.ConceptConverter;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import ai.koryki.catalog.types.ConceptResolver;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeDescriptorParser;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The converter on the PostgreSQL complex bed: composite types as structs, arrays, and jsonb. No
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

    /** JSON that is a list or a map as a whole waits until a JSON concept can say it. */
    @Test
    void onlyJsonShapedAsAWholeIsLeftWithoutAConcept() {
        TreeSet<String> left = new TreeSet<>();
        for (Table table : NEW.getTables()) {
            for (Column column : table.getColumns()) {
                boolean nested =
                        column.getFields() != null
                                || column.getElement() != null
                                || column.getKey() != null
                                || column.getValue() != null;
                if (nested && column.getConcept() == null) {
                    left.add(table.getName() + "." + column.getName());
                }
            }
        }
        assertEquals(
                new TreeSet<>(
                        List.of(
                                "check_complex.attrs",
                                "check_complex.counts",
                                "check_complex.deep",
                                "check_complex.lookup")),
                left);
    }

    @Test
    void theRowSpellingsStayWhereTheyAre() {
        Column dims =
                NEW.getTables().get(0).getColumns().stream()
                        .filter(c -> c.getName().equals("dims"))
                        .findFirst()
                        .orElseThrow();
        assertEquals("check_complex_dims", dims.getConcept());
        assertEquals("ROW(w DOUBLE PRECISION, h DOUBLE PRECISION)", dims.getDialectType());
    }
}
