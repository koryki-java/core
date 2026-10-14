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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import ai.koryki.catalog.types.ConceptResolver;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeDescriptorParser;
import ai.koryki.catalog.types.TypeTree;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The converter on the DuckDB complex bed: every column types as it did before. */
class ConceptConverterTest {

    private static final TypeDescriptorParser PARSER = new TypeDescriptorParser() {};

    private static final Schema OLD = LegacyCatalog.duckdbComplex();
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
                assertNull(column.getConceptRef());
                if (column.getConcept() == null) {
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
        int columns = OLD.getTables().stream().mapToInt(t -> t.getColumns().size()).sum();
        assertEquals(columns, converted, "every column of the bed has a concept of its own");
    }

    @Test
    void theConvertedSchemaPassesTheChecks() {
        new ConceptResolver(NEW).check(PARSER);
    }

    @Test
    void aScalarColumnHasTheConceptItDeclares() {
        Column nr = NEW.getTables().get(0).getColumns().get(0);
        assertEquals("nr", nr.getName());
        assertEquals("Nr", nr.getConcept().getLabel());
        assertEquals("Row identifier (primary key)", nr.getConcept().getDescription());
        assertEquals("INTEGER", nr.getConcept().getTypeFamily());
        assertEquals("SMALLINT", nr.getConcept().getDialectType());
        assertNull(nr.getConceptRef());
    }

    @Test
    void theOldFieldsStayBesideTheConcept() {
        Column dims = column("check_complex", "dims");
        assertEquals("STRUCT", dims.getConcept().getTypeFamily());
        assertNull(dims.getConceptRef());
        assertNotNull(dims.getFields());
        assertEquals("STRUCT(w DOUBLE, h DOUBLE)", dims.getDialectType());
    }

    @Test
    void theNodesAreConceptsOfTheirOwnInTheEntriesAndNothingIsNamed() {
        Concept width =
                column("check_complex", "dims").getConcept().getEntries().get("w").getConcept();
        assertEquals("Width", width.getLabel());
        assertEquals("m", width.getUnit());
        Concept address = column("check_complex", "address").getConcept();
        assertTrue(address.getEntries().get("geo").isInline());
        assertEquals("Position", address.getEntries().get("geo").getConcept().getLabel());
        assertTrue(address.getEntries().get("street").isInline());
        assertEquals(
                "VARCHAR",
                column("check_complex", "attrs")
                        .getConcept()
                        .getEntries()
                        .get("key")
                        .getReference());
        assertTrue(NEW.getConcepts() == null || NEW.getConcepts().isEmpty());
        assertEquals(
                Integer.valueOf(2), column("check_complex", "matrix").getConcept().getArraySize());
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
        java.util.List<Concept> all = new java.util.ArrayList<>();
        NEW.getTables()
                .forEach(
                        t ->
                                t.getColumns()
                                        .forEach(
                                                c -> {
                                                    if (c.getConcept() != null) {
                                                        everyConcept(c.getConcept(), all);
                                                    }
                                                }));
        all.forEach(
                c -> {
                    kept.add("label=" + c.getLabel());
                    kept.add("unit=" + c.getUnit());
                    kept.add("quantity=" + c.getQuantity());
                    kept.add("description=" + c.getDescription());
                });
        said.removeAll(kept);
        assertEquals(java.util.Set.of(), said);
    }

    /** The concept and every concept of its own inside it. */
    private static void everyConcept(Concept concept, java.util.List<Concept> all) {
        all.add(concept);
        if (concept.getEntries() != null) {
            concept.getEntries()
                    .values()
                    .forEach(
                            entry -> {
                                if (entry.isInline()) {
                                    everyConcept(entry.getConcept(), all);
                                }
                            });
        }
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
        Concept element =
                converted
                        .getTables()
                        .get(0)
                        .getColumns()
                        .get(0)
                        .getConcept()
                        .getEntries()
                        .get("element")
                        .getConcept();
        assertEquals(
                "EPOCH:SECONDS", element.getEntries().get("ts").getConcept().getTypeEncoding());
        assertEquals("DATE", element.getEntries().get("day").getReference());
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

        assertNull(column.getConceptRef());
        assertEquals("STRUCT", column.getConcept().getTypeFamily());
        assertEquals("JSON", column.getConcept().getTypeEncoding());
        assertEquals(dialectType, column.getConcept().getDialectType());
        assertEquals(
                PARSER.parse(old.getTables().get(0).getColumns().get(0)),
                new ConceptResolver(converted).descriptor(column, PARSER));
    }

