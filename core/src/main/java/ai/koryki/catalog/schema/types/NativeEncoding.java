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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The "no extra type information" encoding: a value stored in its family's natural
 * physical type (a plain INTEGER, VARCHAR, DATE, native INTERVAL, …). Output type
 * descriptors carry {@code NATIVE} in place of a null encoding so the encoding field is
 * always meaningful — never "unknown/unresolved" — at output time. A schema ({@code db.json})
 * may leave the encoding {@code null}, which is interpreted as NATIVE.
 *
 * <p>Bound to exactly one family like every other {@link TypeEncoding}, so the
 * {@code (family, encoding)} invariant holds; created from a family (never parsed from a
 * name), and serialized as the literal {@code "NATIVE"} (the family travels in its own field).
 */
public final class NativeEncoding implements TypeEncoding {

    /** The single serialized name shared by every family's NATIVE encoding (the family travels separately). */
    public static final String NAME = "NATIVE";

    private static final Map<TypeFamily, NativeEncoding> CACHE = new ConcurrentHashMap<>();

    private final TypeFamily family;

    private NativeEncoding(TypeFamily family) {
        this.family = Objects.requireNonNull(family);
    }

    public static NativeEncoding of(TypeFamily family) {
        return CACHE.computeIfAbsent(family, NativeEncoding::new);
    }

    @Override
    public TypeFamily family() {
        return family;
    }

    @JsonValue
    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NativeEncoding other && family.equals(other.family);
    }

    @Override
    public int hashCode() {
        return family.hashCode();
    }

    @Override
    public String toString() {
        return NAME;
    }
}
