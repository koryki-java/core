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
package ai.koryki.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import ai.koryki.catalog.types.ConceptResolver;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeDescriptorParser;
import ai.koryki.catalog.types.TypeTree;
import ai.koryki.databases.complex.duckdb.ComplexService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The converter on the DuckDB complex bed: every column types as it did before. */
class ConceptConverterTest {

    private static final TypeDescriptorParser PARSER = new TypeDescriptorParser() {};

    private static final Schema OLD = CatalogLoader.db(ComplexService.DB);
    private static final Schema NEW = ConceptConverter.convert(OLD);

    @Test
    void everyColumnTypesAsItDidBefore() {
        ConceptResolver resolver = new ConceptResolver(NEW);
        int converted = 0;
        for (int t = 0; t < OLD.getTables().size(); t++) {
            Table before = OLD.getTables().get(t);
            Table after = NEW.getTables().get(t);
            for (int c = 0; c < before.getColumns().size(); c++) {
                Column old = before.getColumns().get(c);
                Column column = after.getColumns().get(c);
                if (column.getConceptRef() == null) {
                    continue;
                }
                converted++;
                String where = before.getName() + "." + old.getName();
                TypeDescriptor expected = PARSER.parse(old);
                assertEquals(expected, resolver.descriptor(column, PARSER), where);
                assertEquals(
                        expected.portable(), resolver.descriptor(column, PARSER).portable(), where);
            }
        }
        assertTrue(converted >= 15, "the nested columns of the bed: " + converted);
    }

    @Test
    void theConvertedSchemaPassesTheChecks() {
        new ConceptResolver(NEW).check(PARSER);
    }

    @Test
    void aScalarColumnIsLeftAlone() {
        Column nr = NEW.getTables().get(0).getColumns().get(0);
        assertEquals("nr", nr.getName());
        assertNull(nr.getConceptRef());
    }

    @Test
    void theOldFieldsStayBesideTheReference() {
        Column dims = column("check_complex", "dims");
        assertEquals("check_complex_dims", dims.getConceptRef());
        assertNotNull(dims.getFields());
        assertEquals("STRUCT(w DOUBLE, h DOUBLE)", dims.getDialectType());
    }

    @Test
    void theNodesBecomeConceptsNamedByTheirPath() {
        assertEquals("Width", NEW.getConcepts().get("check_complex_dims_w").getLabel());
        assertEquals("m", NEW.getConcepts().get("check_complex_dims_w").getUnit());
        assertEquals(
                "check_complex_address_geo",
                NEW.getConcepts().get("check_complex_address").getEntries().get("geo"));
        assertEquals(
                "check_complex_address_street",
                NEW.getConcepts().get("check_complex_address").getEntries().get("street"));
        assertEquals(
                "VARCHAR", NEW.getConcepts().get("check_complex_attrs").getEntries().get("key"));
        assertEquals(
                Integer.valueOf(2), NEW.getConcepts().get("check_complex_matrix").getArraySize());
    }

    @Test
    void noLabelUnitOrQuantityOfANodeIsLost() {
        java.util.Set<String> said = new java.util.TreeSet<>();
        for (Table table : OLD.getTables()) {
            for (Column column : table.getColumns()) {
                if (TypeTree.hasTree(column)) {
                    collect(column, said);
                }
            }
        }
        java.util.Set<String> kept = new java.util.TreeSet<>();
        NEW.getConcepts()
                .values()
                .forEach(
                        c -> {
                            kept.add("label=" + c.getLabel());
                            kept.add("unit=" + c.getUnit());
                            kept.add("quantity=" + c.getQuantity());
                            kept.add("description=" + c.getDescription());
                        });
        said.removeAll(kept);
        assertEquals(java.util.Set.of(), said);
    }

    private static void collect(Column node, java.util.Set<String> said) {
        if (node.getLabel() != null) said.add("label=" + node.getLabel());
        if (node.getUnit() != null) said.add("unit=" + node.getUnit());
        if (node.getQuantity() != null) said.add("quantity=" + node.getQuantity());
        if (node.getDescription() != null) said.add("description=" + node.getDescription());
        for (Column child :
                java.util.stream.Stream.of(node.getElement(), node.getKey(), node.getValue())
                        .filter(java.util.Objects::nonNull)
                        .toList()) {
            collect(child, said);
        }
        if (node.getFields() != null) {
            node.getFields().forEach(f -> collect(f, said));
        }
    }

    @Test
    void aLeafWithAnEncodingKeepsItAsAConcept() {
        Schema old =
                CatalogLoader.readSchemaJson(
                        """
                        { "name": "d", "relations": [], "tables": [ { "name": "t", "columns": [
                          { "name": "events", "typeFamily": "ARRAY",
                            "dialectType": "STRUCT(ts BIGINT, day DATE)[]",
                            "element": { "typeFamily": "STRUCT", "fields": [
                              { "name": "ts", "typeFamily": "TIMESTAMP",
                                "typeEncoding": "EPOCH:SECONDS", "dialectType": "BIGINT" },
                              { "name": "day", "typeFamily": "DATE", "dialectType": "DATE" } ] } }
                        ] } ] }
                        """);
        Schema converted = ConceptConverter.convert(old);

        TypeDescriptor expected = PARSER.parse(old.getTables().get(0).getColumns().get(0));
        TypeDescriptor actual =
                new ConceptResolver(converted)
                        .descriptor(converted.getTables().get(0).getColumns().get(0), PARSER);
        assertEquals(expected, actual);
        assertEquals(
                "EPOCH:SECONDS",
                converted.getConcepts().get("t_events_element_ts").getTypeEncoding());
        assertEquals(
                "DATE", converted.getConcepts().get("t_events_element").getEntries().get("day"));
    }

    @ParameterizedTest
    @CsvSource({"JSON", "JSONB", "VARCHAR", "TEXT", "VARIANT"})
    void jsonInAnyPhysicalTypeBecomesAContainerWithTheEncodingJson(String dialectType) {
        Schema old =
                CatalogLoader.readSchemaJson(
                        """
                        { "name": "d", "relations": [], "tables": [ { "name": "t", "columns": [
                          { "name": "doc", "typeFamily": "JSON", "dialectType": "%s",
                            "fields": [ { "name": "n", "typeFamily": "INTEGER" } ] } ] } ] }
                        """
                                .formatted(dialectType));
        Schema converted = ConceptConverter.convert(old);
        Column column = converted.getTables().get(0).getColumns().get(0);

        assertEquals("t_doc", column.getConceptRef());
        assertEquals("STRUCT", converted.getConcepts().get("t_doc").getTypeFamily());
        assertEquals("JSON", converted.getConcepts().get("t_doc").getTypeEncoding());
        assertEquals(dialectType, converted.getConcepts().get("t_doc").getDialectType());
        assertEquals(
                PARSER.parse(old.getTables().get(0).getColumns().get(0)),
                new ConceptResolver(converted).descriptor(column, PARSER));
    }

    @Test
    void theOriginalSchemaIsNotChanged() {
        assertNull(column(OLD, "check_complex", "dims").getConceptRef());
        assertTrue(OLD.getConcepts() == null || OLD.getConcepts().isEmpty());
    }

    private static Column column(String table, String name) {
        return column(NEW, table, name);
    }

    private static Column column(Schema schema, String table, String name) {
        return schema.getTables().stream()
                .filter(t -> t.getName().equals(table))
                .flatMap(t -> t.getColumns().stream())
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
