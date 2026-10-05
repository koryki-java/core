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
 * Functions that take a list and a lambda: {@code list_transform(c.tags, x -> upper(x))}, {@code
 * list_filter(c.scores, x -> x > 10)}, {@code list_reduce(c.scores, (a, b) -> a + b)}.
 *
 * <p>Registered unsupported in the standard catalog and supported again by the dialects that have
 * lambdas, so that every other dialect rejects them with a violation instead of sending a call its
 * engine does not know.
 */
public final class ListFunctions {

    private ListFunctions() {}

    private static final String HINT = "lists are not supported by this dialect yet";

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

    /**
     * A working definition of one of the list functions, for a dialect that renders it its own way:
     * it sets a template, or overrides {@code renderBody} on a copy of this, and registers it over
     * the rejected one.
     */
    public static FunctionDefinition supported(String name) {
        return definitions().stream()
                .filter(d -> d.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("not a list function: " + name));
    }

    private static TypeDescriptor element(List<TypeDescriptor> argumentTypes) {
        TypeDescriptor list = argumentTypes.get(0);
        return list != null && CoreTypeFamily.ARRAY.equals(list.getTypeFamily())
                ? list.element()
                : null;
    }

    private static List<FunctionDefinition> definitions() {
        List<FunctionDefinition> all = new java.util.ArrayList<>(lambdaDefinitions());
        all.addAll(valueDefinitions());
        return all;
    }

    /**
     * The functions that take a list and answer about it, no lambda: the size, whether it holds a
     * value, and the sum, smallest, largest and mean of its elements. Rendered in DuckDB's form
     * here, which is the canonical one; every other dialect that has lists writes its own.
     *
     * <p>What they promise, on every dialect: a NULL list gives NULL; the aggregates ignore NULL
     * elements and give NULL for a list with none; {@code list_contains} is true or false, never
     * NULL, for a list that is there.
     */
    private static List<FunctionDefinition> valueDefinitions() {
        return List.of(
                new FunctionDefinition("list_size", ReturnTypes.INTEGER)
                        .args(arg("list", CoreTypeFamily.ARRAY, "the list"))
                        .template("len({0})")
                        .category(FunctionCategory.OTHER)
                        .doc("The number of elements of the list, NULL elements included."),
                new FunctionDefinition("list_contains", ReturnTypes.BOOLEAN)
                        .args(
                                arg("list", CoreTypeFamily.ARRAY, "the list"),
                                arg("value", Families.ANY, "the value to look for"))
                        .template("list_contains({0}, {1})")
                        .category(FunctionCategory.OTHER)
                        .doc("Whether the list has an element equal to the value."),
                new FunctionDefinition("list_sum", ReturnTypes.ELEMENT_OF_LIST)
                        .args(arg("list", CoreTypeFamily.ARRAY, "the list of numbers"))
                        .template("list_sum({0})")
                        .category(FunctionCategory.OTHER)
                        .doc("The sum of the elements, NULL elements ignored."),
                new FunctionDefinition("list_min", ReturnTypes.ELEMENT_OF_LIST)
                        .args(arg("list", CoreTypeFamily.ARRAY, "the list"))
                        .template("list_min({0})")
                        .category(FunctionCategory.OTHER)
                        .doc("The smallest element, NULL elements ignored."),
                new FunctionDefinition("list_max", ReturnTypes.ELEMENT_OF_LIST)
                        .args(arg("list", CoreTypeFamily.ARRAY, "the list"))
                        .template("list_max({0})")
                        .category(FunctionCategory.OTHER)
                        .doc("The largest element, NULL elements ignored."),
                new FunctionDefinition("list_avg", ReturnTypes.DOUBLE)
                        .args(arg("list", CoreTypeFamily.ARRAY, "the list of numbers"))
                        .template("list_avg({0})")
                        .category(FunctionCategory.OTHER)
                        .doc(
                                "The mean of the elements as a floating point number, NULL elements ignored."));
    }

    private static List<FunctionDefinition> lambdaDefinitions() {
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
