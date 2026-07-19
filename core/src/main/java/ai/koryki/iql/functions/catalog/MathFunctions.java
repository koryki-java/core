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
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.ReturnTypeInference;
import ai.koryki.iql.functions.ReturnTypes;

import static ai.koryki.iql.functions.FunctionArg.arg;
import static ai.koryki.iql.functions.FunctionArg.optionalArg;

/**
 * Mathematical functions, mirroring the PostgreSQL "Mathematical Functions"
 * chapter. Deliberately functions only — PostgreSQL's operator spellings
 * ({@code %}, {@code |/}, {@code ||/}, {@code ^}) are not exposed in KQL:
 * the audience is business analysts, and Excel-style {@code mod}/{@code sqrt}/
 * {@code power} read better than operator symbols ({@code %} in particular
 * reads as "percent").
 */
public final class MathFunctions {

    private MathFunctions() {
    }

    public static void register(FunctionRegistry r) {
        r.register(def("abs", ReturnTypes.ARG0).args(arg("value", Families.NUMERIC))
                .doc("Absolute value.").example("abs(-17.4)"));
        r.register(def("ceil", ReturnTypes.ARG0).args(arg("value", Families.NUMERIC))
                .doc("Nearest integer greater than or equal to *value*.")
                .example("ceil(o.freight)"));
        r.register(def("ceiling", ReturnTypes.ARG0).args(arg("value", Families.NUMERIC))
                .doc("Nearest integer greater than or equal to *value* (alias of ceil)."));
        r.register(def("floor", ReturnTypes.ARG0).args(arg("value", Families.NUMERIC))
                .doc("Nearest integer less than or equal to *value*.")
                .example("floor(o.freight)"));
        r.register(def("round", ReturnTypes.ARG0).args(arg("value", Families.NUMERIC), optionalArg("scale", CoreTypeFamily.INTEGER))
                .doc("Rounds to *scale* decimal places (default 0).")
                .paragraph("Half-way rounding of binary-float inputs is engine-defined: a value such as "
                        + "`0.15` is stored as `0.149999…`, so engines may round it up or down."
                        + System.lineSeparator()
                        + System.lineSeparator()
                        + "See: [What Every Computer Scientist Should Know About Floating-Point Arithmetic]"
                        + "(https://docs.oracle.com/cd/E19957-01/806-3568/ncg_goldberg.html).")
                .example("round(o.freight, 2)"));
//        r.register(def("trunc", ReturnTypes.ARG0).args(arg("value"), optionalArg("scale"))
//                .doc("Truncates toward zero, to *scale* decimal places (default 0)."));
//
        r.register(def("mod", ReturnTypes.ARG0).args(arg("dividend", Families.NUMERIC), arg("divisor", Families.NUMERIC))
                .doc("Remainder of *dividend* / *divisor*.").example("mod(o.order_id, 7)"));
//        r.register(def("div", ReturnTypes.ARG0).args(arg("dividend"), arg("divisor"))
//                .doc("Integer quotient of *dividend* / *divisor*."));
//        r.register(def("sign", ReturnTypes.INTEGER).args(arg("value"))
//                .doc("Sign of the argument: -1, 0 or 1."));
//
//        r.register(def("greatest", ReturnTypes.LEAST_RESTRICTIVE).variadic(arg("value"))
//                .doc("Largest of the arguments."));
//        r.register(def("least", ReturnTypes.LEAST_RESTRICTIVE).variadic(arg("value"))
//                .doc("Smallest of the arguments."));
//
        r.register(def("power", ReturnTypes.FLOAT).args(arg("base", Families.NUMERIC), arg("exponent", Families.NUMERIC))
                .doc("*base* raised to the power of *exponent*.").example("power(c.value, 2)"));
//        r.register(def("pow", ReturnTypes.FLOAT).args(arg("base"), arg("exponent"))
//                .doc("*base* raised to the power of *exponent* (alias of power)."));
        r.register(def("sqrt", ReturnTypes.FLOAT).args(arg("value", Families.NUMERIC))
                .doc("Square root.")
                .example("sqrt(2)"));
//        r.register(def("cbrt", ReturnTypes.FLOAT).args(arg("value"))
//                .doc("Cube root."));
//        r.register(def("exp", ReturnTypes.FLOAT).args(arg("value"))
//                .doc("Exponential (e raised to *value*)."));
//
//        r.register(def("ln", ReturnTypes.FLOAT).args(arg("value"))
//                .doc("Natural logarithm."));
//        r.register(def("log", ReturnTypes.FLOAT).args(arg("value"), optionalArg("x"))
//                .doc("Base-10 logarithm of *value*, or logarithm of *x* to base *value* with two arguments."));
//        r.register(def("log2", ReturnTypes.FLOAT).args(arg("value"))
//                .doc("Base-2 logarithm."));
//        r.register(def("log10", ReturnTypes.FLOAT).args(arg("value"))
//                .doc("Base-10 logarithm."));
//
//        r.register(def("gcd", ReturnTypes.ARG0).args(arg("a"), arg("b"))
//                .doc("Greatest common divisor."));
//        r.register(def("lcm", ReturnTypes.ARG0).args(arg("a"), arg("b"))
//                .doc("Least common multiple."));
//        r.register(def("factorial", ReturnTypes.BIGINT).args(arg("value"))
//                .doc("Factorial."));
//        r.register(def("width_bucket", ReturnTypes.INTEGER)
//                .args(arg("operand"), arg("low"), arg("high"), arg("count"))
//                .doc("Bucket number of *operand* in an equal-width histogram with *count* buckets from *low* to *high*."));
//
//        for (String name : List.of("sin", "cos", "tan", "asin", "acos", "atan", "cot")) {
//            r.register(def(name, ReturnTypes.FLOAT).args(arg("value"))
//                    .doc("Trigonometric function (argument in radians)."));
//        }
//        r.register(def("atan2", ReturnTypes.FLOAT).args(arg("y"), arg("x"))
//                .doc("Inverse tangent of *y*/*x*."));
//        for (String name : List.of("sinh", "cosh", "tanh", "asinh", "acosh", "atanh")) {
//            r.register(def(name, ReturnTypes.FLOAT).args(arg("value"))
//                    .doc("Hyperbolic function."));
//        }
//        r.register(def("degrees", ReturnTypes.FLOAT).args(arg("value"))
//                .doc("Converts radians to degrees."));
//        r.register(def("radians", ReturnTypes.FLOAT).args(arg("value"))
//                .doc("Converts degrees to radians."));
//
        r.register(def("pi", ReturnTypes.FLOAT).args()
                .doc("Approximate value of π."));
        r.register(def("random", ReturnTypes.FLOAT).args()
                .doc("Random value in the range 0.0 <= x < 1.0."));
    }

    private static FunctionDefinition def(String name, ReturnTypeInference type) {
        return new FunctionDefinition(name, type).category(FunctionCategory.MATH);
    }
}
