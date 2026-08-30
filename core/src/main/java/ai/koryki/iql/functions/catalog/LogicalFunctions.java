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
import ai.koryki.iql.functions.Fixity;
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.ReturnTypes;

import static ai.koryki.iql.functions.FunctionArg.arg;

/**
 * Logical operators — KQL.g4 {@code logical_expression} rule. Unlike the
 * comparison operators in {@link ComparisonFunctions}, these are NOT dispatched
 * through the catalog at render time: {@code SqlSelectRenderer.toSql(LogicalExpression)}
 * emits them structurally (n-ary AND/OR with indentation, NOT(...) wrapping).
 * The {@code logical_expression} rule is a closed set (NOT/AND/OR, no
 * {@code custom=ID}) and the keywords are ANSI-universal, so there is no
 * dialect variance to absorb and nothing to make extensible. The templates
 * below are therefore DOCUMENTATION-ONLY (they drive the docs rendering line);
 * keep them spelled like the renderer's keyword. The BOOLEAN operand families
 * are likewise documentation — logical nodes are not resolved through the
 * catalog, so they are not enforced at validation. (The grammar cannot express a
 * bare boolean operand anyway: {@code unary_logical_expression} is always a
 * comparison, a parenthesized group or an EXISTS, and there is no boolean literal.)
 *
 * <p>These three share the registry namespace with callable functions, which
 * {@link ai.koryki.iql.SqlDialect#renderComparison} searches by an operator's
 * surface text (filtered to non-PREFIX fixities — which these are). Nothing can
 * reach them today: {@code AND}/{@code OR}/{@code NOT} are keyword tokens, and the
 * grammar's open {@code custom=ID} operator is lowercase-only. A phase that flips
 * the renderer to dispatch logical nodes through the catalog has to keep that
 * separation deliberate rather than inherit it.
 */
public final class LogicalFunctions {

    private LogicalFunctions() {
    }

    public static void register(FunctionRegistry r) {
        r.register(logical("AND", Fixity.INFIX, "{0} AND {1}")
                .args(
                        arg("left", CoreTypeFamily.BOOLEAN, "the first condition"),
                        arg("right", CoreTypeFamily.BOOLEAN, "the second condition"))
                .doc("True if both operands are true."));
        r.register(logical("OR", Fixity.INFIX, "{0} OR {1}")
                .args(
                        arg("left", CoreTypeFamily.BOOLEAN, "the first condition"),
                        arg("right", CoreTypeFamily.BOOLEAN, "the second condition"))
                .doc("True if either operand is true."));
        r.register(logical("NOT", Fixity.PREFIX_UNARY, "NOT {0}")
                .args(arg("operand", CoreTypeFamily.BOOLEAN, "the condition to negate"))
                .doc("Negates the operand."));
    }

    private static FunctionDefinition logical(String name, Fixity fixity, String template) {
        return new FunctionDefinition(name, ReturnTypes.BOOLEAN)
                .fixity(fixity)
                .template(template)
                .category(FunctionCategory.LOGICAL);
    }
}
