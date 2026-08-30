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
                .args(arg("string", TEXT, "the source text to test"),
                        arg("pattern", TEXT, "the regular expression to match against"),
                        optionalArg("flags", TEXT, "match modifiers, e.g. 'i' for case-insensitivity"))
                .doc("True if the string matches the regular expression *pattern*."));
        r.register(def("regexp_count", ReturnTypes.INTEGER)
                .args(arg("string", TEXT, "the source text to search"),
                        arg("pattern", TEXT, "the regular expression to count occurrences of"),
                        optionalArg("start", INT, "1-based index to begin searching from"))
                .doc("Number of matches of *pattern* in the string."));
        r.register(def("regexp_substr", ReturnTypes.TEXT)
                .args(arg("string", TEXT, "the source text to search"),
                        arg("pattern", TEXT, "the regular expression to match"))
                .doc("First substring matching the regular expression *pattern*."));
        // No flags argument: it meant something different in every database -- modifiers in
        // PostgreSQL and DuckDB, a numeric position in Oracle and MariaDB, nothing at all in Trino.
        // Its documented purpose ('g' = all matches) is now the function's own promise.
        r.register(def("regexp_replace", ReturnTypes.TEXT)
                .args(arg("string", TEXT, "the source text to modify"),
                        arg("pattern", TEXT, "the regular expression to match"),
                        arg("replacement", TEXT, "the text substituted for each match"))
                .doc("Replaces **every** substring matching the regular expression *pattern* with "
                        + "*replacement*.")
                .paragraph("Every match, not just the first — on every database. That guarantee costs "
                        + "something: PostgreSQL and DuckDB replace only the first match by default, so "
                        + "KQL renders them with the `g` modifier. MariaDB, Oracle and Trino already "
                        + "replace all. Measured before this was levelled, `regexp_replace(phone, "
                        + "'[^0-9]', '')` on `(02) 201 24 67` gave `02) 201 24 67` on the first two and "
                        + "`022012467` on the other three."));
    }

    private static FunctionDefinition def(String name, ReturnTypeInference type) {
        return new FunctionDefinition(name, type).category(FunctionCategory.PATTERN_MATCHING);
    }
}
