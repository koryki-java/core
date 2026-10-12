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

import ai.koryki.catalog.schema.NestedType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The declared content of a JSON column. The catalog lists members with the path vocabulary of
 * {@link TypePath} and a scalar family each, {@code .n: INTEGER}, {@code .a.b[]: INTEGER}; this
 * builds the tree they describe: fields make a STRUCT, {@code []} an ARRAY, {@code {}} a MAP of
 * text keys. Members that are not listed stay unknown, so a shape may be partial.
 */
final class ShapedJson {

    private ShapedJson() {}

    private static final class Node {
        final Map<String, Node> fields = new LinkedHashMap<>();
        Node element;
        Node value;
        TypeDescriptor leaf;
    }

    static TypeDescriptor build(String column, List<NestedType> declared) {
        Node root = new Node();
        for (NestedType n : declared) {
            TypePath path = TypePath.parse(n.getPath());
            Node at = root;
            for (TypePath.Step step : path.steps()) {
                at =
                        switch (step.kind()) {
                            case FIELD -> at.fields.computeIfAbsent(step.name(), k -> new Node());
                            case ELEMENT ->
                                    at.element == null ? (at.element = new Node()) : at.element;
                            case VALUE -> at.value == null ? (at.value = new Node()) : at.value;
                            case KEY ->
                                    throw new IllegalArgumentException(
                                            "column '"
                                                    + column
                                                    + "': a JSON object has text keys, '"
                                                    + n.getPath()
                                                    + "' cannot name one");
                        };
            }
            if (at.leaf != null) {
                throw new IllegalArgumentException(
                        "column '" + column + "': '" + n.getPath() + "' is declared twice");
            }
            at.leaf = scalar(column, n);
        }
        return build(column, root, "");
    }

    private static TypeDescriptor scalar(String column, NestedType n) {
        TypeFamily family =
                n.getTypeFamily() == null ? null : TypeFamilyRegistry.of(n.getTypeFamily());
        if (CoreTypeFamily.BOOLEAN.equals(family)) {
            return TypeDescriptor.BOOLEAN;
        } else if (CoreTypeFamily.INTEGER.equals(family)) {
            return TypeDescriptor.INTEGER;
        } else if (CoreTypeFamily.FLOAT.equals(family)) {
            return TypeDescriptor.DOUBLE;
        } else if (CoreTypeFamily.DECIMAL.equals(family)) {
            return TypeDescriptor.DECIMAL;
        } else if (CoreTypeFamily.TEXT.equals(family)) {
            return TypeDescriptor.TEXT;
        }
        throw new IllegalArgumentException(
                "column '"
                        + column
                        + "': a JSON member '"
                        + n.getPath()
                        + "' can be declared BOOLEAN, INTEGER, FLOAT, DECIMAL or TEXT, not "
                        + n.getTypeFamily());
    }

    private static TypeDescriptor build(String column, Node node, String where) {
        int kinds =
                (node.leaf != null ? 1 : 0)
                        + (node.fields.isEmpty() ? 0 : 1)
                        + (node.element != null ? 1 : 0)
                        + (node.value != null ? 1 : 0);
        if (kinds > 1) {
            throw new IllegalArgumentException(
                    "column '"
                            + column
                            + "': '"
                            + (where.isEmpty() ? "(the column)" : where)
                            + "' is declared as more than one of a scalar, an object, a list");
        }
        if (node.leaf != null) {
            return node.leaf;
        } else if (node.element != null) {
            return TypeDescriptor.array(build(column, node.element, where + "[]"));
        } else if (node.value != null) {
            return TypeDescriptor.map(TypeDescriptor.TEXT, build(column, node.value, where + "{}"));
        }
        List<String> names = new ArrayList<>(node.fields.keySet());
        List<TypeDescriptor> types = new ArrayList<>();
        for (String name : names) {
            types.add(build(column, node.fields.get(name), where + "." + name));
        }
        return TypeDescriptor.struct(names, types);
    }
}
