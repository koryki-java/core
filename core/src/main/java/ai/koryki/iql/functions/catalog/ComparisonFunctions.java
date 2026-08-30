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
 * Comparison operators as first-class catalog entries — the data half of
 * unifying functions and operators. The logical connectives live in
 * {@link LogicalFunctions}.
 *
 * <p><b>Phase 2 (this class):</b> operators are registered for type metadata,
 * validation and documentation only. The renderer still handles comparisons
 * directly via {@code SqlSelectRenderer}, so registering these definitions is
 * <em>behaviour-neutral</em> for SQL generation. A later phase flips the
 * renderer to dispatch through the catalog by the operator's surface text.
 *
 * <p>Keys are exactly the text the KQL mapper stores in
 * {@code UnaryLogicalExpression.op} (the matched token text: {@code "="},
 * {@code ">="}, {@code "BETWEEN"}, {@code "ISNULL"}, …), so a future renderer can
 * resolve an operator by that text. The grammar's {@code operator: … | custom=ID}
 * rule makes the operator set open-ended — a custom operator is just another
 * entry here, with no renderer change.
 */
public final class ComparisonFunctions {

    private ComparisonFunctions() {
    }

    public static void register(FunctionRegistry r) {
        // Comparison operators — the grammar's `operator` rule, plus BETWEEN, which has a production
        // of its own so that its AND cannot be read as the logical one. Operands are generic (any value compares to
        // a like-typed value), so left/right are the explicit any-type wildcard. The ordered ones
        // are the exception: they take Families.ORDERED, which drops BLOB and JSON — neither has an
        // order that holds across dialects (see Families.ORDERED). Equality keeps ANY, and
        // checkComparisonReconciliation separately requires the two sides to meet in one family.
        r.register(comparison("=", Fixity.INFIX, "{0} = {1}")
                .args(
                        arg("left", Families.ANY, "the value to test for equality"),
                        arg("right", Families.ANY, "the value to compare against"))
                .doc("True if *left* equals *right*."));
        // Keyed under the canonical "<>"; the mappers normalize a written "!=" to it.
        r.register(comparison("<>", Fixity.INFIX, "{0} <> {1}")
                .args(
                        arg("left", Families.ANY, "the value to test for inequality"),
                        arg("right", Families.ANY, "the value to compare against"))
                .doc("True if *left* does not equal *right*. NULL on either side yields NULL, "
                        + "not TRUE — a row with a NULL operand is not returned."));
        r.register(comparison("<", Fixity.INFIX, "{0} < {1}")
                .args(
                        arg("left", Families.ORDERED, "the value being tested"),
                        arg("right", Families.ORDERED, "the threshold it must stay below"))
                .doc("True if *left* is less than *right*."));
        r.register(comparison("<=", Fixity.INFIX, "{0} <= {1}")
                .args(
                        arg("left", Families.ORDERED, "the value being tested"),
                        arg("right", Families.ORDERED, "the threshold it must not exceed"))
                .doc("True if *left* is less than or equal to *right*."));
        r.register(comparison(">", Fixity.INFIX, "{0} > {1}")
                .args(
                        arg("left", Families.ORDERED, "the value being tested"),
                        arg("right", Families.ORDERED, "the threshold it must exceed"))
                .doc("True if *left* is greater than *right*."));
        r.register(comparison(">=", Fixity.INFIX, "{0} >= {1}")
                .args(
                        arg("left", Families.ORDERED, "the value being tested"),
                        arg("right", Families.ORDERED, "the threshold it must reach or exceed"))
                .doc("True if *left* is greater than or equal to *right*."));
        r.register(comparison("LIKE", Fixity.INFIX, "{0} LIKE {1}")
                .args(
                        arg("string", CoreTypeFamily.TEXT, "the text being matched"),
                        arg("pattern", CoreTypeFamily.TEXT, "the SQL LIKE pattern with `%` and `_` wildcards"))
                .doc("True if *string* matches the SQL LIKE *pattern* (`%` and `_` wildcards). "
                        + "Both operands must be TEXT."));
        r.register(comparison("BETWEEN", Fixity.RANGE, "{0} BETWEEN {1} AND {2}")
                .args(
                        arg("value", Families.ORDERED, "the value being tested"),
                        arg("low", Families.ORDERED, "the inclusive lower bound of the range"),
                        arg("high", Families.ORDERED, "the inclusive upper bound of the range"))
                .doc("True if *value* lies within the inclusive range [*low*, *high*]. "
                        + "The `AND` between the bounds belongs to the range and binds tighter than the "
                        + "logical `AND`, so `a BETWEEN 1 AND 2 AND b` is a range test combined with the "
                        + "predicate *b*. "
                        + "With a DATE or TIMESTAMP *literal* as the upper bound this is rewritten to the "
                        + "half-open form `value >= low AND value < high + 1d`, so a range ending on a day "
                        + "includes that whole day rather than stopping at midnight (see docs/TEMPORAL.md)."));
        r.register(comparison("IN", Fixity.SET, "{0} IN ({1*})")
                .variadic(
                        arg("value", Families.ANY, "the value to look for"),
                        arg("items", Families.ANY, "the values to test membership against"))
                .doc("True if *value* equals any of the listed *items*."));
        // Null-safe inequality — the same notion of distinctness FETCH DISTINCT uses, which also
        // treats two NULLs as one value. The only two-valued comparison here: it answers true or
        // false even when an operand is NULL, where the others answer unknown and the row is dropped.
        r.register(comparison("DISTINCT", Fixity.INFIX, "{0} IS DISTINCT FROM {1}")
                .args(
                        arg("left", Families.ANY, "the value to compare"),
                        arg("right", Families.ANY, "the value to compare it against"))
                .doc("True if *left* and *right* hold different values, **counting a missing value as "
                        + "a difference** — and two missing values as equal. Unlike `<>`, the result is "
                        + "never unknown, so a row is never dropped just because a value is absent.")
                .paragraph("This is the same distinctness `FETCH DISTINCT` applies when it collapses "
                        + "duplicate rows: there too, two blanks count as the same value. Write "
                        + "`NOT a DISTINCT b` for the opposite question — do these match, treating two "
                        + "blanks as matching. Prefer `=` and `<>` when the columns cannot be empty: a "
                        + "null-safe comparison is not a plain equality test, so on some engines it can "
                        + "stop an index being used where `=` would use one — worth checking on your own "
                        + "data before reaching for it by default.")); 
        r.register(comparison("ISNULL", Fixity.POSTFIX, "{0} IS NULL")
                .args(arg("value", Families.ANY, "the value tested for NULL"))
                .doc("True if *value* is NULL."));
    }

    private static FunctionDefinition comparison(String name, Fixity fixity, String template) {
        return new FunctionDefinition(name, ReturnTypes.BOOLEAN)
                .fixity(fixity)
                .template(template)
                .category(FunctionCategory.COMPARISON);
    }
}
