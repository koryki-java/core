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
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import ai.koryki.catalog.types.TypeTree;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes the structure a nested column declares with its own nodes (element, key, value, fields) as
 * concepts, so that the column refers to a concept instead. The result is a copy: the old fields
 * stay beside the reference until nothing reads them, and a column without nodes (a scalar, or a
 * nested column read from its spelling alone) is left as it is. A JSON column with content becomes
 * the container it describes (ARRAY, MAP or STRUCT) with the encoding JSON.
 *
 * <p>The names are mechanical: the concept of a column is {@code table_column}, and every node
 * below it adds its role ({@code element}, {@code key}, {@code value}) or its field name. A node
 * becomes a concept of its own when it has nodes below it or says more than its type (a label, a
 * unit, an encoding); a plain leaf is written as the spelling of its type in the entry.
 */
public final class ConceptConverter {

    private final Map<String, Concept> concepts = new LinkedHashMap<>();

    private ConceptConverter() {}

    /**
     * A copy of the schema in which every column that says its type has a concept of its own: a
     * scalar column the one it declares, a nested column the one its nodes describe. The nodes
     * below the column are concepts of the schema, named by their path. A column that has a concept
     * or refers to one is left as it is, and so are the concepts the schema already had.
     *
     * @throws IllegalArgumentException when two nodes would get the same concept name
     */
    public static Schema convert(Schema schema) {
        Schema copy = SchemaCopy.deepCopy(schema);
        ConceptConverter converter = new ConceptConverter();
        if (copy.getConcepts() != null) {
            converter.concepts.putAll(copy.getConcepts());
        }
        for (Table table : copy.getTables()) {
            for (Column column : table.getColumns()) {
                if (column.getConcept() == null
                        && column.getConceptRef() == null
                        && convertible(column)) {
                    column.setConcept(
                            converter.concept(column, table.getName() + "_" + column.getName()));
                }
            }
        }
        copy.setConcepts(converter.concepts);
        return copy;
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

    private void put(String name, Concept concept) {
        if (concepts.putIfAbsent(name, concept) != null) {
            throw new IllegalArgumentException("two nodes would be the concept '" + name + "'");
        }
    }

    private Concept concept(Column node, String name) {
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
        Map<String, String> entries = new LinkedHashMap<>();
        if (node.getElement() != null) {
            entries.put("element", reference(node.getElement(), name + "_element"));
        }
        if (node.getKey() != null) {
            entries.put("key", reference(node.getKey(), name + "_key"));
        }
        if (node.getValue() != null && node.getKey() == null) {
            // JSON objects have text keys unless the node says otherwise
            entries.put("key", "VARCHAR");
        }
        if (node.getValue() != null) {
            entries.put("value", reference(node.getValue(), name + "_value"));
        }
        if (node.getFields() != null) {
            for (Column field : node.getFields()) {
                entries.put(
                        field.getName(),
                        reference(field, name + "_" + field.getName().replaceAll("\\W", "_")));
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

    /** What an entry says: the name of a concept for a node that is more than a plain leaf. */
    private String reference(Column node, String name) {
        if (TypeTree.hasTree(node) || says(node)) {
            put(name, concept(node, name));
            return name;
        }
        return node.getDialectType() != null
                ? node.getDialectType()
                : spelling(node.getTypeFamily());
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
