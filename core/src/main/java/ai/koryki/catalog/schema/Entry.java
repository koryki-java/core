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
package ai.koryki.catalog.schema;

import java.util.Objects;

/**
 * One entry of a container concept: a field of a struct, the element of a list, the key or the
 * value of a map. It is either a reference, the name of a concept of the schema or a type spelling
 * ({@code VARCHAR}, {@code DOUBLE[3]}), or a concept of its own, which belongs to this entry alone
 * and is shared with nobody.
 */
public final class Entry {

    private final String reference;

    private final Concept concept;

    private Entry(String reference, Concept concept) {
        this.reference = reference;
        this.concept = concept;
    }

    /** An entry that refers to a concept by name, or to a type by its spelling. */
    public static Entry of(String reference) {
        return new Entry(Objects.requireNonNull(reference, "reference"), null);
    }

    /** An entry that is a concept of its own. */
    public static Entry of(Concept concept) {
        return new Entry(null, Objects.requireNonNull(concept, "concept"));
    }

    /** The name or spelling this entry refers to; null for an entry of its own. */
    public String getReference() {
        return reference;
    }

    /** The concept of this entry alone; null for a reference. */
    public Concept getConcept() {
        return concept;
    }

    public boolean isInline() {
        return concept != null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Entry that
                && Objects.equals(reference, that.reference)
                && Objects.equals(concept, that.concept);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reference, concept);
    }

    @Override
    public String toString() {
        return reference != null ? reference : "{" + concept + "}";
    }
}
