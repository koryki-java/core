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

package ai.koryki.databases.cases;

import ai.koryki.catalog.SchemaToModel;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Entry;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import ai.koryki.iql.LinkResolver;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The catalog of the tables of {@link GeneratedLists}: {@code id}, the lists {@code tags}, {@code
 * nums} and {@code items} (a list of structs {@code sku} and {@code qty}), and for the shape that
 * has one, a JSON column {@code doc}. Built here and not written as JSON, because only the spelling
 * of the types differs between the engines, and the tables are made in the test.
 */
public final class GeneratedListsCatalog {

    private GeneratedListsCatalog() {}

    /**
     * How an engine spells the types of the columns, as the {@code dialectType} of a concept.
     *
     * @param text a text element, {@code VARCHAR2(100)}
     * @param integer an integer element, {@code INTEGER}
     * @param sku the field of an item, {@code VARCHAR2(20)}
     * @param tags a list of {@code text}
     * @param nums a list of {@code integer}
     * @param items a list of structs of {@code sku} and an integer
     * @param json a JSON column
     */
    public record Spelling(
            String text,
            String integer,
            String sku,
            String tags,
            String nums,
            String items,
            String json) {}

    private static Concept scalar(String family, String dialectType) {
        Concept concept = new Concept();
        concept.setTypeFamily(family);
        concept.setDialectType(dialectType);
        return concept;
    }

    private static Concept array(String dialectType, Concept element) {
        Concept concept = new Concept();
        concept.setTypeFamily("ARRAY");
        concept.setDialectType(dialectType);
        Map<String, Entry> entries = new LinkedHashMap<>();
        entries.put("element", Entry.of(element));
        concept.setEntries(entries);
        return concept;
    }

    private static Column column(String name, Concept concept, boolean key) {
        Column column = new Column(name);
        column.setConcept(concept);
        column.setNullable(!key);
        column.setPkPos(key ? 1 : 0);
        return column;
    }

    /**
     * A schema with a table for each name; the value says whether it has the JSON column {@code
     * doc}.
     */
    public static Schema schema(Spelling spelling, Map<String, Boolean> tables) {
        Schema schema = new Schema("generated");
        for (Map.Entry<String, Boolean> shape : tables.entrySet()) {
            Table table = new Table(shape.getKey());
            table.addColumn(column("id", scalar("INTEGER", spelling.integer()), true));
            table.addColumn(
                    column("tags", array(spelling.tags(), scalar("TEXT", spelling.text())), false));
            table.addColumn(
                    column(
                            "nums",
                            array(spelling.nums(), scalar("INTEGER", spelling.integer())),
                            false));
            Concept item = new Concept();
            item.setTypeFamily("STRUCT");
            Map<String, Entry> fields = new LinkedHashMap<>();
            fields.put("sku", Entry.of(scalar("TEXT", spelling.sku())));
            fields.put("qty", Entry.of(scalar("INTEGER", spelling.integer())));
            item.setEntries(fields);
            table.addColumn(column("items", array(spelling.items(), item), false));
            if (shape.getValue()) {
                Concept doc = new Concept();
                doc.setTypeFamily("STRUCT");
                doc.setTypeEncoding("JSON");
                doc.setDialectType(spelling.json());
                Map<String, Entry> members = new LinkedHashMap<>();
                members.put("n", Entry.of(scalar("INTEGER", spelling.integer())));
                doc.setEntries(members);
                table.addColumn(column("doc", doc, false));
            }
            schema.addTable(table);
        }
        return schema;
    }

    /** The resolver over the tables, in English, with the model drafted from the schema. */
    public static LinkResolver resolver(Spelling spelling, Map<String, Boolean> tables) {
        Schema schema = schema(spelling, tables);
        Model model = SchemaToModel.convert(schema);
        return new LinkResolver(Locale.ENGLISH, schema, model, true);
    }

    /** The tables of the shapes a test compares: with and without a JSON column. */
    public static Map<String, Boolean> shapes(String... names) {
        Map<String, Boolean> shapes = new LinkedHashMap<>();
        for (String name : names) {
            shapes.put(name, name.contains("json"));
        }
        return shapes;
    }

    /** The names of the tables of the shapes. */
    public static List<String> names(Map<String, Boolean> shapes) {
        return List.copyOf(shapes.keySet());
    }
}
