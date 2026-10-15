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
import static ai.koryki.iql.functions.FunctionArg.optionalArg;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.Families;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.LambdaSpec;
import ai.koryki.iql.functions.ReturnTypes;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
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
        all.addAll(shapeDefinitions());
        all.addAll(buildDefinitions());
        return all;
    }

    /** A call of one of the functions of this class with the arguments as expressions. */
    private static Expression call(String name, Expression... arguments) {
        Function function = new Function();
        function.setFunc(name);
        function.getArguments().addAll(List.of(arguments));
        Expression expression = new Expression();
        expression.setFunction(function);
        return expression;
    }

    /**
     * {@code list_any} and {@code list_all} on top of what a dialect already has: the elements that
     * pass the test, counted, against none, or against all of the elements. So they work wherever
     * {@code list_filter} and {@code list_size} do, with the same lambda; a NULL answer of the test
     * does not pass.
     */
    private static String over(
            boolean all, SqlSelectRenderer renderer, Function function, int indent) {
        Expression list = function.getArguments().get(0);
        Expression lambda = function.getArguments().get(1);
        String passing =
                renderer.toSql(call("list_size", call("list_filter", list, lambda)), indent);
        return all
                ? "(" + passing + " = " + renderer.toSql(call("list_size", list), indent) + ")"
                : "(" + passing + " > 0)";
    }

    /**
     * Whether a call of {@code list_sort} asks for the descending order: its second argument is the
     * text {@code 'asc'} (also when it is left out) or {@code 'desc'}.
     */
    public static boolean descending(Function function) {
        if (function.getArguments().size() < 2) {
            return false;
        }
        String text = function.getArguments().get(1).getText();
        String order =
                text == null
                        ? null
                        : text.replaceAll("^'|'$", "").trim().toLowerCase(java.util.Locale.ROOT);
        if ("asc".equals(order)) {
            return false;
        } else if ("desc".equals(order)) {
            return true;
        }
        throw new KorykiaiException("the order of list_sort is the text 'asc' or 'desc'");
    }

    /**
     * Sorting, without duplicates, and joining: the lists they answer are lists again. NULL
     * elements come last in either order, {@code list_distinct} drops them and gives its elements
     * in ascending order (a set has none, but a result that differs from run to run is no result),
     * {@code list_concat} counts a NULL list as an empty one, and is NULL only when both are.
     */
    private static List<FunctionDefinition> shapeDefinitions() {
        return List.of(
                new FunctionDefinition("list_any", ReturnTypes.BOOLEAN) {
                    @Override
                    protected String renderBody(
                            SqlSelectRenderer renderer, Function function, int indent) {
                        return over(false, renderer, function, indent);
                    }
                }.args(
                                arg("list", CoreTypeFamily.ARRAY, "the list"),
                                arg(
                                        "function",
                                        Families.LAMBDA,
                                        "the test an element can pass: x -> ..."))
                        .lambda(new LambdaSpec(1, 1, 1, types -> oneElement(types), true))
                        .category(FunctionCategory.LAMBDA)
                        .doc("Whether at least one element of the list passes the test."),
                new FunctionDefinition("list_all", ReturnTypes.BOOLEAN) {
                    @Override
                    protected String renderBody(
                            SqlSelectRenderer renderer, Function function, int indent) {
                        return over(true, renderer, function, indent);
                    }
                }.args(
                                arg("list", CoreTypeFamily.ARRAY, "the list"),
                                arg(
                                        "function",
                                        Families.LAMBDA,
                                        "the test every element has to pass: x -> ..."))
                        .lambda(new LambdaSpec(1, 1, 1, types -> oneElement(types), true))
                        .category(FunctionCategory.LAMBDA)
                        .doc(
                                "Whether every element of the list passes the test; true for an empty list."),
                new FunctionDefinition("list_sort", ReturnTypes.LIST_OF_ELEMENT) {
                    @Override
                    protected String renderBody(
                            SqlSelectRenderer renderer, Function function, int indent) {
                        return "list_sort("
                                + renderer.toSql(function.getArguments().get(0), indent)
                                + ", '"
                                + (descending(function) ? "DESC" : "ASC")
                                + "', 'NULLS LAST')";
                    }
                }.args(
                                arg("list", CoreTypeFamily.ARRAY, "the list"),
                                optionalArg(
                                        "order",
                                        CoreTypeFamily.TEXT,
                                        "'asc' (the default) or 'desc'"))
                        .category(FunctionCategory.LIST)
                        .doc("The list in order, NULL elements last."),
                new FunctionDefinition("list_distinct", ReturnTypes.LIST_OF_ELEMENT)
                        .args(arg("list", CoreTypeFamily.ARRAY, "the list"))
                        .template("list_sort(list_distinct({0}), 'ASC', 'NULLS LAST')")
                        .category(FunctionCategory.LIST)
                        .doc(
                                "The elements of the list without duplicates and without NULLs, in ascending order."),
                new FunctionDefinition("list_concat", ReturnTypes.LIST_OF_ELEMENT)
                        .args(
                                arg("list", CoreTypeFamily.ARRAY, "the first list"),
                                arg("other", CoreTypeFamily.ARRAY, "the list that follows"))
                        .template(
                                "CASE WHEN {0} IS NULL AND {1} IS NULL THEN NULL ELSE list_concat({0}, {1}) END")
                        .category(FunctionCategory.LIST)
                        .doc(
                                "The first list followed by the second. A NULL list counts as an empty one; the answer is NULL only when both are."));
    }

    /**
     * Making a list: {@code list(a, b, ...)} out of values, {@code list_agg(x)} out of the rows of
     * a group. The aggregate leaves the NULL values out and gives them in no order that is
     * promised, so a result that is to be compared is sorted: {@code list_sort(list_agg(x))}.
     */
    private static List<FunctionDefinition> buildDefinitions() {
        return List.of(
                new FunctionDefinition("list", ReturnTypes.LIST_OF_COMMON)
                        .variadic(arg("value", Families.ANY, "the elements, one or more"))
                        .template("list_value({*})")
                        .category(FunctionCategory.LIST)
                        .doc("A list of the values, in the order they are written."),
                new FunctionDefinition(
                                "list_agg",
                                ReturnTypes.LIST_OF_ARG0,
                                ai.koryki.iql.functions.FunctionKind.AGGREGATE)
                        .args(arg("value", Families.ANY, "the values to collect"))
                        .template("list({0}) FILTER (WHERE {0} IS NOT NULL)")
                        .category(FunctionCategory.AGGREGATE)
                        .doc(
                                "Collects the values of the group into a list, NULL values left out. The order is **unspecified**: sort the list to compare it."));
    }

    private static List<TypeDescriptor> oneElement(List<TypeDescriptor> types) {
        TypeDescriptor e = element(types);
        return e == null ? null : Arrays.asList(e);
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
                        .category(FunctionCategory.LIST)
                        .doc("The number of elements of the list, NULL elements included."),
                new FunctionDefinition("list_contains", ReturnTypes.BOOLEAN)
                        .args(
                                arg("list", CoreTypeFamily.ARRAY, "the list"),
                                arg("value", Families.ANY, "the value to look for"))
                        .template("list_contains({0}, {1})")
                        .category(FunctionCategory.LIST)
                        .doc("Whether the list has an element equal to the value."),
                new FunctionDefinition("list_sum", ReturnTypes.ELEMENT_OF_LIST)
                        .args(arg("list", CoreTypeFamily.ARRAY, "the list of numbers"))
                        .template("list_sum({0})")
                        .category(FunctionCategory.LIST)
                        .doc("The sum of the elements, NULL elements ignored."),
                new FunctionDefinition("list_min", ReturnTypes.ELEMENT_OF_LIST)
                        .args(arg("list", CoreTypeFamily.ARRAY, "the list"))
                        .template("list_min({0})")
                        .category(FunctionCategory.LIST)
                        .doc("The smallest element, NULL elements ignored."),
                new FunctionDefinition("list_max", ReturnTypes.ELEMENT_OF_LIST)
                        .args(arg("list", CoreTypeFamily.ARRAY, "the list"))
                        .template("list_max({0})")
                        .category(FunctionCategory.LIST)
                        .doc("The largest element, NULL elements ignored."),
                new FunctionDefinition("list_avg", ReturnTypes.DOUBLE)
                        .args(arg("list", CoreTypeFamily.ARRAY, "the list of numbers"))
                        .template("list_avg({0})")
                        .category(FunctionCategory.LIST)
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
                        .category(FunctionCategory.LAMBDA)
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
                        .category(FunctionCategory.LAMBDA)
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
                        .category(FunctionCategory.LAMBDA)
                        .doc(
                                "The elements of the list combined into one value, from the first element on."));
    }
}
