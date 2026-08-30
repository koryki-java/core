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

    /** A numeric value or text — the source domain of the numeric casts (to_integer, to_float, to_decimal, …). */
    public static final TypeFamily NUMBER_OR_TEXT = new FamilyGroup("NUMBER_OR_TEXT",
            Set.of(CoreTypeFamily.INTEGER, CoreTypeFamily.DECIMAL, CoreTypeFamily.FLOAT, CoreTypeFamily.TEXT));

    /** A date/time value or text — the source domain of the temporal casts (to_date, to_time, to_timestamp). */
    public static final TypeFamily TEMPORAL_OR_TEXT = new FamilyGroup("TEMPORAL_OR_TEXT",
            Set.of(CoreTypeFamily.DATE, CoreTypeFamily.TIME, CoreTypeFamily.TIMESTAMP, CoreTypeFamily.TEXT));

    /** A numeric value or an interval — the operand domain of additive aggregates such as {@code sum}. */
    public static final TypeFamily ADDITIVE = new FamilyGroup("ADDITIVE",
            Set.of(CoreTypeFamily.INTEGER, CoreTypeFamily.DECIMAL, CoreTypeFamily.FLOAT, CoreTypeFamily.INTERVAL));

    /**
     * The operand domain of the arithmetic operators — numbers, temporal values and intervals.
     * {@link #ANY} minus TEXT, BOOLEAN, BLOB, JSON and UUID.
     *
     * <p>Arithmetic is not polymorphic the way comparison is. There is no reading of
     * {@code o.customer_id * 2} on a text key, and none of {@code -c.company_name}; SQL's own answer
     * varies from an error to a silent coercion depending on the engine, which is exactly the sort
     * of divergence the catalog exists to remove. TEXT is excluded deliberately rather than by
     * oversight: {@code +} is not string concatenation in KQL.
     *
     * <p>The families kept are the ones the operators actually define behaviour for, per
     * docs/TEMPORAL.md: numeric arithmetic, DATE/TIMESTAMP/TIME ± INTERVAL, DATE + TIME,
     * DATE − DATE, TIMESTAMP − TIMESTAMP and INTERVAL ± INTERVAL.
     */
    public static final TypeFamily ARITHMETIC = new FamilyGroup("ARITHMETIC", Set.of(
            CoreTypeFamily.INTEGER, CoreTypeFamily.DECIMAL, CoreTypeFamily.FLOAT,
            CoreTypeFamily.DATE, CoreTypeFamily.TIME, CoreTypeFamily.TIMESTAMP,
            CoreTypeFamily.INTERVAL));

    /**
     * Any value that can be put in order — the operand domain of the ordered comparisons
     * ({@code <}, {@code <=}, {@code >}, {@code >=}, {@code BETWEEN}). This is {@link #ANY} minus
     * BLOB and JSON, the two families with no portable order.
     *
     * <p>Measured, not assumed. Binary values do order on every engine — but only as a short binary
     * type: an Oracle {@code BLOB} (or {@code CLOB}) raises ORA-22848, "cannot be used as a
     * comparison key", for {@code <} and {@code =} alike. JSON is worse: PostgreSQL's {@code json}
     * has no ordering <em>or</em> equality operator at all, and Trino rejects {@code <} on JSON while
     * accepting {@code =}. Neither family has an answer that holds across dialects, so ordering them
     * is refused up front rather than at the database.
     *
     * <p>Equality is a different question and keeps {@link #ANY}: {@code =}, {@code <>}, {@code IN}
     * and {@code DISTINCT} are meaningful for every family. Where equality still fails it is a
     * storage matter rather than a family one — the Oracle LOB case above is the example, and it
     * reaches TEXT (a CLOB column) as readily as BLOB, so no choice of family here would catch it.
     */
    public static final TypeFamily ORDERED = new FamilyGroup("ORDERED", Set.of(
            CoreTypeFamily.BOOLEAN, CoreTypeFamily.DATE, CoreTypeFamily.DECIMAL,
            CoreTypeFamily.FLOAT, CoreTypeFamily.INTEGER, CoreTypeFamily.TIME,
            CoreTypeFamily.INTERVAL, CoreTypeFamily.TIMESTAMP, CoreTypeFamily.TEXT,
            CoreTypeFamily.UUID));

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
