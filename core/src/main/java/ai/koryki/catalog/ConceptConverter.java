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

import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Entry;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import ai.koryki.catalog.types.TypeTree;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes the structure a column declares itself as a concept of its own: a scalar column the
 * concept it declares, a nested column the one its nodes (element, key, value, fields) describe.
 * The result is a copy: the old fields stay beside the concept until nothing reads them, and a
 * column that is a container read from its spelling alone is left as it is. A JSON column with
 * content becomes the container it describes (ARRAY, MAP or STRUCT) with the encoding JSON.
 *
 * <p>A node below the column becomes a concept of its own in its entry when it has nodes below it
 * or says more than its type (a label, a unit, an encoding); a plain leaf is written as the
 * spelling of its type. Nothing is named and nothing is shared: the concepts of the schema stay as
 * they were.
 */
public final class ConceptConverter {

    private ConceptConverter() {}

    /**
     * A copy of the schema in which every column that says its type has a concept of its own. A
     * column that has a concept or refers to one is left as it is, and so are the concepts the
     * schema already had.
     */
    public static Schema convert(Schema schema) {
        Schema copy = SchemaCopy.deepCopy(schema);
        for (Table table : copy.getTables()) {
            for (Column column : table.getColumns()) {
                if (column.getConcept() == null
                        && column.getConceptRef() == null
                        && convertible(column)) {
                    column.setConcept(concept(column));
                }
            }
        }
        return copy;
    }

    /**
     * A copy of the schema in which every column that has a concept of its own has nothing else
     * that says its type or its wording: what {@link #convert} writes, and then the old fields
     * dropped. The schema a {@code db.json} is rewritten from; a column that could not be given a
     * concept (a container that is only a spelling) keeps its old fields, since they are all it
     * says.
     */
    public static Schema migrate(Schema schema) {
        Schema converted = convert(schema);
        for (Table table : converted.getTables()) {
            for (Column column : table.getColumns()) {
                if (column.getConcept() != null) {
                    column.setLabel(null);
                    column.setComment(null);
                    column.setDescription(null);
                    column.setTypeFamily(null);
                    column.setTypeEncoding(null);
                    column.setDialectType(null);
                    column.setFields(null);
                    column.setElement(null);
                    column.setKey(null);
                    column.setValue(null);
                    column.setLength(null);
                    column.setUnit(null);
                    column.setQuantity(null);
                    column.setPresentation(null);
                }
            }
        }
        return converted;
    }

    /**
     * The concept of one column, without the schema: the one it has of its own, or the one its old
     * fields describe. For a reader that holds a column and does not care which form it is in.
     *
     * @return null for a column that refers to a shared concept (that one is in the schema), and
     *     for one that says no type, or is a container that is only a spelling
     */
    public static Concept of(Column column) {
        if (column.getConcept() != null) {
            return column.getConcept();
        }
        if (column.getConceptRef() != null || !convertible(column)) {
            return null;
        }
        return concept(column);
    }

    /**
     * Whether the column says enough to have a concept: a scalar with its family, or a container
     * with its nodes. A container that is only a spelling stays as it is.
     */
    private static boolean convertible(Column column) {
        if (column.getTypeFamily() == null) {
            return false;
        }
        boolean container =
                "ARRAY".equals(column.getTypeFamily())
                        || "STRUCT".equals(column.getTypeFamily())
                        || "MAP".equals(column.getTypeFamily());
        return !container || TypeTree.hasTree(column);
    }

    private static Concept concept(Column node) {
        Concept concept = new Concept();
        concept.setLabel(node.getLabel());
        concept.setComment(node.getComment());
        concept.setDescription(node.getDescription());
        concept.setUnit(node.getUnit());
        concept.setQuantity(node.getQuantity());
        concept.setPresentation(node.getPresentation());
        concept.setTypeFamily(familyOf(node));
        if (!TypeTree.hasTree(node)) {
            concept.setTypeEncoding(node.getTypeEncoding());
            concept.setDialectType(
                    node.getDialectType() != null
                            ? node.getDialectType()
                            : spelling(node.getTypeFamily()));
            return concept;
        }
        Map<String, Entry> entries = new LinkedHashMap<>();
        if (node.getElement() != null) {
            entries.put("element", entry(node.getElement()));
        }
        if (node.getKey() != null) {
            entries.put("key", entry(node.getKey()));
        }
        if (node.getValue() != null && node.getKey() == null) {
            // JSON objects have text keys unless the node says otherwise
            entries.put("key", Entry.of("VARCHAR"));
        }
        if (node.getValue() != null) {
            entries.put("value", entry(node.getValue()));
        }
        if (node.getFields() != null) {
            for (Column field : node.getFields()) {
                entries.put(field.getName(), entry(field));
            }
        }
        concept.setEntries(entries);
        concept.setArraySize(node.getLength());
        if ("JSON".equals(node.getTypeFamily())) {
            concept.setTypeEncoding("JSON");
            concept.setDialectType(node.getDialectType() != null ? node.getDialectType() : "JSON");
        }
        return concept;
    }

    /**
     * The family of the concept: a JSON node with content is the container it describes, stored as
     * JSON (its dialect type says so); JSON without content stays JSON.
     */
    private static String familyOf(Column node) {
        if (!"JSON".equals(node.getTypeFamily()) || !TypeTree.hasTree(node)) {
            return node.getTypeFamily();
        }
        if (node.getElement() != null) {
            return "ARRAY";
        } else if (node.getValue() != null || node.getKey() != null) {
            return "MAP";
        }
        return "STRUCT";
    }

    /** What an entry is: a concept of its own for a node that is more than a plain leaf. */
    private static Entry entry(Column node) {
        if (TypeTree.hasTree(node) || says(node)) {
            return Entry.of(concept(node));
        }
        return Entry.of(
                node.getDialectType() != null
                        ? node.getDialectType()
                        : spelling(node.getTypeFamily()));
    }

    private static boolean says(Column node) {
        return node.getLabel() != null
                || node.getComment() != null
                || node.getDescription() != null
                || node.getUnit() != null
                || node.getQuantity() != null
                || node.getPresentation() != null
                || node.getTypeEncoding() != null;
    }

    /** A spelling of the family, for a leaf that does not say its dialect type. */
    private static String spelling(String family) {
        return switch (family) {
            case "TEXT" -> "VARCHAR";
            case "FLOAT" -> "DOUBLE";
            default -> family;
        };
    }
}
