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

import com.fasterxml.jackson.annotation.JsonValue;

import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Storage declaration "instant as epoch integer": the column holds a point in
 * time as a count of <em>unit</em>s since 1970-01-01T00:00:00Z — Unix epoch
 * seconds, Java epoch millis, BigQuery/Arrow micros, Spark/Arrow nanos.
 * Combines the <em>instant</em> semantics with an integer representation, so
 * reads are normalized to the model zone at the boundary like {@code INSTANT}.
 *
 * <p>Declared in the schema as {@code "EPOCH:<unit>"}, e.g. {@code "EPOCH:MILLIS"}
 * ({@link ChronoUnit#SECONDS}, {@code MILLIS}, {@code MICROS}, {@code NANOS});
 * parsed and cached by {@link TypeEncodingRegistry}.
 */
public final class EpochTypeEncoding implements TypeEncoding {

    public static final String PREFIX = "EPOCH:";

    private final ChronoUnit unit;

    public EpochTypeEncoding(ChronoUnit unit) {
        this.unit = Objects.requireNonNull(unit);
    }

    public static EpochTypeEncoding parse(String name) {
        return new EpochTypeEncoding(ChronoUnit.valueOf(name.substring(PREFIX.length())));
    }

    public ChronoUnit getUnit() {
        return unit;
    }

    @Override
    public TypeFamily family() {
        return CoreTypeFamily.TIMESTAMP;
    }

    @JsonValue
    @Override
    public String name() {
        return PREFIX + unit.name();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EpochTypeEncoding other && unit == other.unit;
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
