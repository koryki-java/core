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

public interface TypeFamily {

    String name();

    /**
     * Whether this <em>declared</em> family accepts a <em>candidate</em> (actual) family. A leaf
     * family accepts only itself; a {@link FamilyGroup} accepts its members. This is the basis of
     * operand-type checking and overload selection — use it instead of {@link Object#equals} so an
     * argument declared as a group (e.g. {@code NUMERIC}) matches any member (INTEGER, DECIMAL,
     * FLOAT).
     */
    default boolean accepts(TypeFamily candidate) {
        return equals(candidate);
    }
}
