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

import java.util.Objects;

/**
 * Storage declaration "interval as a formatted string": an INTERVAL held in a
 * text column, written in a declared {@link Format}. Mirrors
 * {@link WallClockEncoding} — which parameterizes a temporal column by its named
 * zone — except the parameter here is the textual interval <em>format</em>, so
 * that both the read decoder ({@link ai.koryki.jdbc.CoreDecoder}) and the
 * comparison SQL-decode know how to parse the stored text.
 *
 * <p>Spelled {@code "INTERVAL_FROM_STRING:<format>"} (e.g.
 * {@code "INTERVAL_FROM_STRING:ISO8601"}); parsed and cached by
 * {@link TypeEncodingRegistry}. The bare, formatless
 * {@link CoreTypeEncoding#INTERVAL_FROM_STRING} is the implied {@link Format#ISO8601}.
 */
public final class IntervalStringEncoding implements TypeEncoding {

    public static final String PREFIX = "INTERVAL_FROM_STRING:";

    /** The textual form the interval is stored in. */
    public enum Format {
        /** ISO-8601 duration, e.g. {@code "PT2H3M4S"}, {@code "P1Y2M3DT4H"}. */
        ISO8601,
        /** Clock time, e.g. {@code "02:03:04"} ({@code [-]H:MM:SS}). */
        CLOCK
    }

    private final Format format;

    public IntervalStringEncoding(Format format) {
        this.format = Objects.requireNonNull(format);
    }

    public static IntervalStringEncoding parse(String name) {
        return new IntervalStringEncoding(Format.valueOf(name.substring(PREFIX.length())));
    }

    public Format getFormat() {
        return format;
    }

    @Override
    public TypeFamily family() {
        return CoreTypeFamily.INTERVAL;
    }

    @JsonValue
    @Override
    public String name() {
        return PREFIX + format.name();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof IntervalStringEncoding other && format == other.format;
    }

    @Override
    public int hashCode() {
        return format.hashCode();
    }

    @Override
    public String toString() {
        return name();
    }
}
