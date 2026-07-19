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

import java.time.ZoneId;
import java.util.Objects;

/**
 * Storage declaration "wall-clock(zone)" of docs/TEMPORAL.md: the column holds
 * wall-clock values written in a declared named zone (not the model zone).
 *
 * <p>Family-tagged so every encoding binds to exactly one family: a wall-clock
 * DATE (no time-of-day) and a wall-clock TIMESTAMP read differently, so they are
 * separate encodings spelled {@code "DATE_WALLCLOCK:<zoneId>"} and
 * {@code "TIMESTAMP_WALLCLOCK:<zoneId>"} (e.g. {@code "DATE_WALLCLOCK:America/New_York"}).
 * Parsed and cached by {@link TypeEncodingRegistry}.
 */
public final class WallClockEncoding implements TypeEncoding {

    public static final String DATE_PREFIX = "DATE_WALLCLOCK:";
    public static final String TIMESTAMP_PREFIX = "TIMESTAMP_WALLCLOCK:";

    private final TypeFamily family;
    private final ZoneId zone;

    public WallClockEncoding(TypeFamily family, ZoneId zone) {
        this.family = Objects.requireNonNull(family);
        this.zone = Objects.requireNonNull(zone);
    }

    /** Whether {@code name} is a wall-clock encoding (either family prefix). */
    public static boolean matches(String name) {
        return name.startsWith(DATE_PREFIX) || name.startsWith(TIMESTAMP_PREFIX);
    }

    public static WallClockEncoding parse(String name) {
        if (name.startsWith(DATE_PREFIX)) {
            return new WallClockEncoding(CoreTypeFamily.DATE, ZoneId.of(name.substring(DATE_PREFIX.length())));
        }
        if (name.startsWith(TIMESTAMP_PREFIX)) {
            return new WallClockEncoding(CoreTypeFamily.TIMESTAMP, ZoneId.of(name.substring(TIMESTAMP_PREFIX.length())));
        }
        throw new IllegalArgumentException("Not a wall-clock encoding: " + name);
    }

    public ZoneId getZone() {
        return zone;
    }

    @Override
    public TypeFamily family() {
        return family;
    }

    @JsonValue
    @Override
    public String name() {
        return (CoreTypeFamily.DATE.equals(family) ? DATE_PREFIX : TIMESTAMP_PREFIX) + zone.getId();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof WallClockEncoding other && family.equals(other.family) && zone.equals(other.zone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(family, zone);
    }

    @Override
    public String toString() {
        return name();
    }
}
