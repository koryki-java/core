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

import com.fasterxml.jackson.annotation.JsonValue;

import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Storage declaration "interval as integer count of a unit": an INTERVAL amount
 * held in a plain numeric column as a count of <em>unit</em>s — e.g. a NUMBER
 * column storing seconds ({@code 3600} → 1h under {@code DURATION:SECONDS}),
 * millis, or a YEAR-TO-MONTH interval as a count of months ({@code DURATION:MONTHS}).
 * The unit decides the materialized java.time value: time units (SECONDS, MILLIS,
 * MICROS, NANOS, HOURS, …) yield a {@link java.time.Duration}; calendar units
 * (MONTHS, YEARS) a {@link java.time.Period}.
 *
 * <p>The non-native counterpart of the native INTERVAL columns (whose family +
 * dialectType already describe them, no encoding needed) — this adapts the model
 * to existing schemas that store durations in numeric columns, the same role
 * {@link CoreTypeEncoding#TIME_SECONDS_FROM_MIDNIGHT} plays for TIME.
 *
 * <p>Declared in the schema as {@code "DURATION:<unit>"} (e.g. {@code "DURATION:MILLIS"});
 * parsed and cached by {@link TypeEncodingRegistry}.
 */
public final class IntervalTypeEncoding implements TypeEncoding {

    public static final String PREFIX = "INTERVAL:";

    private final ChronoUnit unit;

    public IntervalTypeEncoding(ChronoUnit unit) {
        this.unit = Objects.requireNonNull(unit);
    }

    public static IntervalTypeEncoding parse(String name) {
        return new IntervalTypeEncoding(ChronoUnit.valueOf(name.substring(PREFIX.length())));
    }

    public ChronoUnit getUnit() {
        return unit;
    }

    @Override
    public TypeFamily family() {
        return CoreTypeFamily.INTERVAL;
    }

    @JsonValue
    @Override
    public String name() {
        return PREFIX + unit.name();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof IntervalTypeEncoding other && unit == other.unit;
    }

    @Override
    public int hashCode() {
        return unit.hashCode();
    }

    @Override
    public String toString() {
        return name();
    }
}
