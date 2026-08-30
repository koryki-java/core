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

/**
 * Window-only functions: they answer a question about a row's position within a group of rows —
 * "which rank does this order have by freight", "what was the previous month's total" — and are
 * meaningless without the {@code OVER (…)} clause that says which rows and in what order.
 *
 * <p>They are {@link FunctionKind#WINDOW}, not AGGREGATE: they produce a value per row rather than
 * collapsing rows, so they must never trigger GROUP BY inference. {@code FunctionValidator} enforces
 * that each call carries an OVER clause, and that the ordered ones carry an ORDER BY inside it.
 *
 * <p>No dialect overrides. All six were measured native on all eight supported engines — unusually,
 * these are among the most portable functions in the catalog, not the least.
 */
public final class WindowFunctions {

    private WindowFunctions() {
    }

    public static void register(FunctionRegistry r) {
        r.register(def("row_number", ReturnTypes.BIGINT)
                .signature(FunctionSignature.of())
                .doc("Position of the row within its window, counting from 1. Ties are numbered "
                        + "arbitrarily — use `rank` if equal values should share a number."));
        r.register(def("rank", ReturnTypes.BIGINT)
                .signature(FunctionSignature.of())
                .doc("Position of the row by the window's ordering, where equal values share a rank "
                        + "and the next rank skips ahead: 1, 2, 2, 4."));
        r.register(def("dense_rank", ReturnTypes.BIGINT)
                .signature(FunctionSignature.of())
                .doc("Like `rank`, but without gaps after a tie: 1, 2, 2, 3."));
        r.register(def("ntile", ReturnTypes.BIGINT)
                .args(arg("buckets", Families.NUMERIC, "how many groups to divide the rows into"))
                .doc("Splits the window's rows into *buckets* groups of near-equal size and returns "
                        + "which group the row falls in — quartiles with `ntile(4)`."));
        r.register(def("lag", ReturnTypes.ARG0)
                .signature(FunctionSignature.of(
                        arg("value", Families.ANY, "the value to read from an earlier row"),
                        FunctionArg.optionalArg("offset", Families.NUMERIC,
                                "how many rows back to look; 1 (the previous row) by default"),
                        FunctionArg.optionalArg("default", Families.ANY,
                                "value to use when there is no such row; null by default")))
                .doc("The value from an earlier row of the window — the previous month's figure, "
                        + "say, for comparing against this one."));
        r.register(def("lead", ReturnTypes.ARG0)
                .signature(FunctionSignature.of(
                        arg("value", Families.ANY, "the value to read from a later row"),
                        FunctionArg.optionalArg("offset", Families.NUMERIC,
                                "how many rows forward to look; 1 (the next row) by default"),
                        FunctionArg.optionalArg("default", Families.ANY,
                                "value to use when there is no such row; null by default")))
                .doc("The value from a later row of the window — the mirror of `lag`."));
    }

    private static FunctionDefinition def(String name, ReturnTypeInference type) {
        return new FunctionDefinition(name, type, FunctionKind.WINDOW)
                .category(FunctionCategory.WINDOW);
    }
}
