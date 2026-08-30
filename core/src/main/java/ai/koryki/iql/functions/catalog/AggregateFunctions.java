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

import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.Families;
import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.query.Function;
import ai.koryki.iql.functions.FunctionArg;
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionKind;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.FunctionSignature;
import ai.koryki.iql.functions.ReturnTypeInference;
import ai.koryki.iql.functions.ReturnTypes;

import static ai.koryki.iql.functions.FunctionArg.arg;

/** Aggregate functions; their kind drives GROUP BY / HAVING inference. */
public final class AggregateFunctions {

    private AggregateFunctions() {
    }

    /**
     * The way out for the three dialects that cannot count distinct combinations at all. Stated once
     * so oracle, mssql and sqlite say the same thing.
     *
     * <p>The separator is the author's decision and not koryki's, which is why it is advice and not
     * a rendering: no character can be guaranteed absent from the data, and choosing one silently
     * would put the very error back that this rejection exists to prevent.
     */
    public static final String COUNT_DISTINCT_HINT =
            "count distinct combinations with count_distinct(concat(a, <separator>, b)), and pick a "
                    + "separator that cannot occur in the values — without one, (1, 23) and (12, 3) "
                    + "are the same combination";

    public static void register(FunctionRegistry r) {
        // Without an argument the result must be COUNT(*), not count(). Only duckdb, sqlite and
        // trino accept the latter; postgresql, mssql, mariadb, oracle and snowflake reject it
        // ("count(*) must be used to call a parameterless aggregate function"). That of all
        // dialects the reference one is among the three permissive ones had hidden it.
        r.register(new FunctionDefinition("count", ReturnTypes.BIGINT, FunctionKind.AGGREGATE) {
            @Override
            protected String renderBody(SqlSelectRenderer renderer, Function function, int indent) {
                var args = function.getArguments();
                return args.isEmpty() ? "COUNT(*)"
                        : "count(" + renderer.toSql(args.get(0), indent) + ")";
            }
        }.category(FunctionCategory.AGGREGATE)
                .signature(FunctionSignature.of(FunctionArg.optionalArg("value", Families.ANY,
                        "expression whose non-null values are counted; omit to count all rows")))
                .doc("Number of input rows, or of non-null values when an expression is given."));
        r.register(def("count_distinct", ReturnTypes.BIGINT)
                .args(arg("value", Families.ANY, "the values whose distinct occurrences are counted"))
                .template("COUNT(DISTINCT {0})")
                .doc("Number of distinct non-null values — how many *different* customers, say, "
                        + "rather than how many rows."));
        // Several values: how many distinct *combinations*. Needed because an entity's identity can
        // be several columns -- counting distinct order_details by order_id alone answered 830 where
        // the truth is 2155, silently, because every detail of one order collapsed into one.
        //
        // A second overload rather than making the one above variadic: with a single argument the
        // rendering must stay COUNT(DISTINCT x) to the character, or every existing count_distinct
        // golden across eight dialects moves for no reason.
        //
        // The row-constructor form is the default and not the majority: measured, duckdb, postgresql
        // and trino take it; mariadb and snowflake want a plain comma list and override below;
        // oracle, mssql and sqlite have neither and declare it unsupported.
        r.register(def("count_distinct", ReturnTypes.BIGINT)
                .variadic(arg("value", Families.ANY, "the values whose distinct combinations are counted"),
                        arg("more", Families.ANY, "a further value forming part of the combination"))
                .template("COUNT(DISTINCT ({*}))")
                .doc("Number of distinct combinations of the given values — how many *different* "
                        + "order lines, say, when a line is identified by order and product together."));
        r.register(def("avg",   ReturnTypes.FLOAT).args(arg("value", Families.ADDITIVE, "the numeric values to average")).doc("Average of the input values."));
        r.register(def("sum",   ReturnTypes.ARG0).args(arg("value", Families.ADDITIVE, "the values to add together")).doc("Sum of the input values."));
        r.register(def("min",   ReturnTypes.ARG0).args(arg("value", Families.ANY, "the values to take the minimum of")).doc("Minimum input value."));
        r.register(def("max",   ReturnTypes.ARG0).args(arg("value", Families.ANY, "the values to take the maximum of")).doc("Maximum input value."));
        r.register(def("string_agg", ReturnTypes.TEXT)
                .args(arg("value", Families.ANY, "the values to concatenate"),
                        arg("separator", CoreTypeFamily.TEXT, "text placed between consecutive values"))
                .doc("Concatenates non-null input values into a string, separated by *separator*. "
                        + "The order is **unspecified**: no engine promises one for an aggregate "
                        + "without an explicit sort, so the same query may answer differently on "
                        + "another dialect, another plan, or another run. Pass *order_by* to fix it."));
        // The sorted form is a separate overload rather than an optional argument, because every
        // engine spells the sort differently and a template cannot leave {2} out.
        r.register(def("string_agg", ReturnTypes.TEXT)
                .args(arg("value", Families.ANY, "the values to concatenate"),
                        arg("separator", CoreTypeFamily.TEXT, "text placed between consecutive values"),
                        arg("order_by", Families.ANY, "the expression the values are sorted by"))
                .template("string_agg({0}, {1} ORDER BY {2})")
                .doc("Concatenates non-null input values into a string, separated by *separator*, "
                        + "in ascending order of *order_by*. The two-argument form leaves the order "
                        + "to the engine."));
    }

    private static FunctionDefinition def(String name, ReturnTypeInference type) {
        return new FunctionDefinition(name, type, FunctionKind.AGGREGATE)
                .category(FunctionCategory.AGGREGATE);
    }
}
