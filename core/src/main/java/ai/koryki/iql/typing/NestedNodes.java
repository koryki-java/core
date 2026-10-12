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
package ai.koryki.iql.typing;

import ai.koryki.catalog.schema.Column;
import ai.koryki.iql.query.Selector;
import java.util.List;
import java.util.Optional;

/**
 * The node of a nested column a selector chain ends at: where {@link Selectors} says what
 * <em>type</em> a chain has, this says what the catalog <em>knows</em> about it - its label, its
 * unit, its quantity, its encoding. {@code t.address@geo@lat} is the {@code lat} node of the {@code
 * geo} node of the {@code address} column, and a consumer that explains a result column (a heading,
 * a unit) reads it there.
 *
 * <p>The steps follow the same rules as the types do. A struct is entered by a field name, without
 * regard to case, or by a position (from the end when negative); a list by a position, to its
 * element; a map by a key, to its value; a JSON value by a member name, a position or a key, as far
 * as the catalog lists members. A range selects a list of elements, which is no node. Where the
 * catalog says nothing - a column described only by its spelling, a JSON member not listed - the
 * answer is empty, and the caller falls back to the column.
 */
public final class NestedNodes {

    private NestedNodes() {}

    /** The node at the end of the chain; the column itself for no steps. */
    public static Optional<Column> node(Column column, List<Selector> steps) {
        Column node = column;
        for (Selector step : steps) {
            node = step(node, step);
            if (node == null) {
                return Optional.empty();
            }
        }
        return Optional.of(node);
    }

    private static Column step(Column node, Selector step) {
        if (step.getFrom() != null || step.getTo() != null) {
            return null;
        }
        String family = node.getTypeFamily();
        boolean named = step.getId() != null || step.getName() != null;
        String name = step.getId() != null ? step.getId() : step.getName();
        List<Column> fields = node.getFields();
        if ("STRUCT".equals(family) || ("JSON".equals(family) && fields != null && named)) {
            return field(fields, step, named, name);
        } else if ("ARRAY".equals(family) || "JSON".equals(family) && !named) {
            return node.getElement();
        } else if ("MAP".equals(family) || "JSON".equals(family)) {
            return node.getValue();
        }
        return null;
    }

    private static Column field(List<Column> fields, Selector step, boolean named, String name) {
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        if (!named) {
            if (step.getIndex() == null) {
                return null;
            }
            int index = step.getIndex() > 0 ? step.getIndex() - 1 : fields.size() + step.getIndex();
            return index >= 0 && index < fields.size() ? fields.get(index) : null;
        }
        for (Column field : fields) {
            if (field.getName().equals(name)) {
                return field;
            }
        }
        for (Column field : fields) {
            if (field.getName().equalsIgnoreCase(name)) {
                return field;
            }
        }
        return null;
    }
}
