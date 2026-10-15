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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.Entries;
import ai.koryki.catalog.SchemaCopy;
import ai.koryki.catalog.Util;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A column may carry a concept of its own, which is checked and typed like a named one. */
class ConceptInlineTest {

    private static final TypeDescriptorParser PARSER = new TypeDescriptorParser() {};

    private static Concept concept(String family, String dialectType) {
        Concept concept = new Concept();
        concept.setTypeFamily(family);
        concept.setDialectType(dialectType);
        return concept;
    }

    private static Concept container(String family, String... kv) {
        Concept concept = new Concept();
        concept.setTypeFamily(family);
        Map<String, String> entries = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            entries.put(kv[i], kv[i + 1]);
        }
        concept.setEntries(Entries.of(entries));
        return concept;
    }

    /** A schema with the table {@code t} and the named concepts. */
    private static Schema schema(Column column, Map<String, Concept> named) {
        Table table = new Table("t");
        table.setColumns(new java.util.ArrayList<>(List.of(column)));
        Schema schema = new Schema("s");
        schema.addTable(table);
        schema.setConcepts(new LinkedHashMap<>(named));
        return schema;
    }

    private static Column inline(Concept concept) {
        Column column = new Column("c");
        column.setConcept(concept);
        return column;
    }

    private static String refused(Schema schema) {
        return assertThrows(
                        IllegalArgumentException.class,
                        () -> new ConceptResolver(schema).check(PARSER))
                .getMessage();
    }

    @Test
    void aColumnFindsItsOwnConcept() {
        Concept own = concept("INTEGER", "SMALLINT");
        Column column = inline(own);

        assertSame(own, new ConceptResolver(schema(column, Map.of())).concept(column));
    }

    @Test
    void aColumnWithBothAnOwnConceptAndAReferenceIsRefused() {
        Column column = inline(concept("INTEGER", "SMALLINT"));
        column.setConceptRef("money");
        Schema schema = schema(column, Map.of("money", concept("DECIMAL", "DECIMAL(10,2)")));

        String message = refused(schema);

        assertEquals(
                "column 't.c': it has a concept of its own and refers to 'money'; a column has one"
                        + " or the other",
                message);
        assertThrows(
                IllegalArgumentException.class, () -> new ConceptResolver(schema).concept(column));
    }

    @Test
    void anOwnConceptIsTypedLikeTheSpellingOfItsType() {
        Column column =
                inline(
                        container(
                                "STRUCT", "street", "VARCHAR", "no", "count", "tags", "VARCHAR[]"));
        Schema schema = schema(column, Map.of("count", concept("INTEGER", "SMALLINT")));

        assertEquals(
                TypeSyntax.parse(
                                "STRUCT(street VARCHAR, no SMALLINT, tags VARCHAR[])",
                                PARSER::parseLeaf)
                        .portable(),
                new ConceptResolver(schema).descriptor(column, PARSER).portable());
    }

    @Test
    void anOwnScalarConceptIsTypedLikeItsDialectType() {
        Column column = inline(concept("DECIMAL", "DECIMAL(10,2)"));

        assertEquals(
                PARSER.parse("column 'c'", "DECIMAL", null, "DECIMAL(10,2)"),
                new ConceptResolver(schema(column, Map.of())).descriptor(column, PARSER));
    }

    @Test
    void theShapeOfAnOwnConceptIsCheckedAndNamesTheColumn() {
        Column column = inline(container("ARRAY", "x", "VARCHAR"));

        assertEquals(
                "column 't.c': an ARRAY has the entries 'element'; missing: 'element'; not an entry"
                        + " of it: 'x'",
                refused(schema(column, Map.of())));
    }

    @Test
    void anEntryOfAnOwnConceptHasToReferToSomething() {
        Column column = inline(container("STRUCT", "a", "nowhere"));

        assertTrue(
                refused(schema(column, Map.of("count", concept("INTEGER", "INTEGER"))))
                        .startsWith(
                                "column 't.c': entry 'a' refers to 'nowhere', which is neither a"
                                        + " concept nor a type spelling; the schema has count"));
        assertTrue(refused(schema(column, Map.of())).endsWith("the schema has none"));
    }

    @Test
    void aSoundOwnConceptPasses() {
        Column column = inline(container("STRUCT", "a", "count", "b", "VARCHAR"));

        new ConceptResolver(schema(column, Map.of("count", concept("INTEGER", "INTEGER"))))
                .check(PARSER);
    }

    @Test
    void theCopyOfASchemaHasItsOwnCopyOfTheConcept() {
        Column column = inline(concept("INTEGER", "SMALLINT"));
        column.getConcept().setLabel("Nr");
        Schema schema = schema(column, Map.of());

        Schema copy = SchemaCopy.deepCopy(schema);
        Concept copied = copy.getTables().get(0).getColumns().get(0).getConcept();
        copied.setLabel("changed");

        assertNotSame(column.getConcept(), copied);
        assertEquals("Nr", column.getConcept().getLabel());
        assertEquals("SMALLINT", copied.getDialectType());
    }

    @Test
    void anOwnConceptIsReadAndWrittenInDbJson(@TempDir Path dir) throws IOException {
        Schema schema =
                CatalogLoader.readSchemaJson(
                        """
                        { "name": "d", "relations": [], "tables": [ { "name": "t", "columns": [
                          { "name": "nr", "pkPos": 1,
                            "concept": { "label": "Nr", "typeFamily": "INTEGER",
                                         "dialectType": "SMALLINT" } },
                          { "name": "address",
                            "concept": { "typeFamily": "STRUCT",
                                         "entries": { "street": "VARCHAR", "no": "INTEGER" } } }
                        ] } ] }
                        """);
        Column nr = schema.getTables().get(0).getColumns().get(0);

        assertEquals("Nr", nr.getConcept().getLabel());
        assertEquals("SMALLINT", nr.getConcept().getDialectType());
        assertEquals(
                "STRUCT(street:TEXT,no:INTEGER)",
                new ConceptResolver(schema)
                        .descriptor(schema.getTables().get(0).getColumns().get(1), PARSER)
                        .portable());

        Path file = dir.resolve("db.json");
        Util.write(schema, file.toFile());
        String written = Files.readString(file);
        assertTrue(written.contains("\"concept\""), written);
        assertTrue(!written.contains("conceptRef"), written);
        Schema again = CatalogLoader.readSchemaJson(written);
        assertEquals("Nr", again.getTables().get(0).getColumns().get(0).getConcept().getLabel());
    }

    @Test
    void aSchemaWithABadOwnConceptIsRefusedWhereItIsRead() {
        assertTrue(
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        CatalogLoader.readSchemaJson(
                                                """
                                                { "name": "d", "relations": [], "tables": [
                                                  { "name": "t", "columns": [
                                                    { "name": "c",
                                                      "concept": { "typeFamily": "STRUCT" } } ] } ] }
                                                """))
                        .getMessage()
                        .startsWith("column 't.c': a STRUCT has at least one entry"));
    }
}
