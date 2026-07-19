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

/**
 * Data type formatting functions, mirroring the PostgreSQL "Data Type
 * Formatting" chapter. Format masks are passed through to the dialect —
 * they are not translated between dialects.
 */
public final class FormattingFunctions {

    private FormattingFunctions() {
    }

    public static void register(FunctionRegistry r) {
        r.register(def("to_char", ReturnTypes.TEXT).args(arg("value", Families.ANY), arg("format", CoreTypeFamily.TEXT))
                .doc("Formats a number, date or timestamp using the dialect-native *format* mask.")
                .example("to_char(o.order_date, 'YYYY-MM')"));
        r.register(def("to_number", ReturnTypes.DECIMAL).args(arg("value", CoreTypeFamily.TEXT), arg("format", CoreTypeFamily.TEXT))
                .doc("Parses a string into a number using the dialect-native *format* mask.")
                .example("to_number(c.postal_code, '99999')"));
    }

    private static FunctionDefinition def(String name, ReturnTypeInference type) {
        return new FunctionDefinition(name, type).category(FunctionCategory.FORMATTING);
    }
}
