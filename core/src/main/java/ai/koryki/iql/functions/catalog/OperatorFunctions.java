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
import ai.koryki.iql.functions.Fixity;
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.ReturnTypes;

import static ai.koryki.iql.functions.FunctionArg.arg;

/**
 * Comparison and logical operators as first-class catalog entries — the data
 * half of unifying functions and operators.
 *
 * <p><b>Phase 2 (this class):</b> operators are registered for type metadata,
 * validation and documentation only. The renderer still handles comparisons and
 * logical expressions directly via {@code SqlSelectRenderer}, so registering
 * these definitions is <em>behaviour-neutral</em> for SQL generation. A later
 * phase flips the renderer to dispatch through the catalog by the operator's
 * surface text.
 *
 * <p>Keys are exactly the text the KQL mapper stores in
 * {@code UnaryLogicalExpression.op} (the matched token text: {@code "="},
 * {@code ">="}, {@code "BETWEEN"}, {@code "ISNULL"}, …), so a future renderer can
 * resolve an operator by that text. The grammar's {@code operator: … | custom=ID}
 * rule makes the operator set open-ended — a custom operator is just another
 * entry here, with no renderer change.
 */
public final class OperatorFunctions {

    private OperatorFunctions() {
    }

    public static void register(FunctionRegistry r) {
        // Comparison operators — KQL.g4 `operator` rule. Operands are generic (any value compares to
        // a like-typed value), so left/right are the explicit any-type wildcard.
        r.register(comparison("=", Fixity.INFIX, "{0} = {1}")
                .args(arg("left", Families.ANY), arg("right", Families.ANY))
                .doc("True if *left* equals *right*.")
                .example("c.country = 'Germany'"));
        r.register(comparison("<", Fixity.INFIX, "{0} < {1}")
                .args(arg("left", Families.ANY), arg("right", Families.ANY))
                .doc("True if *left* is less than *right*.")
                .example("o.freight < 50"));
        r.register(comparison("<=", Fixity.INFIX, "{0} <= {1}")
                .args(arg("left", Families.ANY), arg("right", Families.ANY))
                .doc("True if *left* is less than or equal to *right*.")
                .example("od.discount <= 0.1"));
        r.register(comparison(">", Fixity.INFIX, "{0} > {1}")
                .args(arg("left", Families.ANY), arg("right", Families.ANY))
                .doc("True if *left* is greater than *right*.")
                .example("o.freight > 100"));
        r.register(comparison(">=", Fixity.INFIX, "{0} >= {1}")
                .args(arg("left", Families.ANY), arg("right", Families.ANY))
                .doc("True if *left* is greater than or equal to *right*.")
                .example("od.quantity >= 10"));
        r.register(comparison("LIKE", Fixity.INFIX, "{0} LIKE {1}")
                .args(arg("string", CoreTypeFamily.TEXT), arg("pattern", CoreTypeFamily.TEXT))
                .doc("True if *string* matches the SQL LIKE *pattern* (`%` and `_` wildcards). "
                        + "Both operands must be TEXT.")
                .example("c.company_name LIKE 'A%'"));
        r.register(comparison("BETWEEN", Fixity.RANGE, "{0} BETWEEN {1} AND {2}")
                .args(arg("value", Families.ANY), arg("low", Families.ANY), arg("high", Families.ANY))
                .doc("True if *value* lies within the inclusive range [*low*, *high*].")
                .example("o.order_date BETWEEN '1997-01-01' AND '1997-12-31'"));
        r.register(comparison("IN", Fixity.SET, "{0} IN ({1*})")
                .variadic(arg("value", Families.ANY), arg("items", Families.ANY))
                .doc("True if *value* equals any of the listed *items*.")
                .example("c.country IN ('USA', 'Canada')"));
        r.register(comparison("ISNULL", Fixity.POSTFIX, "{0} IS NULL")
                .args(arg("value", Families.ANY))
                .doc("True if *value* is NULL.")
                .example("o.shipped_date ISNULL"));

        // Logical operators — KQL.g4 `logical_expression` rule. Unlike the
        // comparison operators above, these are NOT dispatched through the
        // catalog at render time: `SqlSelectRenderer.toSql(LogicalExpression)`
        // emits them structurally (n-ary AND/OR with indentation, NOT(...)
        // wrapping). The `logical_expression` rule is a closed set (NOT/AND/OR,
        // no `custom=ID`) and the keywords are ANSI-universal, so there is no
        // dialect variance to absorb and nothing to make extensible. The
        // templates below are therefore DOCUMENTATION-ONLY (they drive the docs
        // rendering line); keep them spelled like the renderer's keyword. The
        // BOOLEAN operand families are likewise documentation — logical nodes are
        // not resolved through the catalog, so they are not enforced at validation.
        r.register(logical("AND", Fixity.INFIX, "{0} AND {1}")
                .args(arg("left", CoreTypeFamily.BOOLEAN), arg("right", CoreTypeFamily.BOOLEAN))
                .doc("True if both operands are true.")
                .example("o.freight > 50 AND c.country = 'USA'"));
        r.register(logical("OR", Fixity.INFIX, "{0} OR {1}")
                .args(arg("left", CoreTypeFamily.BOOLEAN), arg("right", CoreTypeFamily.BOOLEAN))
                .doc("True if either operand is true.")
                .example("c.country = 'USA' OR c.country = 'Mexico'"));
        r.register(logical("NOT", Fixity.PREFIX_UNARY, "NOT {0}")
                .args(arg("operand", CoreTypeFamily.BOOLEAN))
                .doc("Negates the operand.")
                .example("NOT o.shipped_date ISNULL"));
    }

    private static FunctionDefinition comparison(String name, Fixity fixity, String template) {
        return operator(name, fixity, template, FunctionCategory.COMPARISON);
    }

    private static FunctionDefinition logical(String name, Fixity fixity, String template) {
        return operator(name, fixity, template, FunctionCategory.LOGICAL);
    }

    private static FunctionDefinition operator(String name, Fixity fixity, String template, FunctionCategory category) {
        return new FunctionDefinition(name, ReturnTypes.BOOLEAN)
                .fixity(fixity)
                .template(template)
                .category(category);
    }
}
