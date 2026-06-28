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

import ai.koryki.iql.functions.catalog.AggregateFunctions;
import ai.koryki.iql.functions.catalog.ArithmeticFunctions;
import ai.koryki.iql.functions.catalog.ConditionalFunctions;
import ai.koryki.iql.functions.catalog.ConversionFunctions;
import ai.koryki.iql.functions.catalog.DateTimeFunctions;
import ai.koryki.iql.functions.catalog.FormattingFunctions;
import ai.koryki.iql.functions.catalog.MathFunctions;
import ai.koryki.iql.functions.catalog.OperatorFunctions;
import ai.koryki.iql.functions.catalog.PatternMatchingFunctions;
import ai.koryki.iql.functions.catalog.StringFunctions;

/**
 * Facade over the canonical function catalog. The definitions live in
 * {@code ai.koryki.iql.functions.catalog}, one class per documentation
 * category; this class assembles the dialect-neutral registry and answers
 * name-level classification queries.
 */
public class StandardFunctions {

    public static FunctionRegistry registry() {
        FunctionRegistry r = new FunctionRegistry();
        ConversionFunctions.register(r);
        DateTimeFunctions.register(r);
        FormattingFunctions.register(r);
        AggregateFunctions.register(r);
        ArithmeticFunctions.register(r);
        StringFunctions.register(r);
        PatternMatchingFunctions.register(r);
        MathFunctions.register(r);
        ConditionalFunctions.register(r);
        OperatorFunctions.register(r);
        return r;
    }

    /** Standard (dialect-neutral) registry, built once, for classification queries like {@link #isAggregate}. */
    private static final FunctionRegistry CANONICAL = registry();

    /** Whether a function is an aggregate, per the canonical function metadata (single source of truth). */
    public static boolean isAggregate(String name) {
        FunctionDefinition fn = CANONICAL.lookup(name);
        return fn != null && fn.getKind() == FunctionKind.AGGREGATE;
    }

    // Delegates kept for the dialect modules; the definitions live in the catalog classes.

    public static void registerExtractParts(FunctionRegistry r) {
        DateTimeFunctions.registerExtractParts(r);
    }

    public static FunctionDefinition parseTwoArg(String name, ReturnTypeInference type, String sqlFunction) {
        return DateTimeFunctions.parseTwoArg(name, type, sqlFunction);
    }
}
