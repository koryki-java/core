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
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeDescriptorParser;
import ai.koryki.iql.query.Selector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the catalog knows about the part of a nested column a selector chain ends at, read from the
 * concepts: {@link NestedNodes} does the same from the nodes a column declares itself. {@code
 * t.address@geo@lat} is the concept the {@code lat} entry of the {@code geo} entry of the concept
 * of {@code address} refers to, and a consumer that explains a result column (a heading, a unit)
 * reads its label, unit, quantity and presentation there.
 *
 * <p>The steps follow the same rules as the types do. A struct is entered by a field name, without
 * regard to case, or by a position (from the end when negative); a list by a position, to its
 * element; a map by a key, to its value. Where the container is stored as JSON (the encoding {@code
 * JSON}) a struct is entered by name only, a list by position only, and a map by a position only
 * when its keys are integers. A range selects a list of elements, which is no node.
 *
 * <p>An entry that is a type spelling and not a concept says nothing: it is found, and has no
 * label, unit or quantity, and nothing below it. Where the catalog has no part at all (a column
 * without concept, a name that is no entry) the answer is empty, and the caller falls back to the
 * column.
 */
public final class ConceptNodes {

    private final Schema schema;

    public ConceptNodes(Schema schema) {
        this.schema = schema;
    }

    /** The concept at the end of the chain; the concept of the column itself for no steps. */
    public Optional<Concept> node(Column column, List<Selector> steps) {
        if (column.getConcept() == null) {
            return Optional.empty();
        }
        Concept node = schema.getConcepts().get(column.getConcept());
        for (Selector step : steps) {
            if (node == null) {
                return Optional.empty();
            }
            node = step(node, step);
        }
        return Optional.ofNullable(node);
    }

    /** The element of a list concept, or empty. */
    public Optional<Concept> element(Concept list) {
        return entry(list, "element");
    }

    /**
     * The fields of a struct concept, in order; a field that is a spelling is a concept without
     * label.
     */
    public List<Concept> fields(Concept struct) {
        List<Concept> found = new ArrayList<>();
        if ("STRUCT".equals(struct.getTypeFamily()) && struct.getEntries() != null) {
            struct.getEntries().values().forEach(value -> found.add(resolve(value)));
        }
        return found;
    }

    private Concept step(Concept node, Selector step) {
        if (step.getFrom() != null || step.getTo() != null) {
            return null;
        }
        boolean json = "JSON".equals(node.getTypeEncoding());
        boolean named = step.getId() != null || step.getName() != null;
        String name = step.getId() != null ? step.getId() : step.getName();
        if ("STRUCT".equals(node.getTypeFamily())) {
            return json && !named ? null : field(node, step, named, name);
        } else if ("ARRAY".equals(node.getTypeFamily())) {
            return json && named ? null : entry(node, "element").orElse(null);
        } else if ("MAP".equals(node.getTypeFamily())) {
            return json && !named && !integerKeys(node) ? null : entry(node, "value").orElse(null);
        }
        return null;
    }

    private boolean integerKeys(Concept map) {
        String key = map.getEntries() == null ? null : map.getEntries().get("key");
        if (key == null) {
            return false;
        }
        Concept concept = resolve(key);
        if (concept.getTypeFamily() != null) {
            return "INTEGER".equals(concept.getTypeFamily());
        }
        try {
            return CoreTypeFamily.INTEGER.equals(
                    new TypeDescriptorParser() {}.inferFamily(key.toUpperCase()));
        } catch (IllegalArgumentException unknown) {
            return false;
        }
    }

    private Concept field(Concept struct, Selector step, boolean named, String name) {
        Map<String, String> entries = struct.getEntries();
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>(entries.keySet());
        if (!named) {
            if (step.getIndex() == null) {
                return null;
            }
            int index = step.getIndex() > 0 ? step.getIndex() - 1 : names.size() + step.getIndex();
            return index >= 0 && index < names.size()
                    ? resolve(entries.get(names.get(index)))
                    : null;
        }
        for (String field : names) {
            if (field.equals(name)) {
                return resolve(entries.get(field));
            }
        }
        for (String field : names) {
            if (field.equalsIgnoreCase(name)) {
                return resolve(entries.get(field));
            }
        }
        return null;
    }

    private Optional<Concept> entry(Concept concept, String key) {
        if (concept.getEntries() == null || !concept.getEntries().containsKey(key)) {
            return Optional.empty();
        }
        return Optional.of(resolve(concept.getEntries().get(key)));
    }

    /**
     * What an entry refers to: the concept of that name, or for a spelling a concept that says
     * nothing.
     */
    private Concept resolve(String value) {
        Concept concept = schema.getConcepts() == null ? null : schema.getConcepts().get(value);
        if (concept != null) {
            return concept;
        }
        Concept spelled = new Concept();
        spelled.setDialectType(value);
        return spelled;
    }
}
