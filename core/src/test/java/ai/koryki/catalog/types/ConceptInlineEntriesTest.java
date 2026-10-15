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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.SchemaCopy;
import ai.koryki.catalog.Util;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Entry;
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

/** An entry of a container may be a concept of its own, shared with nobody. */
class ConceptInlineEntriesTest {

    private static final TypeDescriptorParser PARSER = new TypeDescriptorParser() {};

    private static Concept scalar(String label, String family, String dialectType) {
        Concept concept = new Concept();
        concept.setLabel(label);
        concept.setTypeFamily(family);
        concept.setDialectType(dialectType);
        return concept;
    }

    private static Concept container(String family, Object... keyAndEntry) {
        Concept concept = new Concept();
        concept.setTypeFamily(family);
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (int i = 0; i < keyAndEntry.length; i += 2) {
            Object value = keyAndEntry[i + 1];
            entries.put(
                    (String) keyAndEntry[i],
                    value instanceof Concept c ? Entry.of(c) : Entry.of((String) value));
        }
        concept.setEntries(entries);
        return concept;
    }

    /** The schema with the table {@code t}, its column {@code c} and the named concepts. */
    private static Schema schema(Concept own, Map<String, Concept> named) {
        Column column = new Column("c");
        column.setConcept(own);
        Table table = new Table("t");
        table.setColumns(new java.util.ArrayList<>(List.of(column)));
        Schema schema = new Schema("s");
        schema.addTable(table);
        schema.setConcepts(new LinkedHashMap<>(named));
        return schema;
    }

    private static TypeDescriptor type(Schema schema) {
        return new ConceptResolver(schema)
                .descriptor(schema.getTables().get(0).getColumns().get(0), PARSER);
    }

    /** {@code a} first, then {@code b}: the order the cycle is found in. */
    private static Map<String, Concept> ordered(Concept a, Concept b) {
        Map<String, Concept> map = new LinkedHashMap<>();
        map.put("a", a);
        map.put("b", b);
        return map;
    }

    private static String refused(Schema schema) {
        return assertThrows(
                        IllegalArgumentException.class,
                        () -> new ConceptResolver(schema).check(PARSER))
                .getMessage();
    }

    @Test
    void aStructWithEntriesOfItsOwnTypesLikeItsSpelling() {
        Concept dims =
                container(
                        "STRUCT",
                        "w",
                        scalar("Width", "FLOAT", "DOUBLE"),
                        "h",
                        scalar("Height", "FLOAT", "DOUBLE"),
                        "note",
                        "VARCHAR");

        assertEquals("STRUCT(w:FLOAT,h:FLOAT,note:TEXT)", type(schema(dims, Map.of())).portable());
    }

    @Test
    void anEntryOfItsOwnMayHaveEntriesOfItsOwn() {
        Concept address =
                container(
                        "STRUCT",
                        "geo",
                        container(
                                "STRUCT",
                                "lat",
                                scalar("Latitude", "FLOAT", "DOUBLE"),
                                "lon",
                                scalar("Longitude", "FLOAT", "DOUBLE")),
                        "phones",
                        container("ARRAY", "element", scalar("Phone", "TEXT", "VARCHAR")));

        assertEquals(
                "STRUCT(geo:STRUCT(lat:FLOAT,lon:FLOAT),phones:ARRAY(TEXT))",
                type(schema(address, Map.of())).portable());
    }

    @Test
    void theElementTheKeyAndTheValueMayBeOfTheirOwn() {
        assertEquals(
                "ARRAY(INTEGER)",
                type(schema(
                                container("ARRAY", "element", scalar("N", "INTEGER", "INTEGER")),
                                Map.of()))
                        .portable());
        assertEquals(
                "MAP(TEXT,INTEGER)",
                type(schema(
                                container(
                                        "MAP",
                                        "key",
                                        scalar("Name", "TEXT", "VARCHAR"),
                                        "value",
                                        scalar("Count", "INTEGER", "INTEGER")),
                                Map.of()))
                        .portable());
    }

    @Test
    void anEntryOfItsOwnAndAReferenceMix() {
        Concept row =
                container(
                        "STRUCT",
                        "a",
                        scalar("A", "INTEGER", "INTEGER"),
                        "b",
                        "count",
                        "c",
                        "VARCHAR");

        assertEquals(
                "STRUCT(a:INTEGER,b:INTEGER,c:TEXT)",
                type(schema(row, Map.of("count", scalar(null, "INTEGER", "BIGINT")))).portable());
    }

    @Test
    void aContainerStoredAsJsonKeepsItsMembersOfTheirOwn() {
        Concept doc = container("STRUCT", "n", scalar("Number", "INTEGER", "INTEGER"));
        doc.setTypeEncoding("JSON");
        doc.setDialectType("VARCHAR");

        TypeDescriptor type = type(schema(doc, Map.of()));

        assertEquals("JSON(STRUCT(n:INTEGER))", type.portable());
        assertEquals("VARCHAR", type.getPhysicalTypeName());
    }

    @Test
    void anEntryOfItsOwnIsCheckedAndNamesWhereItIs() {
        Concept row = container("STRUCT", "geo", container("ARRAY", "x", "VARCHAR"));

        assertEquals(
                "column 't.c', entry 'geo': an ARRAY has the entries 'element'; missing: 'element';"
                        + " not an entry of it: 'x'",
                refused(schema(row, Map.of())));
    }