    @Test
    void aScalarColumnKeepsEverythingItSays() {
        Schema converted =
                ConceptConverter.convert(
                        CatalogLoader.readSchemaJson(
                                """
                                { "name": "d", "relations": [], "tables": [ { "name": "t", "columns": [
                                  { "name": "price", "label": "Price", "comment": "net",
                                    "description": "What it costs", "typeFamily": "DECIMAL",
                                    "dialectType": "DECIMAL(10,2)", "unit": "EUR",
                                    "quantity": "money", "presentation": "DECIMALS:2",
                                    "nullable": true } ] } ] }
                                """));
        Column price = converted.getTables().get(0).getColumns().get(0);
        Concept concept = price.getConcept();

        assertEquals("Price", concept.getLabel());
        assertEquals("net", concept.getComment());
        assertEquals("What it costs", concept.getDescription());
        assertEquals("EUR", concept.getUnit());
        assertEquals("money", concept.getQuantity());
        assertEquals("DECIMALS:2", concept.getPresentation());
        assertEquals("DECIMAL", concept.getTypeFamily());
        assertEquals("DECIMAL(10,2)", concept.getDialectType());
        // what belongs to the column in the table stays on it
        assertEquals(Boolean.TRUE, price.getNullable());
    }

    @Test
    void aColumnThatHasAConceptAlreadyIsLeftAsItIs() {
        Schema old =
                CatalogLoader.readSchemaJson(
                        """
                        { "name": "d", "relations": [], "tables": [ { "name": "t", "columns": [
                          { "name": "a", "typeFamily": "INTEGER", "dialectType": "INTEGER",
                            "conceptRef": "count" },
                          { "name": "b", "typeFamily": "INTEGER", "dialectType": "INTEGER",
                            "concept": { "label": "Mine", "typeFamily": "INTEGER",
                                         "dialectType": "BIGINT" } } ] } ],
                          "concepts": { "count": { "typeFamily": "INTEGER",
                                                   "dialectType": "INTEGER" } } }
                        """);

        Schema converted = ConceptConverter.convert(old);
        Column a = converted.getTables().get(0).getColumns().get(0);
        Column b = converted.getTables().get(0).getColumns().get(1);

        assertEquals("count", a.getConceptRef());
        assertNull(a.getConcept());
        assertNull(b.getConceptRef());
        assertEquals("Mine", b.getConcept().getLabel());
        assertEquals("BIGINT", b.getConcept().getDialectType());
    }

    @Test
    void aContainerThatIsOnlyASpellingHasNoConceptYet() {
        Schema converted =
                ConceptConverter.convert(
                        CatalogLoader.readSchemaJson(
                                """
                                { "name": "d", "relations": [], "tables": [ { "name": "t", "columns": [
                                  { "name": "tags", "typeFamily": "ARRAY",
                                    "dialectType": "VARCHAR[]" } ] } ] }
                                """));

        assertNull(converted.getTables().get(0).getColumns().get(0).getConcept());
    }

    @Test
    void theConceptOfAColumnIsTheOneItHasOrTheOneItsOldFieldsDescribe() {
        for (int t = 0; t < OLD.getTables().size(); t++) {
            for (int c = 0; c < OLD.getTables().get(t).getColumns().size(); c++) {
                Column old = OLD.getTables().get(t).getColumns().get(c);
                Column converted = NEW.getTables().get(t).getColumns().get(c);
                String where = OLD.getTables().get(t).getName() + "." + old.getName();

                Concept described = ConceptConverter.of(old);
                assertEquals(converted.getConcept().getLabel(), described.getLabel(), where);
                assertEquals(
                        converted.getConcept().getTypeFamily(), described.getTypeFamily(), where);
                assertEquals(
                        converted.getConcept().getDialectType(), described.getDialectType(), where);
                assertSame(converted.getConcept(), ConceptConverter.of(converted), where);
            }
        }
    }

