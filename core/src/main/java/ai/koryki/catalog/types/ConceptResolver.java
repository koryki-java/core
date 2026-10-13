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
import java.util.Map;

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
