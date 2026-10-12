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
package ai.koryki.catalog.schema.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.SchemaCopy;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeDescriptorParser;
import ai.koryki.catalog.types.TypeTree;
import org.junit.jupiter.api.Test;

/** A nested column as the catalog writes it: db.json in, descriptor out, and a faithful copy. */
class NestedColumnCatalogTest {

    private static final String DB =
            """
            { "name": "complex", "tables": [ { "name": "t", "columns": [
              { "name": "nr", "typeFamily": "INTEGER", "dialectType": "SMALLINT" },
              { "name": "tags", "typeFamily": "ARRAY", "dialectType": "VARCHAR[]" },
              { "name": "events", "typeFamily": "ARRAY",
                "dialectType": "STRUCT(ts BIGINT, day DATE)[]",
                "element": { "typeFamily": "STRUCT", "fields": [
                  { "name": "ts", "label": "Timestamp", "typeFamily": "TIMESTAMP",
                    "typeEncoding": "EPOCH:SECONDS", "dialectType": "BIGINT" },
                  { "name": "day", "typeFamily": "DATE", "dialectType": "DATE" } ] } },
              { "name": "address", "typeFamily": "STRUCT", "fields": [
                { "name": "street", "typeFamily": "TEXT" },
                { "name": "geo", "typeFamily": "STRUCT", "fields": [
                  { "name": "lat", "typeFamily": "FLOAT", "unit": "deg" },
                  { "name": "lon", "typeFamily": "FLOAT" } ] },
                { "name": "phones", "typeFamily": "ARRAY", "element": { "typeFamily": "TEXT" } } ] }
            ] } ], "relations": [] }
            """;

    private static Column column(Schema schema, String name) {
        return schema.getTables().get(0).getColumns().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void aNestedColumnLoadsFromJson() {
        Schema schema = CatalogLoader.readSchemaJson(DB);
        TypeDescriptorParser parser = new TypeDescriptorParser() {};

        assertEquals("ARRAY(TEXT)", parser.parse(column(schema, "tags")).portable());
        assertFalse(TypeTree.hasTree(column(schema, "tags")), "a spelling alone stays a spelling");

        TypeDescriptor events = parser.parse(column(schema, "events"));
        assertEquals("ARRAY(STRUCT(ts:TIMESTAMP,day:DATE))", events.portable());
        assertEquals(
                "EPOCH:SECONDS",
                events.element().field("ts").orElseThrow().getTypeEncoding().name());
    }

    @Test
    void aColumnWithoutASpellingIsItsTree() {
        Schema schema = CatalogLoader.readSchemaJson(DB);

        TypeDescriptor address = new TypeDescriptorParser() {}.parse(column(schema, "address"));

        assertEquals(
                "STRUCT(street:TEXT,geo:STRUCT(lat:FLOAT,lon:FLOAT),phones:ARRAY(TEXT))",
                address.portable());
    }

    @Test
    void theNodesCarryWhatASpellingCannot() {
        Schema schema = CatalogLoader.readSchemaJson(DB);

        Column ts = column(schema, "events").getElement().getFields().get(0);
        Column lat = column(schema, "address").getFields().get(1).getFields().get(0);

        assertEquals("Timestamp", ts.getLabel());
        assertEquals("deg", lat.getUnit());
    }

    @Test
    void aDeepCopyKeepsTheTree() {
        Column original = column(CatalogLoader.readSchemaJson(DB), "events");

        Column copy = SchemaCopy.deepCopy(original);

        assertEquals("Timestamp", copy.getElement().getFields().get(0).getLabel());
        assertEquals("EPOCH:SECONDS", copy.getElement().getFields().get(0).getTypeEncoding());
        assertEquals(
                new TypeDescriptorParser() {}.parse(original),
                new TypeDescriptorParser() {}.parse(copy));
    }

    @Test
    void aDeepCopyOfAPlainColumnStaysPlain() {
        Column copy = SchemaCopy.deepCopy(column(CatalogLoader.readSchemaJson(DB), "tags"));

        assertFalse(TypeTree.hasTree(copy));
        assertNull(copy.getElement());
    }

    @Test
    void aSpellingThatDisagreesWithTheTreeIsRefused() {
        String db = DB.replace("STRUCT(ts BIGINT, day DATE)[]", "STRUCT(ts BIGINT, d DATE)[]");

        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new TypeDescriptorParser() {}.parse(
                                        column(CatalogLoader.readSchemaJson(db), "events")));

        assertTrue(e.getMessage().contains("column 'events'"), e.getMessage());
        assertTrue(e.getMessage().contains("dialectType"), e.getMessage());
    }
}
