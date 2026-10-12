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
package ai.koryki.tools.flatten;

import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeDescriptorParser;
import ai.koryki.catalog.types.TypeTree;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The tree of every nested column of a catalog, as the nodes the catalog would carry for the type
 * its spelling describes: {@code TypeTrees db.json out.json} writes {@code {"table.column": {fields
 * ...}}}. The tool a catalog written as spellings is converted with.
 */
public final class TypeTrees {

    private TypeTrees() {}

    private static final TypeDescriptorParser PARSER = new TypeDescriptorParser() {};

    public static Map<String, Map<String, Object>> of(Schema db) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Table table : db.getTables()) {
            for (Column column : table.getColumns()) {
                if (TypeTree.hasTree(column)) {
                    continue;
                }
                TypeDescriptor type;
                try {
                    type = PARSER.parse(column);
                } catch (RuntimeException unreadable) {
                    continue;
                }
                Map<String, Object> children = TypeTree.children(type);
                if (!children.isEmpty()) {
                    out.put(table.getName() + "." + column.getName(), children);
                }
            }
        }
        return out;
    }

    public static void main(String[] args) throws java.io.IOException {
        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        Schema db = mapper.readValue(new java.io.File(args[0]), Schema.class);
        mapper.writerWithDefaultPrettyPrinter().writeValue(new java.io.File(args[1]), of(db));
    }
}
