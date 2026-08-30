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
package ai.koryki.iql.validate;

import ai.koryki.antlr.Range;
import ai.koryki.iql.Collector;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.Visitor;
import ai.koryki.iql.functions.MathOp;
import ai.koryki.catalog.types.CoreTypeEncoding;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeEncoding;
import ai.koryki.iql.typing.ExpressionTypeResolver;
import ai.koryki.iql.query.Duration;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
import org.antlr.v4.runtime.RuleContext;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Reports a duration the engine cannot represent <em>as a value</em>, according to what the dialect
 * declares in {@link SqlDialect#intervalSupport()}.
 *
 * <p>Only the value path is at stake. In arithmetic no interval is ever built: {@code date + 1y2mo1d}
 * expands into chained per-unit steps, which is why that form works on every engine. The two paths
 * are told apart structurally, without type resolution — a duration that is an operand of
 * {@code +}/{@code −} alongside something that is not itself a duration is on the arithmetic path.
 *
 * <p>Why this is a validator and not a render-time exception: the default
 * {@link SqlDialect#durationLiteral} happily emits {@code INTERVAL '1' HOUR + INTERVAL '2' MINUTE},
 * and MariaDB, SQL Server and SQLite — which have no interval type at all — inherited it. They
 * therefore produced SQL that only the database rejected, at a point with no position to report and
 * nothing in the catalog predicting it. Nine fixtures carried a hand-written {@code ignore=} marker
 * for that, which also suppressed their SQL check. Trino and Oracle failed the same way one step
 * later: they have {@code INTERVAL YEAR TO MONTH} and {@code INTERVAL DAY TO SECOND} but no type
 * spanning both, so a mixed duration is what they reject.
 *
 * <p>Deliberately conservative: it never reports a form the engine can render, and an exotic shape
 * it misses still reaches the render-time guard, which stays as the backstop.
 */
public class DurationValueValidator implements Visitor, Collector<List<Violation>> {

    private final List<Violation> violations = new ArrayList<>();
    private final Map<Object, RuleContext> iqlToContext;
    private final SqlDialect.IntervalSupport support;
    private final ValidationContext context;

    /**
     * The scope a field is looked up in. Passing the root context instead resolves nothing — the
     * alias belongs to the select it was written in, and {@code ExpressionTypeResolver} then trips
     * over a null source. {@code FunctionValidator} keeps the same stack for the same reason.
     */
    private final java.util.Deque<ai.koryki.iql.IQLVisibilityContext> scopes = new java.util.ArrayDeque<>();

    @Override
    public boolean visit(Deque<Object> deque, ai.koryki.iql.query.Select select) {
        scopes.push((scopes.isEmpty() ? context.visibility() : scopes.peek()).child(select));
        return true;
    }

    @Override
    public void leave(ai.koryki.iql.query.Select select) {
        if (!scopes.isEmpty()) {
            scopes.pop();
        }
    }

    public DurationValueValidator(ValidationContext context, SqlDialect dialect) {
        this.context = context;
        this.iqlToContext = context.iqlToContext();
        this.support = dialect.intervalSupport();
    }

    /**
     * On a {@code SPLIT} engine, comparing the two interval families with each other.
     *
     * <p>Oracle stores {@code INTERVAL YEAR TO MONTH} and {@code INTERVAL DAY TO SECOND} as separate
     * types with no conversion between them, so {@code c.interval_year_month > 1h2min3s} is not a
     * value it can answer -- it raises ORA-00932. Every dialect with a single interval type answers
     * it, which is why this is declared per dialect rather than rejected outright.
     *
     * <p>Which family a column belongs to is read from its {@code typeEncoding}, not from the text
     * of its dialect type: {@code INTERVAL_YEAR_MONTH} and {@code INTERVAL_DAY_SECOND} exist for
     * exactly this and are what the catalog declares. A duration literal is classified by its own
     * units; a mixed one is left alone, because it has no value on this engine at all and the rule
     * above has already said so.
     */
    @Override
    public boolean visit(Deque<Object> deque, ai.koryki.iql.query.UnaryLogicalExpression comparison) {
        if (support != SqlDialect.IntervalSupport.SPLIT
                || comparison.getOp() == null || comparison.getRight().isEmpty()) {
            return true;
        }
        List<Expression> operands = new ArrayList<>();
        operands.add(comparison.getLeft());
        operands.addAll(comparison.getRight());

        Duration duration = null;
        Expression durationOperand = null;
        CoreTypeEncoding column = null;
        if (scopes.isEmpty()) {
            return true;
        }
        ExpressionTypeResolver types = new ExpressionTypeResolver(
                context.resolver(), scopes.peek(), context.functions());
        for (Expression e : operands) {
            if (e == null) {
                continue;
            }
            if (e.getDuration() != null) {
                duration = e.getDuration();
                durationOperand = e;
            } else {
                CoreTypeEncoding enc = intervalEncoding(types, e);
                if (enc != null) {
                    column = enc;
                }
            }
        }
        if (duration == null || column == null || isMixed(duration)) {
            return true;
        }
        boolean durationIsCalendar = duration.getComponents().stream().allMatch(c -> isCalendar(c.unit()));
        boolean columnIsCalendar = CoreTypeEncoding.INTERVAL_YEAR_MONTH.equals(column);
        if (durationIsCalendar != columnIsCalendar) {
            violations.add(new Violation(Violation.UNSUPPORTED, durationOperand,
                    Range.of(iqlToContext, durationOperand),
                    "duration '" + duration + "' is a "
                            + (durationIsCalendar ? "YEAR TO MONTH" : "DAY TO SECOND")
                            + " amount and the other side is "
                            + (columnIsCalendar ? "YEAR TO MONTH" : "DAY TO SECOND")
                            + " — this dialect keeps the two interval types apart and cannot compare them"));
        }
        return true;
    }

    /** The interval sub-family an operand's declared type names, or null when it is not one. */
    private static CoreTypeEncoding intervalEncoding(ExpressionTypeResolver types, Expression e) {
        try {
            TypeDescriptor t = types.resolve(e);
            TypeEncoding enc = t != null ? t.getTypeEncoding() : null;
            if (CoreTypeEncoding.INTERVAL_YEAR_MONTH.equals(enc) || CoreTypeEncoding.INTERVAL_DAY_SECOND.equals(enc)) {
                return (CoreTypeEncoding) enc;
            }
        } catch (RuntimeException ignored) {
            // An operand that cannot be typed is not this rule's business; the schema check reports it.
        }
        return null;
    }

    @Override
    public boolean visit(Deque<Object> deque, Expression expression) {
        Duration duration = expression.getDuration();
        if (duration == null || support == SqlDialect.IntervalSupport.FULL) {
            return true;
        }
        if (support == SqlDialect.IntervalSupport.SPLIT && !isMixed(duration)) {
            return true;
        }
        if (inTemporalArithmetic(deque, expression)) {
            return true;
        }
        violations.add(new Violation(Violation.UNSUPPORTED, expression,
                Range.of(iqlToContext, expression), message(duration)));
        return true;
    }

    private String message(Duration duration) {
        String tail = " Adding it to a date works; using it as a value does not";
        if (support == SqlDialect.IntervalSupport.NONE) {
            return "duration '" + duration + "' cannot be a value on this dialect — it has no "
                    + "interval type." + tail;
        }
        return "duration '" + duration + "' mixes calendar units (y, q, mo) with clock units, and "
                + "this dialect keeps YEAR TO MONTH and DAY TO SECOND apart with no type spanning "
                + "both." + tail;
    }

    private static boolean isMixed(Duration duration) {
        boolean calendar = duration.getComponents().stream().anyMatch(c -> isCalendar(c.unit()));
        boolean clock = duration.getComponents().stream().anyMatch(c -> !isCalendar(c.unit()));
        return calendar && clock;
    }

    /** YEAR/QUARTAL/MONTH are the year-month family; everything from WEEK down is day-to-second. */
    private static boolean isCalendar(Duration.Unit unit) {
        return unit == Duration.Unit.YEAR || unit == Duration.Unit.QUARTAL || unit == Duration.Unit.MONTH;
    }

    /**
     * True when this duration is an operand of {@code +}/{@code −} whose other operand is not a
     * duration — the shape that expands into chained per-unit steps instead of an interval value.
     * Intervening {@link Expression}s are pass-through wrappers (parentheses, unary sign).
     */
    private static boolean inTemporalArithmetic(Deque<Object> deque, Expression self) {
        for (Object ancestor : deque) {
            if (ancestor instanceof Expression) {
                continue;
            }
            if (!(ancestor instanceof Function fn)) {
                return false;
            }
            if (!MathOp.add.name().equals(fn.getFunc()) && !MathOp.minus.name().equals(fn.getFunc())) {
                return false;
            }
            return fn.getArguments().stream().anyMatch(a -> a != self && a.getDuration() == null);
        }
        return false;
    }

    @Override
    public List<Violation> collect() {
        return violations;
    }
}
