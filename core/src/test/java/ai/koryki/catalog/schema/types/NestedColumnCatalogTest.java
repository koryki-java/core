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
import static org.junit.jupiter.api.Assertions.assertNull;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeDescriptorParser;
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
                "nested": [ { "path": "[].ts", "typeFamily": "TIMESTAMP",
                              "typeEncoding": "EPOCH:SECONDS" } ] }
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
        assertNull(column(schema, "tags").getNested(), "no refinement means null, as before");

        TypeDescriptor events = parser.parse(column(schema, "events"));
        assertEquals("ARRAY(STRUCT(ts:TIMESTAMP,day:DATE))", events.portable());
        assertEquals(
                "EPOCH:SECONDS",
                events.element().field("ts").orElseThrow().getTypeEncoding().name());
    }

    @Test
    void aDeepCopyKeepsTheRefinement() {
        Column original = column(CatalogLoader.readSchemaJson(DB), "events");

        Column copy = Schema.deepCopy(original);

        assertEquals(1, copy.getNested().size());
        assertEquals("[].ts", copy.getNested().get(0).getPath());
        assertEquals("TIMESTAMP", copy.getNested().get(0).getTypeFamily());
        assertEquals("EPOCH:SECONDS", copy.getNested().get(0).getTypeEncoding());
        assertEquals(
                new TypeDescriptorParser() {}.parse(original),
                new TypeDescriptorParser() {}.parse(copy));
    }

    @Test
    void aDeepCopyOfAPlainColumnStaysPlain() {
        assertNull(Schema.deepCopy(column(CatalogLoader.readSchemaJson(DB), "tags")).getNested());
    }
}
