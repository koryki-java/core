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

import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
import ai.koryki.catalog.types.TypeDescriptor;

import java.util.HashMap;
import java.util.Map;

public class FunctionBinding {

    private final Function function;
    private final java.util.function.Function<Expression, TypeDescriptor> resolver;
    private final Map<Integer, TypeDescriptor> cache = new HashMap<>();

    public FunctionBinding(Function function, java.util.function.Function<Expression, TypeDescriptor> resolver) {
        this.function = function;
        this.resolver = resolver;
    }

    public TypeDescriptor getOperandType(int i) {
        return cache.computeIfAbsent(i, idx -> resolver.apply(function.getArguments().get(idx)));
    }

    public int getOperandCount() {
        return function.getArguments().size();
    }

    public Function getFunction() {
        return function;
    }
}
