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
 * What a function says about a lambda it takes at one argument position: how many parameters, of
 * what types, and whether the body is a value or a predicate.
 *
 * @param position the argument position of the lambda
 * @param minParameters fewest parameters the lambda may declare
 * @param maxParameters most parameters it may declare
 * @param parameterTypes the types of the parameters, in order, from the types of the other
 *     arguments (the lambda's own position is null in that list); only the first as many as the
 *     lambda declares are used. Null for a list that cannot be typed, an argument of the wrong type
 *     for one, which the family check of that argument reports.
 * @param predicate whether the body has to be a yes/no test
 */
public record LambdaSpec(
        int position,
        int minParameters,
        int maxParameters,
        ParameterTypes parameterTypes,
        boolean predicate) {

    @FunctionalInterface
    public interface ParameterTypes {
        List<TypeDescriptor> of(List<TypeDescriptor> argumentTypes);
    }
}
