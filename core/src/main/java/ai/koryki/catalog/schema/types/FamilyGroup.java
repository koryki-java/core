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
package ai.koryki.catalog.schema.types;

import java.util.Set;

/**
 * A named umbrella over several leaf {@link TypeFamily} values — e.g. NUMERIC
 * over {INTEGER, DECIMAL, FLOAT}. Lets an operand declare "any numeric value"
 * without enumerating the members at every call site, mirroring Calcite's
 * {@code SqlTypeFamily} groups.
 *
 * <p>A group only ever appears on the <em>declared</em> side of a check: a
 * resolved operand type is always a leaf family, so {@link #accepts} tests
 * membership of the candidate leaf.
 */
public final class FamilyGroup implements TypeFamily {

    private final String name;
    private final Set<TypeFamily> members;

    public FamilyGroup(String name, Set<TypeFamily> members) {
        this.name = name;
        this.members = Set.copyOf(members);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean accepts(TypeFamily candidate) {
        return equals(candidate) || members.contains(candidate);
    }
}
