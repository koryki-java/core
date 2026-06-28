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

import ai.koryki.catalog.schema.types.CoreTypeFamily;
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.ReturnTypeInference;
import ai.koryki.iql.functions.ReturnTypes;

import static ai.koryki.iql.functions.FunctionArg.arg;
import static ai.koryki.iql.functions.FunctionArg.optionalArg;

/**
 * Regular-expression functions, mirroring the PostgreSQL "Pattern Matching"
 * chapter. Functions only — LIKE/SIMILAR TO operator forms are comparison
 * syntax, not catalog functions. Pattern syntax is passed through to the
 * dialect and not translated.
 */
public final class PatternMatchingFunctions {

    private static final CoreTypeFamily TEXT = CoreTypeFamily.TEXT;
    private static final CoreTypeFamily INT = CoreTypeFamily.INTEGER;

    private PatternMatchingFunctions() {
    }

    public static void register(FunctionRegistry r) {
        r.register(def("regexp_like", ReturnTypes.BOOLEAN)
                .args(arg("string", TEXT), arg("pattern", TEXT), optionalArg("flags", TEXT))
                .doc("True if the string matches the regular expression *pattern*.")
                .example("regexp_like(c.company_name, '^A')"));
        r.register(def("regexp_count", ReturnTypes.INTEGER)
                .args(arg("string", TEXT), arg("pattern", TEXT), optionalArg("start", INT))
                .doc("Number of matches of *pattern* in the string.")
                .example("regexp_count(e.notes, 'sales')"));
        r.register(def("regexp_substr", ReturnTypes.TEXT)
                .args(arg("string", TEXT), arg("pattern", TEXT))
                .doc("First substring matching the regular expression *pattern*.")
                .example("regexp_substr(c.phone, '[0-9]+')"));
        r.register(def("regexp_replace", ReturnTypes.TEXT)
                .args(arg("string", TEXT), arg("pattern", TEXT), arg("replacement", TEXT), optionalArg("flags", TEXT))
                .doc("Replaces substrings matching the regular expression *pattern* with *replacement*.")
                .example("regexp_replace(c.phone, '[^0-9]', '')"));
    }

    private static FunctionDefinition def(String name, ReturnTypeInference type) {
        return new FunctionDefinition(name, type).category(FunctionCategory.PATTERN_MATCHING);
    }
}
