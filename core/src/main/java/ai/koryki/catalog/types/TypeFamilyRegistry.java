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

public final class TypeFamilyRegistry {

    private static final Map<String, TypeFamily> REGISTRY = new ConcurrentHashMap<>();

    static {
        for (CoreTypeFamily f : CoreTypeFamily.values()) {
            REGISTRY.put(f.name(), f);
        }
    }

    private TypeFamilyRegistry() {}

    public static void register(TypeFamily family) {
        REGISTRY.put(family.name(), family);
    }

    public static TypeFamily of(String name) {
        TypeFamily family = REGISTRY.get(name);
        if (family == null) throw new IllegalArgumentException("Unknown TypeFamily: " + name);
        return family;
    }
}
