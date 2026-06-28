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
 * Named {@link FamilyGroup}s for declaring operand-type checks — the koryki
 * analogue of Calcite's umbrella {@code SqlTypeFamily} values.
 */
public final class Families {

    private Families() {
    }

    /** Any exact (INTEGER, DECIMAL) or approximate (FLOAT) numeric value. */
    public static final TypeFamily NUMERIC = new FamilyGroup("NUMERIC",
            Set.of(CoreTypeFamily.INTEGER, CoreTypeFamily.DECIMAL, CoreTypeFamily.FLOAT));

    /** Any date/time value (DATE, TIME, TIMESTAMP). */
    public static final TypeFamily TEMPORAL = new FamilyGroup("TEMPORAL",
            Set.of(CoreTypeFamily.DATE, CoreTypeFamily.TIME, CoreTypeFamily.TIMESTAMP));

    /** A numeric value or an interval — the operand domain of additive aggregates such as {@code sum}. */
    public static final TypeFamily ADDITIVE = new FamilyGroup("ADDITIVE",
            Set.of(CoreTypeFamily.INTEGER, CoreTypeFamily.DECIMAL, CoreTypeFamily.FLOAT, CoreTypeFamily.INTERVAL));

    /**
     * Any value at all — the explicit, non-null wildcard for genuinely polymorphic operand positions
     * (comparison operands, {@code coalesce}/{@code case} results, the source of a cast). Behaviourally
     * equivalent to a {@code null} family in operand-family matching, but declared, so every argument
     * carries a type.
     */
    public static final TypeFamily ANY = new FamilyGroup("ANY", Set.of(
            CoreTypeFamily.BLOB, CoreTypeFamily.BOOLEAN, CoreTypeFamily.DATE, CoreTypeFamily.DECIMAL,
            CoreTypeFamily.FLOAT, CoreTypeFamily.INTEGER, CoreTypeFamily.TIME, CoreTypeFamily.INTERVAL,
            CoreTypeFamily.TIMESTAMP, CoreTypeFamily.TEXT, CoreTypeFamily.JSON, CoreTypeFamily.UUID));
}
