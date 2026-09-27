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
import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.functions.FunctionArg;
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionKind;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.FunctionSignature;
import ai.koryki.iql.functions.ReturnTypeInference;
import ai.koryki.iql.functions.ReturnTypes;
import ai.koryki.iql.query.Function;

/** Aggregate functions; their kind drives GROUP BY / HAVING inference. */
public final class AggregateFunctions {

    private AggregateFunctions() {}

    /**
     * The way out for the three dialects that cannot count distinct combinations at all. Stated
     * once so oracle, mssql and sqlite say the same thing.
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
        r.register(
                new FunctionDefinition("count", ReturnTypes.BIGINT, FunctionKind.AGGREGATE) {
                    @Override
                    protected String renderBody(
                            SqlSelectRenderer renderer, Function function, int indent) {
                        var args = function.getArguments();
                        return args.isEmpty()
                                ? "COUNT(*)"
                                : "count(" + renderer.toSql(args.get(0), indent) + ")";
                    }
                }.category(FunctionCategory.AGGREGATE)
                        .signature(
                                FunctionSignature.of(
                                        FunctionArg.optionalArg(
                                                "value",
                                                Families.ANY,
                                                "expression whose non-null values are counted; omit to count all rows")))
                        .doc(
                                "Number of input rows, or of non-null values when an expression is given."));
        r.register(
                def("count_distinct", ReturnTypes.BIGINT)
                        .args(
                                arg(
                                        "value",
                                        Families.ANY,
                                        "the values whose distinct occurrences are counted"))
                        .template("COUNT(DISTINCT {0})")
                        .doc(
                                "Number of distinct non-null values — how many *different* customers, say, "
                                        + "rather than how many rows."));
        // Several values: how many distinct *combinations*. Needed because an entity's identity can
        // be several columns -- counting distinct order_details by order_id alone answered 830
        // where
        // the truth is 2155, silently, because every detail of one order collapsed into one.
        //
        // A second overload rather than making the one above variadic: with a single argument the
        // rendering must stay COUNT(DISTINCT x) to the character, or every existing count_distinct
        // golden across eight dialects moves for no reason.
        //
        // The row-constructor form is the default and not the majority: measured, duckdb,
        // postgresql
        // and trino take it; mariadb and snowflake want a plain comma list and override below;
        // oracle, mssql and sqlite have neither and declare it unsupported.
        r.register(
                def("count_distinct", ReturnTypes.BIGINT)
                        .variadic(
                                arg(
                                        "value",
                                        Families.ANY,
                                        "the values whose distinct combinations are counted"),
                                arg(
                                        "more",
                                        Families.ANY,
                                        "a further value forming part of the combination"))
                        .template("COUNT(DISTINCT ({*}))")
                        .doc(
                                "Number of distinct combinations of the given values — how many *different* "
                                        + "order lines, say, when a line is identified by order and product together."));
        r.register(
                def("avg", ReturnTypes.FLOAT)
                        .args(arg("value", Families.ADDITIVE, "the numeric values to average"))
                        .doc("Average of the input values."));
        r.register(
                def("sum", ReturnTypes.ARG0)
                        .args(arg("value", Families.ADDITIVE, "the values to add together"))
                        .doc("Sum of the input values."));
        r.register(
                def("min", ReturnTypes.ARG0)
                        .args(arg("value", Families.ANY, "the values to take the minimum of"))
                        .doc("Minimum input value."));
        r.register(
                def("max", ReturnTypes.ARG0)
                        .args(arg("value", Families.ANY, "the values to take the maximum of"))
                        .doc("Maximum input value."));
        r.register(
                def("string_agg", ReturnTypes.TEXT)
                        .args(
                                arg("value", Families.ANY, "the values to concatenate"),
                                arg(
                                        "separator",
                                        CoreTypeFamily.TEXT,
                                        "text placed between consecutive values"))
                        .doc(
                                "Concatenates non-null input values into a string, separated by *separator*. "
                                        + "The order is **unspecified**: no engine promises one for an aggregate "
                                        + "without an explicit sort, so the same query may answer differently on "
                                        + "another dialect, another plan, or another run. Pass *order_by* to fix it."));
        // The sorted form is a separate overload rather than an optional argument, because every
        // engine spells the sort differently and a template cannot leave {2} out.
        r.register(
                def("string_agg", ReturnTypes.TEXT)
                        .args(
                                arg("value", Families.ANY, "the values to concatenate"),
                                arg(
                                        "separator",
                                        CoreTypeFamily.TEXT,
                                        "text placed between consecutive values"),
                                arg(
                                        "order_by",
                                        Families.ANY,
                                        "the expression the values are sorted by"))
                        .template("string_agg({0}, {1} ORDER BY {2})")
                        .doc(
                                "Concatenates non-null input values into a string, separated by *separator*, "
                                        + "in ascending order of *order_by*. The two-argument form leaves the order "
                                        + "to the engine."));
        // median, quantile_cont, quantile_disc — measured 2026-09-27 against DuckDB, PostgreSQL,
        // MariaDB, Oracle, SQL Server, SQLite and Trino directly; Snowflake from its own published
        // reference. All three are the SQL-standard ordered-set aggregates -- MEDIAN(x) is the
        // special case PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY x) -- and the WITHIN GROUP form
        // is what the default template uses for the other two, because it renders unchanged on
        // DuckDB, PostgreSQL, Oracle and (by documentation) Snowflake: no override needed for any
        // of
        // the three on any of those four.
        //
        // Two dialects still need one apiece:
        //   - PostgreSQL has no native MEDIAN and overrides it to PERCENTILE_CONT(0.5) WITHIN
        //     GROUP -- exact, not an approximation, and measured equal to Oracle's native MEDIAN on
        //     the same input.
        //   - MariaDB, SQLite, Trino and SQL Server decline all three: MariaDB and SQLite have no
        //     ordered-set aggregate syntax at all and reject WITHIN GROUP outright; Trino's parser
        //     does the same; SQL Server accepts PERCENTILE_CONT/PERCENTILE_DISC only as window
        //     functions with an OVER clause, never as a plain GROUP BY aggregate, which is the only
        //     form this catalog renders them in.
        r.register(
                def("median", ReturnTypes.FLOAT)
                        .args(arg("value", Families.NUMERIC, "the numbers to take the middle of"))
                        .template("MEDIAN({0})")
                        .doc(
                                "Middle value of the inputs once sorted -- the average of the two middle "
                                        + "values when there is an even number of them, so the result need not be "
                                        + "one of the inputs. Prefer it to `avg` for a value skewed by outliers: a "
                                        + "few near-zero denominators drag the average away but move the median "
                                        + "only if they are actually in the middle."));
        r.register(
                def("quantile_cont", ReturnTypes.FLOAT)
                        .args(
                                arg(
                                        "fraction",
                                        Families.NUMERIC,
                                        "where in the sorted values to read, from 0 (the first) to 1 (the last); "
                                                + "0.5 is the median"),
                                arg(
                                        "value",
                                        Families.NUMERIC,
                                        "the numbers to take the quantile of"))
                        .template("PERCENTILE_CONT({0}) WITHIN GROUP (ORDER BY {1})")
                        .doc(
                                "The value at *fraction* of the way through the sorted inputs, interpolating "
                                        + "between the two nearest when it falls between them -- so the result need "
                                        + "not be one of the inputs. `quantile_cont(0.5, x)` is `median(x)`; "
                                        + "`quantile_cont(0.25, x)` the lower quartile."));
        r.register(
                def("quantile_disc", ReturnTypes.ARG1)
                        .args(
                                arg(
                                        "fraction",
                                        Families.NUMERIC,
                                        "where in the sorted values to read, from 0 (the first) to 1 (the last)"),
                                arg(
                                        "value",
                                        Families.ORDERED,
                                        "the values to take the quantile of"))
                        .template("PERCENTILE_DISC({0}) WITHIN GROUP (ORDER BY {1})")
                        .doc(
                                "Like `quantile_cont`, but never interpolates: the result is always one of the "
                                        + "input values, the one that sits at or just past *fraction* of the way "
                                        + "through. Works on any value that can be ordered, not only numbers -- a "
                                        + "quantile of dates, say -- which `quantile_cont` cannot do."));
    }

    private static FunctionDefinition def(String name, ReturnTypeInference type) {
        return new FunctionDefinition(name, type, FunctionKind.AGGREGATE)
                .category(FunctionCategory.AGGREGATE);
    }
}
