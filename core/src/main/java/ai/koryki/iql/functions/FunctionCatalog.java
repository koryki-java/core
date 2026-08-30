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
package ai.koryki.iql.functions;

import ai.koryki.catalog.types.TypeDescriptor;

import java.util.List;

/**
 * The type-facing role of the function catalog: return-type inference and
 * overload lookup, with <em>no</em> dependency on SQL rendering. The
 * type-resolution layer ({@code ExpressionTypeResolver}) and validation
 * ({@code FunctionValidator}) depend only on this, so they stay independent of
 * the renderer. {@link FunctionRenderer} extends this with the rendering role;
 * {@code FunctionRegistry} implements both.
 */
public interface FunctionCatalog {

    TypeDescriptor descriptor(FunctionBinding binding);

    /** Overload set of a function name; empty = unknown to this catalog (validation skips it). */
    default List<FunctionDefinition> overloads(String name) {
        return List.of();
    }

    /**
     * Every function name that can be <em>written as a call</em>; empty = no suggestions from here.
     *
     * <p>Calls only, which is to say {@link Fixity#PREFIX}. The catalog holds operators in the same
     * tables — {@code =}, {@code AND}, {@code BETWEEN}, {@code IS NULL}, unary minus — because
     * unifying them is what lets one signature check serve both. But an operator is not a name
     * anyone can mistype into {@code upper(text)}: offering {@code IN} for a misspelt {@code min}
     * would propose something that cannot be written in that position at all.
     *
     * <p>Default empty rather than abstract, for the same reason {@link #overloads} is: a catalog
     * that does not know its own inventory should make no suggestion, not fail to compile.
     */
    default List<String> names() {
        return List.of();
    }
}
