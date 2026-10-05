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
package ai.koryki.iql.functions.catalog;

import static ai.koryki.iql.functions.FunctionArg.arg;

import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.Families;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.LambdaSpec;
import ai.koryki.iql.functions.ReturnTypes;
import java.util.Arrays;
import java.util.List;

/**
 * Functions that take a list and a lambda: {@code list_transform(c.tags, x -> upper(@x))}, {@code
 * list_filter(c.scores, x -> @x > 10)}, {@code list_reduce(c.scores, (a, b) -> @a + @b)}.
 *
 * <p>Registered unsupported in the standard catalog and supported again by the dialects that have
 * lambdas, so that every other dialect rejects them with a violation instead of sending a call its
 * engine does not know.
 */
public final class ListFunctions {

    private ListFunctions() {}

    private static final String HINT = "lambdas on lists are supported on DuckDB so far";

    public static void register(FunctionRegistry r) {
        register(r, false);
    }

    /**
     * @param supported false marks every function rejected, true replaces that by the working
     *     definition: registering one over the other replaces it, their signatures are the same
     */
    public static void register(FunctionRegistry r, boolean supported) {
        for (FunctionDefinition d : definitions()) {
            r.register(supported ? d : d.unsupported().unsupportedHint(HINT));
        }
    }

    private static TypeDescriptor element(List<TypeDescriptor> argumentTypes) {
        TypeDescriptor list = argumentTypes.get(0);
        return list != null && CoreTypeFamily.ARRAY.equals(list.getTypeFamily())
                ? list.element()
                : null;
    }

    private static List<FunctionDefinition> definitions() {
        return List.of(
                new FunctionDefinition("list_transform", ReturnTypes.LIST_OF_LAMBDA)
                        .args(
                                arg("list", CoreTypeFamily.ARRAY, "the list"),
                                arg(
                                        "function",
                                        Families.LAMBDA,
                                        "what is done to each element: x -> ..., or (x, i) -> ... with the position"))
                        .lambda(
                                new LambdaSpec(
                                        1,
                                        1,
                                        2,
                                        types -> {
                                            TypeDescriptor e = element(types);
                                            return e == null
                                                    ? null
                                                    : Arrays.asList(e, TypeDescriptor.INTEGER);
                                        },
                                        false))
                        .category(FunctionCategory.OTHER)
                        .doc("A list of what the function returns for each element of the list."),
                new FunctionDefinition("list_filter", ReturnTypes.LIST_OF_ELEMENT)
                        .args(
                                arg("list", CoreTypeFamily.ARRAY, "the list"),
                                arg(
                                        "function",
                                        Families.LAMBDA,
                                        "the test an element has to pass: x -> ..., or (x, i) -> ..."))
                        .lambda(
                                new LambdaSpec(
                                        1,
                                        1,
                                        2,
                                        types -> {
                                            TypeDescriptor e = element(types);
                                            return e == null
                                                    ? null
                                                    : Arrays.asList(e, TypeDescriptor.INTEGER);
                                        },
                                        true))
                        .category(FunctionCategory.OTHER)
                        .doc("The elements of the list for which the function is true, in order."),
                new FunctionDefinition("list_reduce", ReturnTypes.LAMBDA)
                        .args(
                                arg("list", CoreTypeFamily.ARRAY, "the list"),
                                arg(
                                        "function",
                                        Families.LAMBDA,
                                        "combines the result so far with the next element: (a, x) -> ..."))
                        .lambda(
                                new LambdaSpec(
                                        1,
                                        2,
                                        2,
                                        types -> {
                                            TypeDescriptor e = element(types);
                                            return e == null ? null : Arrays.asList(e, e);
                                        },
                                        false))
                        .category(FunctionCategory.OTHER)
                        .doc(
                                "The elements of the list combined into one value, from the first element on."));
    }
}