    @Test
    void aColumnThatReferstoAConceptOrSaysNoTypeHasNoConceptOfItsOwn() {
        Column refers = new Column("a");
        refers.setConceptRef("count");
        refers.setTypeFamily("INTEGER");
        refers.setDialectType("INTEGER");
        Column silent = new Column("b");
        Column spelled = new Column("c");
        spelled.setTypeFamily("ARRAY");
        spelled.setDialectType("VARCHAR[]");

        assertNull(ConceptConverter.of(refers));
        assertNull(ConceptConverter.of(silent));
        assertNull(ConceptConverter.of(spelled));
    }

    @Test
    void aMigratedColumnHasNothingButItsConceptAndWhatBelongsToTheTable() {
        Schema migrated = ConceptConverter.migrate(OLD);

        for (Table table : migrated.getTables()) {
            for (Column column : table.getColumns()) {
                String where = table.getName() + "." + column.getName();
                assertNotNull(column.getConcept(), where);
                assertNull(column.getLabel(), where);
                assertNull(column.getComment(), where);
                assertNull(column.getDescription(), where);
                assertNull(column.getTypeFamily(), where);
                assertNull(column.getTypeEncoding(), where);
                assertNull(column.getDialectType(), where);
                assertNull(column.getFields(), where);
                assertNull(column.getElement(), where);
                assertNull(column.getKey(), where);
                assertNull(column.getValue(), where);
                assertNull(column.getLength(), where);
                assertNull(column.getUnit(), where);
                assertNull(column.getQuantity(), where);
                assertNull(column.getPresentation(), where);
            }
        }
        Column nr = migrated.getTables().get(0).getColumns().get(0);
        assertEquals(1, nr.getPkPos());
        assertEquals(Boolean.FALSE, nr.getNullable());
    }

    @Test
    void aMigratedSchemaTypesAsTheOldOneDid() {
        Schema migrated = ConceptConverter.migrate(OLD);
        ConceptResolver resolver = new ConceptResolver(migrated);

        for (int t = 0; t < OLD.getTables().size(); t++) {
            for (int c = 0; c < OLD.getTables().get(t).getColumns().size(); c++) {
                assertEquals(
                        PARSER.parse(OLD.getTables().get(t).getColumns().get(c)),
                        resolver.descriptor(
                                migrated.getTables().get(t).getColumns().get(c), PARSER),
                        OLD.getTables().get(t).getName());
            }
        }
    }

    @Test
    void aContainerThatIsOnlyASpellingKeepsItsOldFieldsWhenMigrated() {
        Schema migrated =
                ConceptConverter.migrate(
                        CatalogLoader.readSchemaJson(
                                """
                                { "name": "d", "relations": [], "tables": [ { "name": "t", "columns": [
                                  { "name": "tags", "typeFamily": "ARRAY", "dialectType": "VARCHAR[]" },
                                  { "name": "n", "typeFamily": "INTEGER", "dialectType": "INTEGER" } ] } ] }
                                """));

        Column tags = migrated.getTables().get(0).getColumns().get(0);
        Column n = migrated.getTables().get(0).getColumns().get(1);
        assertNull(tags.getConcept());
        assertEquals("VARCHAR[]", tags.getDialectType());
        assertNull(n.getDialectType());
        assertEquals("INTEGER", n.getConcept().getDialectType());
    }

    @Test
    void aContainerKeepsTheSpellingOfItsTypeInTheDatabase() {
        for (String name : new String[] {"dims", "address", "items", "deep", "matrix"}) {
            Column old = column(OLD, "check_complex", name);
            Column converted = column(NEW, "check_complex", name);

            assertEquals(old.getDialectType(), converted.getConcept().getDialectType(), name);
        }
        // and the concept checks it against its entries
        new ConceptResolver(NEW).check(PARSER);
    }

    @Test
    void theOriginalSchemaIsNotChanged() {
        assertNull(column(OLD, "check_complex", "dims").getConcept());
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
