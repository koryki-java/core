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

import ai.koryki.catalog.schema.Column;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link TypeDescriptor} as the nodes a catalog column carries: the other direction of {@link
 * TypeDescriptorParser}. A column that was only a spelling becomes the tree that says the same,
 * which is how an introspection tool writes a catalog and how an old one is converted; labels,
 * units and encodings are then added to the nodes by hand.
 *
 * <p>The result is plain maps and lists in the order a catalog file reads best, ready for a JSON
 * writer: {@code fields} for a STRUCT, {@code element} (and {@code length}) for an ARRAY, {@code
 * key} and {@code value} for a MAP, and for a JSON value the members its shape lists.
 */
public final class TypeTree {

    private TypeTree() {}

    /**
     * Whether a column describes the parts of a nested value: it has fields, an element, a key or a
     * value.
     */
    public static boolean hasTree(Column column) {
        return (column.getFields() != null && !column.getFields().isEmpty())
                || column.getElement() != null
                || column.getKey() != null
                || column.getValue() != null;
    }

    /**
     * The children of a nested column, keyed as the catalog keys them; empty for a scalar and for
     * JSON without a shape.
     */
    public static Map<String, Object> children(TypeDescriptor type) {
        boolean json = CoreTypeFamily.JSON.equals(type.getTypeFamily());
        return children(json ? type.shape() : type, json);
    }

    private static Map<String, Object> children(TypeDescriptor structure, boolean json) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (structure == null) {
            return out;
        }
        if (CoreTypeFamily.STRUCT.equals(structure.getTypeFamily())) {
            List<Object> fields = new ArrayList<>();
            for (int i = 0; i < structure.children().size(); i++) {
                fields.add(node(structure.fieldNames().get(i), structure.children().get(i), json));
            }
            out.put("fields", fields);
        } else if (CoreTypeFamily.ARRAY.equals(structure.getTypeFamily())) {
            out.put("element", node(null, structure.element(), json));
            if (structure.length() > 0) {
                out.put("length", structure.length());
            }
        } else if (CoreTypeFamily.MAP.equals(structure.getTypeFamily())) {
            if (!json) {
                out.put("key", node(null, structure.key(), json));
            }
            out.put("value", node(null, structure.value(), json));
        }
        return out;
    }

    /**
     * One node: its name (a field has one), family, encoding where it is not the native one, the
     * physical type of a leaf, and its children. A member of a JSON shape has no physical type.
     */
    private static Map<String, Object> node(String name, TypeDescriptor type, boolean member) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (name != null) {
            out.put("name", name);
        }
        out.put("typeFamily", type.getTypeFamily().name());
        if (type.getTypeEncoding() != null
                && !NativeEncoding.NAME.equals(type.getTypeEncoding().name())) {
            out.put("typeEncoding", type.getTypeEncoding().name());
        }
        if (!type.isComplex() && !member) {
            out.put("dialectType", type.getPhysicalTypeName());
        }
        out.putAll(children(type, member));
        return out;
    }
}
