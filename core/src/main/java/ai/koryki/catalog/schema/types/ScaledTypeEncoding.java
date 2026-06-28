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

/**
 * Storage declaration "fixed-point": an exact numeric (typically money) stored
 * as an integer of minor units, with a fixed <em>scale</em> applied on read —
 * e.g. cents stored as BIGINT under {@code SCALED:2}, so {@code 1299} reads as
 * {@code 12.99}.
 *
 * <p>Declared in the schema as {@code "SCALED:<scale>"}; parsed and cached by
 * {@link TypeEncodingRegistry}.
 */
public final class ScaledTypeEncoding implements TypeEncoding {

    public static final String PREFIX = "SCALED:";

    private final int scale;

    public ScaledTypeEncoding(int scale) {
        this.scale = scale;
    }

    public static ScaledTypeEncoding parse(String name) {
        return new ScaledTypeEncoding(Integer.parseInt(name.substring(PREFIX.length())));
    }

    public int getScale() {
        return scale;
    }

    @Override
    public TypeFamily family() {
        return CoreTypeFamily.DECIMAL;
    }

    @JsonValue
    @Override
    public String name() {
        return PREFIX + scale;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ScaledTypeEncoding other && scale == other.scale;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(scale);
    }

    @Override
    public String toString() {
        return name();
    }
}