    @Test
    void aNamedConceptsEntryOfItsOwnIsCheckedToo() {
        Concept row = container("STRUCT", "geo", container("STRUCT"));

        assertEquals(
                "concept 'row', entry 'geo': a STRUCT has at least one entry",
                refused(schema(scalar(null, "INTEGER", "INTEGER"), Map.of("row", row))));
    }

    @Test
    void whatAnEntryOfItsOwnRefersToHasToExist() {
        Concept row = container("STRUCT", "geo", container("STRUCT", "lat", "nowhere"));

        assertTrue(
                refused(schema(row, Map.of()))
                        .startsWith(
                                "column 't.c', entry 'geo': entry 'lat' refers to 'nowhere', which"
                                        + " is neither a concept nor a type spelling"));
    }

    @Test
    void aCycleThroughAnEntryOfItsOwnIsFound() {
        Concept a = container("STRUCT", "inner", container("STRUCT", "back", "a"));

        assertEquals(
                "concept cycle: a -> a; a concept cannot contain itself, a type is finite",
                refused(schema(scalar(null, "INTEGER", "INTEGER"), Map.of("a", a))));
    }

    @Test
    void aCycleOverTwoConceptsThroughAnEntryOfItsOwnIsFound() {
        Concept a = container("STRUCT", "x", container("ARRAY", "element", "b"));
        Concept b = container("STRUCT", "y", "a");

        assertEquals(
                "concept cycle: a -> b -> a; a concept cannot contain itself, a type is finite",
                refused(
                        schema(
                                scalar(null, "INTEGER", "INTEGER"),
                                new LinkedHashMap<>(
                                        java.util.Collections.unmodifiableMap(ordered(a, b))))));
    }

    @Test
    void aSoundSchemaWithEntriesOfTheirOwnPasses() {
        Concept row =
                container(
                        "STRUCT",
                        "geo",
                        container("STRUCT", "lat", scalar("Lat", "FLOAT", "DOUBLE")),
                        "n",
                        "count");

        new ConceptResolver(schema(row, Map.of("count", scalar(null, "INTEGER", "INTEGER"))))
                .check(PARSER);
    }

    @Test
    void theCopyHasItsOwnCopyOfAnEntryOfItsOwn() {
        Concept row = container("STRUCT", "w", scalar("Width", "FLOAT", "DOUBLE"));
        Schema schema = schema(row, Map.of());

        Schema copy = SchemaCopy.deepCopy(schema);
        Concept copied =
                copy.getTables()
                        .get(0)
                        .getColumns()
                        .get(0)
                        .getConcept()
                        .getEntries()
                        .get("w")
                        .getConcept();
        copied.setLabel("changed");

        assertNotSame(row.getEntries().get("w").getConcept(), copied);
        assertEquals("Width", row.getEntries().get("w").getConcept().getLabel());
    }

    @Test
    void anEntryIsAStringOrAnObjectInDbJson(@TempDir Path dir) throws IOException {
        Schema schema =
                CatalogLoader.readSchemaJson(
                        """
                        { "name": "d", "relations": [], "tables": [ { "name": "t", "columns": [
                          { "name": "dims",
                            "concept": { "typeFamily": "STRUCT", "entries": {
                              "w": { "label": "Width", "unit": "m", "typeFamily": "FLOAT",
                                     "dialectType": "DOUBLE" },
                              "note": "VARCHAR" } } } ] } ] }
                        """);
        Concept dims = schema.getTables().get(0).getColumns().get(0).getConcept();

        assertTrue(dims.getEntries().get("w").isInline());
        assertEquals("Width", dims.getEntries().get("w").getConcept().getLabel());
        assertFalse(dims.getEntries().get("note").isInline());
        assertEquals("VARCHAR", dims.getEntries().get("note").getReference());
        assertEquals(List.of("w", "note"), List.copyOf(dims.getEntries().keySet()));

        Path file = dir.resolve("db.json");
        Util.write(schema, file.toFile());
        String written = Files.readString(file);
        assertTrue(written.contains("\"note\" : \"VARCHAR\""), written);
        assertTrue(written.contains("\"label\" : \"Width\""), written);
        assertFalse(written.contains("reference"), written);

        Concept again =
                CatalogLoader.readSchemaJson(written)
                        .getTables()
                        .get(0)
                        .getColumns()
                        .get(0)
                        .getConcept();
        assertEquals("m", again.getEntries().get("w").getConcept().getUnit());
        assertEquals("VARCHAR", again.getEntries().get("note").getReference());
    }

    @Test
    void aBadEntryOfItsOwnInDbJsonIsRefusedWhereItIsRead() {
        assertTrue(
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        CatalogLoader.readSchemaJson(
                                                """
                                                { "name": "d", "relations": [], "tables": [
                                                  { "name": "t", "columns": [
                                                    { "name": "c", "concept": {
                                                      "typeFamily": "STRUCT", "entries": {
                                                        "x": { "typeFamily": "ARRAY" } } } } ] } ] }
                                                """))
                        .getMessage()
                        .startsWith("column 't.c', entry 'x': an ARRAY has the entries 'element'"));
    }
}
