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

import ai.koryki.antlr.Text;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.Families;
import ai.koryki.iql.functions.CaseFunctionDefinition;
import ai.koryki.iql.functions.ConditionalFunctionDefinition;
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionRegistry;

import static ai.koryki.iql.functions.FunctionArg.arg;

/**
 * The conditional core: three primitives that render to standard SQL on every dialect —
 * {@code coalesce} (first non-null), {@code nullif} (its inverse) and the searched {@code case}.
 *
 * <p>The dialect-specific spellings are deliberately absent: {@code if}, {@code iff}, {@code iif},
 * {@code nvl}, {@code nvl2}, {@code ifnull} and {@code decode} all say something {@code case} or
 * {@code coalesce} already says, and each of them means slightly different things on different
 * databases. One name per idea, portable by construction.
 *
 * <p>{@code nullif} is the one that does not follow strictly from that rule: {@code case(a = b, null, a)}
 * expresses it, so by the principle above it need not be here. It is kept because it is standard SQL,
 * carries no dialect ambiguity, and states the intent — turning a placeholder into a real blank — far
 * more plainly than the {@code case} that replaces it.
 */
public final class ConditionalFunctions {

    private ConditionalFunctions() {
    }

    public static void register(FunctionRegistry r) {
        r.register(new ConditionalFunctionDefinition("coalesce")
                .category(FunctionCategory.CONDITIONAL)
                .variadic(arg("value", Families.ANY, "the values checked in order for the first non-null"))
                .doc("The first argument that is not blank; blank only if every argument is.")
                .paragraph("The arguments are read left to right and **evaluation stops at the first "
                        + "non-blank one** — what stands to its right is never evaluated. That is a "
                        + "guarantee, not an optimisation: it is what makes `coalesce(x, 1/0)` safe."));
        r.register(new ConditionalFunctionDefinition("nullif")
                .category(FunctionCategory.CONDITIONAL)
                .args(arg("value", Families.ANY, "the value to return unless it matches"),
                        arg("when", Families.ANY, "the value that turns the result into null"))
                .doc("*value*, or blank when it equals *when* — the inverse of `coalesce`, and the way "
                        + "to turn a placeholder such as `0` or an empty string into a real blank.")
                .paragraph("The two are **compared**, so they must be able to meet in one type, and the "
                        + "result takes that common type rather than *value*'s own. `nullif(quantity, "
                        + "0.5)` therefore comes out decimal, not integer — the comparison promotes "
                        + "*value* before it is returned. Comparing across type groups, say text with a "
                        + "number, is an error."));
        r.register(new CaseFunctionDefinition()
                .category(FunctionCategory.CONDITIONAL)
                .variadic(
                        arg("condition", CoreTypeFamily.BOOLEAN, "a WHEN test evaluated in order until one is true"),
                        arg("result", Families.ANY, "the value returned when its paired condition is true"))
                .doc("Tests each condition in order and returns the result paired with the first one "
                        + "that holds. A trailing argument without a condition is the fallback; without "
                        + "it, a row that matches nothing comes out blank.")
                .paragraph("All the results must be able to meet in **one type** — that is what the "
                        + "column will hold, and it is decided once for the whole expression, not per "
                        + "row. Mixing text and numbers across the branches is therefore an error, not "
                        + "a per-row surprise."
                        + Text.NL
                        + Text.NL
                        + "Like `coalesce`, only the branches that are needed get evaluated: testing "
                        + "stops at the first condition that holds."));
    }
}
