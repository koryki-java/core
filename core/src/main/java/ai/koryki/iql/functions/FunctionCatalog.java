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

import ai.koryki.catalog.schema.types.TypeDescriptor;

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
}
