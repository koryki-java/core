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
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Schema;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the concept a name or a column refers to in the {@code concepts} of a schema.
 *
 * <p>The schema classes are plain data and know nothing of each other's names; whoever needs the
 * concept of a column asks here, so that a name that is not there is reported in one place and in
 * one way: with the column or the name, and with the names the schema does have.
 */
public final class ConceptResolver {

    private final Schema schema;

    public ConceptResolver(Schema schema) {
        this.schema = schema;
    }

    /**
     * The concept a column refers to.
     *
     * @throws IllegalArgumentException when the column refers to none, or to a name the schema does
     *     not have
     */
    public Concept concept(Column column) {
        if (column.getConcept() == null) {
            throw new IllegalArgumentException(
                    "column '" + column.getName() + "': it refers to no concept");
        }
        return find(column.getConcept(), "column '" + column.getName() + "'");
    }

    /**
     * The concept of that name.
     *
     * @throws IllegalArgumentException when the schema does not have it
     */
    public Concept concept(String name) {
        return find(name, null);
    }

    /**
     * Whether every entry of every concept refers to something: the value of an entry is the name
     * of a concept of the schema, or a type spelling ({@code VARCHAR}, {@code DOUBLE[3]}, {@code
     * STRUCT(lat DOUBLE)}) the parser can read. A name wins over a spelling.
     *
     * @param parser reads a spelling; a dialect may know names others do not
     * @throws IllegalArgumentException for the first entry that refers to neither, naming the
     *     concept, the entry and the names the schema has
     */
    public void checkEntries(TypeDescriptorParser parser) {
        Map<String, Concept> concepts = schema.getConcepts();
        if (concepts == null) {
            return;
        }
        for (Map.Entry<String, Concept> concept : concepts.entrySet()) {
            Map<String, String> entries = concept.getValue().getEntries();
            if (entries == null) {
                continue;
            }
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                String value = entry.getValue();
                if (value != null && (concepts.containsKey(value) || spelling(value, parser))) {
                    continue;
                }
                throw new IllegalArgumentException(
                        "concept '"
                                + concept.getKey()
                                + "': entry '"
                                + entry.getKey()
                                + "' refers to "
                                + (value == null ? "nothing" : "'" + value + "'")
                                + ", which is neither a concept nor a type spelling; the schema has "
                                + String.join(", ", concepts.keySet()));
            }
        }
    }

    /**
     * Runs the four checks, simplest first, so that a catalog with several mistakes is told about
     * the plainest one: the names ({@link #checkNames}), the shapes of the entries ({@link
     * #checkShapes}), what the entries refer to ({@link #checkEntries}) and the cycles ({@link
     * #checkCycles}). A schema without concepts passes.
     *
     * @param parser reads a spelling; a dialect may know names others do not
     * @throws IllegalArgumentException for the first mistake of the first check that finds one
     */
    public void check(TypeDescriptorParser parser) {
        checkNames(parser);
        checkShapes();
        checkEntries(parser);
        checkCycles();
    }

    /**
     * Whether no concept is named like a type. A name is looked up before a spelling, so a concept
     * called {@code varchar} would take the place of the type {@code VARCHAR} in every entry that
     * says it, and a catalog would change its meaning by the name of one concept. The names of the
     * families ({@code TEXT}, {@code STRUCT}) are taken, whatever the case, and so is everything
     * the parser reads as a type: {@code double}, {@code DECIMAL(10,2)}.
     *
     * @param parser reads a spelling; a dialect may know names others do not
     * @throws IllegalArgumentException for the first concept with such a name
     */
    public void checkNames(TypeDescriptorParser parser) {
        Map<String, Concept> concepts = schema.getConcepts();
        if (concepts == null) {
            return;
        }
        for (String name : concepts.keySet()) {
            if (isFamily(name) || spelling(name, parser)) {
                throw new IllegalArgumentException(
                        "concept '"
                                + name
                                + "': the name is a type, and an entry that says "
                                + name
                                + " would refer to the concept instead of the type; call it"
                                + " something else");
            }
        }
    }

    private static boolean isFamily(String name) {
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        for (CoreTypeFamily family : CoreTypeFamily.values()) {
            if (family.name().equals(upper)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the entries of every concept are the ones its family has: an ARRAY has the one entry
     * {@code element}, a MAP the two entries {@code key} and {@code value}, a STRUCT at least one
     * entry, and a scalar none. JSON may list the members it is known to hold, any number. An
     * {@code arraySize} is a positive size and belongs to an ARRAY.
     *
     * @throws IllegalArgumentException for the first concept that does not fit, naming it and what
     *     is wrong
     */
    public void checkShapes() {
        Map<String, Concept> concepts = schema.getConcepts();
        if (concepts == null) {
            return;
        }
        for (Map.Entry<String, Concept> concept : concepts.entrySet()) {
            String problem = shapeProblem(concept.getValue());
            if (problem != null) {
                throw new IllegalArgumentException(
                        "concept '" + concept.getKey() + "': " + problem);
            }
        }
    }

    /** What is wrong with the entries of a concept, or null. */
    private static String shapeProblem(Concept concept) {
        Set<String> keys =
                concept.getEntries() == null
                        ? Set.of()
                        : new java.util.LinkedHashSet<>(concept.getEntries().keySet());
        if (concept.getTypeFamily() == null) {
            return keys.isEmpty() && concept.getArraySize() == null
                    ? null
                    : "it has entries or a size but no typeFamily";
        }
        TypeFamily family;
        try {
            family = TypeFamilyRegistry.of(concept.getTypeFamily());
        } catch (IllegalArgumentException unknown) {
            return unknown.getMessage();
        }
        if (concept.getArraySize() != null
                && (!CoreTypeFamily.ARRAY.equals(family) || concept.getArraySize() <= 0)) {
            return CoreTypeFamily.ARRAY.equals(family)
                    ? "an arraySize is a positive size, not " + concept.getArraySize()
                    : "an arraySize belongs to an ARRAY, not to a " + family.name();
        }
        if (CoreTypeFamily.ARRAY.equals(family)) {
            return fixed("an ARRAY", keys, "element");
        } else if (CoreTypeFamily.MAP.equals(family)) {
            return fixed("a MAP", keys, "key", "value");
        } else if (CoreTypeFamily.STRUCT.equals(family)) {
            return keys.isEmpty() ? "a STRUCT has at least one entry" : null;
        } else if (CoreTypeFamily.JSON.equals(family)) {
            return null;
        }
        return keys.isEmpty()
                ? null
                : "a " + family.name() + " has no entries, not " + quoted(keys);
    }

    private static String fixed(String what, Set<String> keys, String... expected) {
        Set<String> missing = new java.util.LinkedHashSet<>(List.of(expected));
        missing.removeAll(keys);
        Set<String> stray = new java.util.LinkedHashSet<>(keys);
        stray.removeAll(List.of(expected));
        if (missing.isEmpty() && stray.isEmpty()) {
            return null;
        }
        return what
                + " has the entries "
                + quoted(new java.util.LinkedHashSet<>(List.of(expected)))
                + (missing.isEmpty() ? "" : "; missing: " + quoted(missing))
                + (stray.isEmpty() ? "" : "; not an entry of it: " + quoted(stray));
    }

    private static String quoted(Set<String> names) {
        return names.stream()
                .map(n -> "'" + n + "'")
                .collect(java.util.stream.Collectors.joining(", "));
    }

    /**
     * Whether no concept contains itself: following the entries that name concepts never leads back
     * to a concept already on the way. A type is a finite tree, so a concept inside itself has
     * none. A concept used twice, by two members or by two concepts, is shared and not a cycle.
     *
     * @throws IllegalArgumentException for the first cycle, with the names along it, from the
     *     concept that comes round again: {@code address -> position -> address}
     */
    public void checkCycles() {
        Map<String, Concept> concepts = schema.getConcepts();
        if (concepts == null) {
            return;
        }
        Set<String> done = new HashSet<>();
        for (String name : concepts.keySet()) {
            follow(name, concepts, new ArrayList<>(), done);
        }
    }

    private static void follow(
            String name, Map<String, Concept> concepts, List<String> way, Set<String> done) {
        int at = way.indexOf(name);
        if (at >= 0) {
            List<String> cycle = new ArrayList<>(way.subList(at, way.size()));
            cycle.add(name);
            throw new IllegalArgumentException(
                    "concept cycle: "
                            + String.join(" -> ", cycle)
                            + "; a concept cannot contain itself, a type is finite");
        }
        if (!done.add(name)) {
            return;
        }
        way.add(name);
        Map<String, String> entries = concepts.get(name).getEntries();
        if (entries != null) {
            for (String value : entries.values()) {
                if (value != null && concepts.containsKey(value)) {
                    follow(value, concepts, way, done);
                }
            }
        }
        way.remove(way.size() - 1);
    }

    private static boolean spelling(String value, TypeDescriptorParser parser) {
        try {
            TypeSyntax.parse(value, parser::parseLeaf);
            return true;
        } catch (RuntimeException unreadable) {
            return false;
        }
    }

    private Concept find(String name, String where) {
        Map<String, Concept> concepts = schema.getConcepts();
        Concept concept = concepts == null ? null : concepts.get(name);
        if (concept == null) {
            throw new IllegalArgumentException(
                    (where == null ? "" : where + ": ")
                            + "no concept '"
                            + name
                            + "'; the schema has "
                            + (concepts == null || concepts.isEmpty()
                                    ? "none"
                                    : String.join(", ", concepts.keySet())));
        }
        return concept;
    }
}
