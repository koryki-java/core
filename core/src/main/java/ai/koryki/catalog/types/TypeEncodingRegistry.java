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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TypeEncodingRegistry {

    private static final Map<String, TypeEncoding> REGISTRY = new ConcurrentHashMap<>();

    static {
        for (CoreTypeEncoding e : CoreTypeEncoding.values()) {
            REGISTRY.put(e.name(), e);
        }
    }

    private TypeEncodingRegistry() {}

    public static void register(TypeEncoding encoding) {
        REGISTRY.put(encoding.name(), encoding);
    }

    public static TypeEncoding ofNullable(String name) {
        // An absent encoding (null) or the explicit family-native marker ("NATIVE") both resolve to the
        // per-family NATIVE encoding. The family is not known here, so return null and let the
        // TypeDescriptor constructor fill NATIVE from the column's family.
        if (name == null || NativeEncoding.NAME.equals(name)) return null;
        TypeEncoding enc = REGISTRY.get(name);
        if (enc == null) {
            enc = parseParameterized(name);
            if (enc != null) {
                REGISTRY.put(name, enc);
            }
        }
        if (enc == null) throw new IllegalArgumentException("Unknown TypeEncoding: " + name);
        return enc;
    }

    /** Prefix-parameterized encodings: {@code DATE_WALLCLOCK:<zone>}/{@code TIMESTAMP_WALLCLOCK:<zone>}, {@code EPOCH:<unit>}, {@code DURATION:<unit>}, {@code SCALED:<scale>}. */
    private static TypeEncoding parseParameterized(String name) {
        if (WallClockEncoding.matches(name)) {
            return WallClockEncoding.parse(name);
        }
        if (name.startsWith(EpochTypeEncoding.PREFIX)) {
            return EpochTypeEncoding.parse(name);
        }
        if (name.startsWith(IntervalStringEncoding.PREFIX)) {   // "INTERVAL_FROM_STRING:" before "INTERVAL:"
            return IntervalStringEncoding.parse(name);
        }
        if (name.startsWith(IntervalTypeEncoding.PREFIX)) {
            return IntervalTypeEncoding.parse(name);
        }
        if (name.startsWith(ScaledTypeEncoding.PREFIX)) {
            return ScaledTypeEncoding.parse(name);
        }
        return null;
    }
}
