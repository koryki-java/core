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

import java.time.temporal.ChronoUnit;

/**
 * The three classes of interval unit that have an anchor-independent order among themselves
 * (docs/TEMPORAL.md, "Comparisons"). Across classes there is none: {@code 1mo} against {@code 30d}
 * and {@code 1d} against {@code 25h} have no answer that does not depend on <em>which</em> month or
 * <em>which</em> day.
 *
 * <p>Shared deliberately. Two places need this same judgement and must not drift apart:
 * {@code FunctionValidator.checkIntervalComparison}, which rejects ordering across classes, and
 * {@link EncodingLattice}, which may only scale one unit into another <em>within</em> a class.
 * The lattice case is the subtle one — {@code DAYS} to {@code HOURS} is an exact ×24 by
 * {@link ChronoUnit#getDuration()} and would look perfectly convertible on the arithmetic alone,
 * while being exactly the conversion the validator forbids.
 */
public enum IntervalUnitClass {

    /** Elapsed clock time: HOURS down to NANOS. Fixed lengths, so these scale into each other. */
    CLOCK,

    /** Whole days and weeks. A calendar day is not always 24 hours (DST), hence its own class. */
    DAY,

    /** Calendar months, quarters and years. A month has no fixed length. */
    MONTH;

    /** The class of a {@link ChronoUnit} as used by {@link IntervalTypeEncoding}. */
    public static IntervalUnitClass of(ChronoUnit unit) {
        return switch (unit) {
            case YEARS, MONTHS -> MONTH;
            case WEEKS, DAYS -> DAY;
            default -> CLOCK;               // HOURS .. NANOS
        };
    }
}
