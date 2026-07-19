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

    public static void register(FunctionRegistry r) {
        r.register(def("count", ReturnTypes.BIGINT)
                .signature(FunctionSignature.of(FunctionArg.optionalArg("value", Families.ANY)))
                .doc("Number of input rows or non-null values.")
                .example("count(o.order_id)"));
        r.register(def("avg",   ReturnTypes.FLOAT).args(arg("value", Families.ADDITIVE)).doc("Average of the input values.")
                .example("avg(od.unit_price)"));
        r.register(def("sum",   ReturnTypes.ARG0).args(arg("value", Families.ADDITIVE)).doc("Sum of the input values.")
                .example("sum(od.quantity)"));
        r.register(def("min",   ReturnTypes.ARG0).args(arg("value", Families.ANY)).doc("Minimum input value.")
                .example("min(o.order_date)"));
        r.register(def("max",   ReturnTypes.ARG0).args(arg("value", Families.ANY)).doc("Maximum input value.")
                .example("max(o.order_date)"));
        r.register(def("string_agg", ReturnTypes.TEXT)
                .args(arg("value", Families.ANY), arg("separator", CoreTypeFamily.TEXT))
                .doc("Concatenates non-null input values into a string, separated by *separator*.")
                .example("string_agg(p.product_name, ', ')"));
    }

    private static FunctionDefinition def(String name, ReturnTypeInference type) {
        return new FunctionDefinition(name, type, FunctionKind.AGGREGATE)
                .category(FunctionCategory.AGGREGATE);
    }
}
