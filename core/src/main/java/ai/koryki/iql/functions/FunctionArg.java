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

import ai.koryki.catalog.schema.types.TypeFamily;

/**
 * One declared argument of a function signature.
 *
 * @param family      expected type family; {@code null} means any type is accepted
 *                    (also the wildcard for arguments whose type cannot be resolved,
 *                    such as NULL literals)
 * @param optional    whether the argument may be omitted (optionals must be trailing)
 * @param description optional per-argument prose for the generated docs; {@code null}
 *                    when only the name and type are documented
 */
public record FunctionArg(String name, TypeFamily family, boolean optional, String description) {

    public static FunctionArg arg(String name) {
        return new FunctionArg(name, null, false, null);
    }

    public static FunctionArg arg(String name, TypeFamily family) {
        return new FunctionArg(name, family, false, null);
    }

    public static FunctionArg arg(String name, TypeFamily family, String description) {
        return new FunctionArg(name, family, false, description);
    }

    public static FunctionArg optionalArg(String name) {
        return new FunctionArg(name, null, true, null);
    }

    public static FunctionArg optionalArg(String name, TypeFamily family) {
        return new FunctionArg(name, family, true, null);
    }

    public static FunctionArg optionalArg(String name, TypeFamily family, String description) {
        return new FunctionArg(name, family, true, description);
    }
}
