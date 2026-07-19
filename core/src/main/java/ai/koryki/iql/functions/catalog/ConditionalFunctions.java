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
import ai.koryki.iql.functions.CaseFunctionDefinition;
import ai.koryki.iql.functions.ConditionalFunctionDefinition;
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionRegistry;

import static ai.koryki.iql.functions.FunctionArg.arg;

/**
 * The conditional core: just two portable primitives — {@code coalesce} (first non-null) and the
 * searched {@code case}. Both render to standard SQL on every dialect, so dialect-specific spellings
 * (if / iff / iif / nvl / nvl2 / ifnull / nullif / decode) are intentionally absent: {@code case}
 * subsumes the ternaries (and nullif/nvl2/decode via a condition), {@code coalesce} subsumes nvl/ifnull.
 */
public final class ConditionalFunctions {

    private ConditionalFunctions() {
    }

    public static void register(FunctionRegistry r) {
        r.register(new ConditionalFunctionDefinition("coalesce")
                .category(FunctionCategory.CONDITIONAL)
                .variadic(arg("value", Families.ANY))
                .doc("First non-null argument.")
                .example("coalesce(o.shipped_date, today())"));
        r.register(new CaseFunctionDefinition()
                .category(FunctionCategory.CONDITIONAL)
                .variadic(arg("condition", CoreTypeFamily.BOOLEAN), arg("result", Families.ANY))
                .doc("Searched CASE: case(cond1, result1, ..., [else]) -> CASE WHEN cond1 THEN result1 ... [ELSE else] END.")
                .example("case(o.freight > 100, 'high', 'low')"));
    }
}
