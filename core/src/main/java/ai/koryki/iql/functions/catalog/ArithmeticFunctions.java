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

import ai.koryki.catalog.schema.types.Families;
import ai.koryki.catalog.schema.types.TypeDescriptor;
import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.MathOp;
import ai.koryki.iql.functions.ReturnTypeInference;
import ai.koryki.iql.functions.ReturnTypes;
import ai.koryki.iql.query.Duration;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;

import java.util.ArrayList;
import java.util.List;

import static ai.koryki.iql.functions.FunctionArg.arg;

/**
 * Internal arithmetic operator functions (add, minus, multiply, divide, negate).
 * Rendering stays in Java: operands chain pairwise through the dialect's
 * {@code renderEncodedArithmetic} hook so encoded types (e.g. TIME stored as
 * seconds-from-midnight) can intercept each step.
 */
public final class ArithmeticFunctions {

    private ArithmeticFunctions() {
    }

    public static void register(FunctionRegistry r) {
        r.register(new FunctionDefinition(MathOp.negate.name(), ReturnTypes.ARG0) {
            @Override
            public String render(SqlSelectRenderer renderer, Function function, int indent) {
                Expression operand = function.getArguments().get(0);
                String inner = renderer.toSql(operand, indent);
                return operand.getFunction() != null ? "-(" + inner + ")" : "-" + inner;
            }
        }.category(FunctionCategory.ARITHMETIC)
                .args(arg("value", Families.ANY))
                .doc("Arithmetic negation (`-x`): negates a number, or flips the sign of every component "
                        + "of a DURATION.")
                .example("-c.balance"));

        for (MathOp op : new MathOp[]{MathOp.add, MathOp.minus, MathOp.multiply, MathOp.divide}) {
            String symbol = op.getOperator();
            ReturnTypeInference returnType = switch (op) {
                case multiply   -> ReturnTypes.MULTIPLY;
                case add, minus -> ReturnTypes.ADD_SUB;
                case divide     -> ReturnTypes.DECIMAL_DIVIDE;
                default         -> ReturnTypes.LEAST_RESTRICTIVE;
            };
            MathOp current = op;
            FunctionDefinition fn = new FunctionDefinition(op.name(), returnType) {
                @Override
                public String render(SqlSelectRenderer renderer, Function function, int indent) {
                    List<Expression> args = function.getArguments();
                    if (args.size() < 2) {
                        throw new IllegalArgumentException(function.getFunc() + " requires at least 2 arguments, got " + args.size());
                    }
                    if (current == MathOp.multiply && args.size() == 2) {
                        // DURATION × integer (either order) → the component-wise scaled duration literal,
                        // rendered via the dialect's own duration rendering (portable; avoids `n * INTERVAL`).
                        String scaled = scaleDurationLiteral(renderer, args.get(0), args.get(1), indent);
                        if (scaled != null) {
                            return scaled;
                        }
                    }
                    Expression leftExpr = args.get(0);
                    TypeDescriptor leftType = renderer.resolveType(leftExpr);
                    Expression rightExpr = args.get(1);
                    TypeDescriptor rightType = renderer.resolveType(rightExpr);
                    String result = renderer.getDialect().renderEncodedArithmetic(renderer, symbol, leftExpr, leftType, rightExpr, rightType, indent);
                    TypeDescriptor resultType = rightType;
                    for (int i = 2; i < args.size(); i++) {
                        Expression right = args.get(i);
                        TypeDescriptor rType = renderer.resolveType(right);
                        result = renderer.getDialect().renderEncodedArithmetic(renderer, symbol, result, resultType, right, rType, indent);
                        resultType = rType;
                    }
                    return result;
                }
            }.category(FunctionCategory.ARITHMETIC)
                    .variadic(arg("left", Families.ANY), arg("right", Families.ANY));
            switch (op) {
                case add -> fn.doc("Addition (`+`): numeric addition, or temporal arithmetic per the "
                                + "result-type algebra — DATE/TIMESTAMP/TIME + DURATION, DATE + TIME, and "
                                + "DURATION + DURATION (see docs/TEMPORAL.md).")
                        .example("o.order_date + 30d");
                case minus -> fn.doc("Subtraction (`-`): numeric subtraction, or temporal — DATE − DATE and "
                                + "TIMESTAMP − TIMESTAMP yield a DURATION, and a temporal value − DURATION "
                                + "shifts it (see docs/TEMPORAL.md).")
                        .example("o.shipped_date - o.order_date");
                case multiply -> fn.doc("Multiplication (`*`): numeric multiplication, or DURATION × integer "
                                + "scaled component-wise (see docs/TEMPORAL.md).")
                        .example("o.unit_price * o.quantity");
                case divide -> fn.doc("Division (`/`): numeric division; the result is decimal/double. "
                                + "Dividing a DURATION is not defined.")
                        .example("o.freight / o.quantity");
                default -> { }
            }
            r.register(fn);
        }
    }

    /**
     * {@code integer × DURATION-literal} (either order) folded into the component-wise scaled duration
     * (per docs/TEMPORAL.md), rendered through the dialect's normal duration rendering — so it is portable
     * and never emits {@code n * INTERVAL}. Returns {@code null} when the pair is not an integer-scaled
     * duration literal (e.g. a column factor, a non-integer factor, or duration × duration), leaving the
     * caller to fall back to the generic numeric multiply.
     */
    private static String scaleDurationLiteral(SqlSelectRenderer renderer, Expression a, Expression b, int indent) {
        Expression durExpr;
        Expression numExpr;
        if (a.getDuration() != null && b.getNumber() != null) {
            durExpr = a; numExpr = b;
        } else if (b.getDuration() != null && a.getNumber() != null) {
            durExpr = b; numExpr = a;
        } else {
            return null;
        }
        Number n = numExpr.getNumber();
        if (n.longValue() != n.doubleValue()) {
            return null;   // non-integer factor — undefined (validation error elsewhere), don't fold
        }
        long factor = n.longValue();
        List<Duration.Component> scaled = new ArrayList<>();
        for (Duration.Component c : durExpr.getDuration().getComponents()) {
            scaled.add(new Duration.Component(Math.toIntExact(c.value() * factor), c.unit()));
        }
        Expression folded = new Expression();
        folded.setDuration(new Duration(scaled));
        return renderer.toSql(folded, indent);
    }
}
