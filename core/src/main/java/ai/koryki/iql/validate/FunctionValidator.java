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
import ai.koryki.catalog.types.CoreTypeEncoding;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.IntervalStringEncoding;
import ai.koryki.catalog.types.IntervalTypeEncoding;
import ai.koryki.catalog.types.IntervalUnitClass;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeEncoding;
import ai.koryki.catalog.types.TypeFamily;
import ai.koryki.iql.IQLVisibilityContext;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.Collector;
import ai.koryki.iql.Visitor;
import ai.koryki.iql.functions.BranchedConditional;
import ai.koryki.iql.functions.CaseFunctionDefinition;
import ai.koryki.iql.functions.ConditionalReconciler;
import ai.koryki.iql.functions.Fixity;
import ai.koryki.iql.functions.FunctionCatalog;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionKind;
import ai.koryki.iql.functions.FunctionSignature;
import ai.koryki.iql.functions.MathOp;
import ai.koryki.iql.functions.StandardFunctions;
import ai.koryki.iql.query.*;
import ai.koryki.iql.typing.ExpressionTypeResolver;
import org.antlr.v4.runtime.RuleContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FunctionValidator implements Visitor, Collector<List<Violation>> {

    private List<Violation> violations = new ArrayList<>();
    private Map<Object, RuleContext> iqlToContext;
    private final FunctionCatalog functions;

    // Type resolution for operator argument-family checks. Null resolver/visibility
    // (the arity-only constructors) disables those checks. The scope stack mirrors
    // the renderer: a child visibility context is pushed per Select so operands
    // resolve in their own scope (correct alias shadowing, no false positives).
    private final LinkResolver resolver;
    private final IQLVisibilityContext rootVisibility;
    private final Deque<IQLVisibilityContext> scopes = new ArrayDeque<>();

    /**
     * The only constructor, deliberately. Convenience overloads used to allow a validator with a
     * null catalog, resolver or visibility, each of which silently switches off a group of checks —
     * `validateCall` and `checkKnownOperator` on the catalog, everything behind {@link #typeChecks}
     * on the other two. That is fine as an internal degradation but was a trap as an API: it made
     * "the validator reported nothing" mean "most checks did not run", which is how `ValidatorTest`
     * spent eleven cases asserting almost nothing. Callers now have to supply all four, or say
     * explicitly with a null argument which checks they are giving up.
     *
     * @param functions  dialect function catalog for arity/unsupported checks; null = skip those
     * @param resolver   schema resolver for typing operands; null = skip family checks
     * @param visibility root scope; child contexts are derived per Select during the walk
     */
    public FunctionValidator(Map<Object, RuleContext> iqlToContext, FunctionCatalog functions,
            LinkResolver resolver, IQLVisibilityContext visibility) {
        this.iqlToContext = iqlToContext;
        this.functions = functions;
        this.resolver = resolver;
        this.rootVisibility = visibility;
    }

    private boolean typeChecks() {
        return functions != null && resolver != null && rootVisibility != null;
    }

    @Override
    public boolean visit(Deque<Object> deque, Function function) {
        if (functions != null) {
            validateCall(function);
        }
        checkTimeArithmetic(function);
        checkAnchorFirst(function);
        checkConditionalReconciliation(function);
        checkLogicalArguments(function);
        checkProjectionOnly(deque, function);
        checkWindowOnly(function);
        return true;
    }

    /** Window functions that are meaningless without an ordering inside the frame. */
    private static final java.util.Set<String> NEEDS_ORDER =
            java.util.Set.of("rank", "dense_rank", "ntile", "lag", "lead");

    /**
     * A {@link FunctionKind#WINDOW} function answers "where does this row sit among those rows", so
     * it needs an {@code OVER (…)} saying which rows — and, for the ordered ones, in what order. A
     * {@code rank()} with nothing to rank by is not a query the author meant to write.
     *
     * <p>{@code row_number} is deliberately exempt from the ordering rule: numbering the rows of an
     * unordered partition is a legitimate thing to ask for.
     */
    private void checkWindowOnly(Function function) {
        if (functions == null) {
            return;
        }
        FunctionDefinition def = null;
        for (FunctionDefinition d : functions.overloads(function.getFunc())) {
            def = d;
            break;
        }
        if (def == null || def.getKind() != FunctionKind.WINDOW) {
            return;
        }
        if (function.getWindow() == null) {
            violations.add(new Violation("function", function, Range.of(iqlToContext, function),
                    "'" + function.getFunc() + "' needs an OVER clause saying which rows it looks at — "
                            + "e.g. " + function.getFunc() + "(...) OVER (PARTITION BY ... ORDER BY ...)"));
            return;
        }
        if (NEEDS_ORDER.contains(function.getFunc().toLowerCase(Locale.ROOT))
                && function.getWindow().getOrder().isEmpty()) {
            violations.add(new Violation("function", function, Range.of(iqlToContext, function),
                    "'" + function.getFunc() + "' needs an ORDER inside its OVER clause — without one "
                            + "there is nothing to order by, so the result would be arbitrary"));
        }
        // A ranking or navigation function reads the whole partition by definition, so a frame has
        // nothing to narrow and the SQL standard bars the combination. Only aggregates used with
        // OVER take one — and those never reach here, since this method returns early unless the
        // function's kind is WINDOW.
        //
        // Measured before this check existed: MariaDB, SQL Server, Oracle and Trino each refused it
        // with their own driver message; DuckDB, PostgreSQL and SQLite ignored the frame; and
        // Snowflake accepted it and returned a *different* number (2 where the others said 5). The
        // last is the reason this is an error rather than a warning — a query that answers
        // differently on one engine, with nothing to indicate it, is worse than one that fails.
        if (function.getWindow().getUpper() != null || function.getWindow().getLower() != null) {
            violations.add(new Violation("function", function, Range.of(iqlToContext, function),
                    "'" + function.getFunc() + "' cannot take a frame (ROWS BETWEEN …) — it looks at "
                            + "the whole partition by definition. Frames belong to aggregates used "
                            + "with OVER, such as sum(...) or avg(...)"));
        }
    }

    /**
     * {@code calendar_distance} is <strong>projection-only</strong>. Its SQL value is a decode-only wire
     * form — the two instants as epoch-seconds, reassembled into a calendar {@link
     * ai.koryki.jdbc.Interval} by the reader — so it is meaningless to any SQL operation. It may therefore
     * appear only as a {@code FETCH} output expression, never in {@code FILTER}, a comparison, {@code
     * ORDER}, {@code GROUP}, or as an argument to another function. A valid projection's first
     * non-{@link Expression} ancestor is its {@link Out}; anything else is a positioned error here.
     * Use {@code TIMESTAMP − TIMESTAMP} for an elapsed span that can be filtered or sorted.
     */
    private void checkProjectionOnly(Deque<Object> deque, Function function) {
        if (!"calendar_distance".equalsIgnoreCase(function.getFunc())) {
            return;
        }
        for (Object ancestor : deque) {          // nearest first; the function itself is not yet on the deque
            if (ancestor instanceof Expression) {
                continue;                        // pass-through wrapper
            }
            if (!(ancestor instanceof Out)) {
                violations.add(new Violation("type", function, Range.of(iqlToContext, function),
                        "calendar_distance(...) is projection-only — it may only be a FETCH output, not used "
                                + "in FILTER, a comparison, ORDER, GROUP or as an argument to another function "
                                + "(its value is a decode-only form). Use TIMESTAMP − TIMESTAMP for an elapsed "
                                + "span you can filter or sort."));
            }
            return;                              // decided at the first structural ancestor
        }
    }

    /**
     * A logical (boolean) expression is only valid as a <em>condition</em> of {@code case} — there it
     * renders into a {@code CASE WHEN <pred>} (a predicate position, portable across dialects). Used
     * anywhere else (a value argument, or a non-condition position) a standalone boolean value is not
     * portable, so it is a positioned error — mirroring how a bare identity argument is rejected
     * outside its slot.
     */
    private void checkLogicalArguments(Function function) {
        List<Expression> args = function.getArguments();
        int n = args.size();
        boolean isCase = "case".equalsIgnoreCase(function.getFunc());
        for (int i = 0; i < n; i++) {
            if (args.get(i).getLogical() == null) {
                continue;
            }
            if (!(isCase && CaseFunctionDefinition.isCondition(i, n))) {
                violations.add(new Violation("type", function, Range.of(iqlToContext, function),
                        "a boolean condition (logical expression) is only valid as a condition of case, "
                                + "not as argument " + (i + 1) + " of '" + function.getFunc() + "'"));
            }
        }
    }

    /**
     * Conditional branch reconciliation (if / iff / coalesce / nvl / …): the value branches must
     * reconcile to one output type — a shared family-group with a lossless common encoding — else it
     * is a positioned error here rather than the raw {@link ConditionalReconciler.ReconcileException}
     * thrown later at resolve/render time. A branch that cannot be typed is passed as untyped
     * (reconciliation skips it), so this never fires on a call it cannot fully type.
     */
    private void checkConditionalReconciliation(Function function) {
        if (!typeChecks() || scopes.isEmpty()) {
            return;
        }
        BranchedConditional def = branchedConditional(function.getFunc(), function.getArguments().size());
        if (def == null) {
            return;
        }
        ExpressionTypeResolver types = new ExpressionTypeResolver(resolver, scopes.peek(), functions);
        List<Expression> args = function.getArguments();
        List<TypeDescriptor> branches = new ArrayList<>();
        for (int i : def.branchIndices(args.size())) {
            branches.add(resolveOrNull(types, args.get(i)));
        }
        try {
            ConditionalReconciler.reconcile(branches);
        } catch (ConditionalReconciler.ReconcileException e) {
            violations.add(new Violation("type", function, Range.of(iqlToContext, function), e.getMessage()));
        }
    }

    private static TypeDescriptor resolveOrNull(ExpressionTypeResolver types, Expression e) {
        try {
            return types.resolve(e);
        } catch (RuntimeException unresolved) {
            return null;
        }
    }

    /** The {@link BranchedConditional} for {@code name}/{@code argCount}, or null if it is not one. */
    private BranchedConditional branchedConditional(String name, int argCount) {
        for (FunctionDefinition d : functions.overloads(name)) {
            if (d instanceof BranchedConditional bc
                    && (d.getSignature() == null || d.getSignature().matchesArity(argCount))) {
                return bc;
            }
        }
        return null;
    }

    /**
     * TIME ± DURATION admits only fixed clock units (h, m, s, ms): a time-of-day has
     * no calendar context, so a day / week / month / quarter / year component is a
     * validation error (docs/TEMPORAL.md, "Applying a duration → TIME"). Day and week
     * are calendar (variable-length) units, rejected here as much as month/year.
     */
    private void checkTimeArithmetic(Function function) {
        if (!typeChecks() || scopes.isEmpty()) {
            return;
        }
        String fn = function.getFunc();
        if (!MathOp.add.name().equals(fn) && !MathOp.minus.name().equals(fn)) {
            return;
        }
        List<Expression> args = function.getArguments();
        if (args.size() < 2) {
            return;
        }
        ExpressionTypeResolver types = new ExpressionTypeResolver(resolver, scopes.peek(), functions);
        if (args.stream().noneMatch(a -> isTime(a, types))) {
            return;
        }
        for (Expression a : args) {
            Duration dur = a.getDuration();
            if (dur == null) {
                continue;
            }
            for (Duration.Component c : dur.getComponents()) {
                if (isCalendarUnit(c.unit())) {
                    violations.add(new Violation("type", function, Range.of(iqlToContext, function),
                            "cannot add " + c.unit() + " to a TIME value — only fixed clock units "
                                    + "(h, m, s, ms) apply to a time-of-day"));
                    return;
                }
            }
        }
    }

    /**
     * The temporal anchor (DATE or TIMESTAMP) must be the left operand of + and −.
     * INTERVAL + DATE and INTERVAL − DATE are both rejected: a duration has no
     * calendar context of its own and cannot act as the base of temporal arithmetic.
     * Natural language always puts the anchor first: "3 months after DATE",
     * never "3 months ??? DATE".
     */
    private void checkAnchorFirst(Function function) {
        if (!typeChecks() || scopes.isEmpty()) {
            return;
        }
        String fn = function.getFunc();
        if (!MathOp.add.name().equals(fn) && !MathOp.minus.name().equals(fn)) {
            return;
        }
        List<Expression> args = function.getArguments();
        if (args.size() < 2) {
            return;
        }
        ExpressionTypeResolver types = new ExpressionTypeResolver(resolver, scopes.peek(), functions);
        TypeDescriptor left = resolveOrNull(types, args.get(0));
        TypeDescriptor right = resolveOrNull(types, args.get(1));
        if (left == null || right == null) {
            return;
        }
        if (CoreTypeFamily.INTERVAL.equals(left.getTypeFamily()) && isAnchor(right.getTypeFamily())) {
            violations.add(new Violation("type", function, Range.of(iqlToContext, function),
                    "INTERVAL " + fn + " DATE/TIMESTAMP is not valid — write DATE/TIMESTAMP " + fn + " INTERVAL"));
        }
    }

    private static boolean isAnchor(TypeFamily family) {
        return CoreTypeFamily.DATE.equals(family) || CoreTypeFamily.TIMESTAMP.equals(family);
    }

    private boolean isTime(Expression e, ExpressionTypeResolver types) {
        try {
            TypeDescriptor t = types.resolve(e);
            return t != null && CoreTypeFamily.TIME.equals(t.getTypeFamily());
        } catch (RuntimeException unresolved) {
            return false;
        }
    }

    private static boolean isCalendarUnit(Duration.Unit unit) {
        return switch (unit) {
            case DAY, WEEK, MONTH, QUARTAL, YEAR -> true;
            case MILLISECOND, SECOND, MINUTE, HOUR -> false;
        };
    }

    private void validateCall(Function function) {
        checkDivisionByZero(function);
        List<FunctionDefinition> set = functions.overloads(function.getFunc());
        if (set.isEmpty()) {
            // Unknown names render as-is by design (FunctionRegistry.defaultRender), which is a
            // deliberate escape hatch — but a silent one: the query works on the dialect it was
            // written against and fails elsewhere with a raw SQL error. Advisory, not fatal.
            violations.add(Violation.warning("function", function, Range.of(iqlToContext, function),
                    "'" + function.getFunc() + "' is not a KQL function; it is passed through to SQL "
                            + "unchanged and may not exist on other dialects")
                    .suggesting(Suggest.closest(function.getFunc(), functions.names())));
            return;
        }
        // The overload the call actually selects, not simply the first: a dialect can declare one
        // *arity* unsupported (MariaDB has trim(s) but no set-based trim(s, chars)), and the
        // supported overload is registered first, so set.get(0) would report the call as fine.
        // Falls back to the first entry when no arity matches — the arity violation below then
        // names the real problem.
        int arity = function.getArguments().size();
        FunctionDefinition selected = set.stream()
                .filter(d -> d.getSignature() != null && d.getSignature().matchesArity(arity))
                .findFirst()
                .orElse(set.get(0));
        if (selected.isUnsupported()) {
            // Category UNSUPPORTED, not "function": this is the one violation a caller may want to
            // treat as "this dialect cannot express the query" rather than "the query is wrong".
            // The test harnesses use it to skip a shared fixture on the dialects that declared the
            // function unsupported, instead of each fixture carrying a hand-written ignore= marker.
            violations.add(new Violation(Violation.UNSUPPORTED, function, Range.of(iqlToContext, function),
                    "function '" + function.getFunc() + "' is not supported by this dialect"
                            + (set.size() > 1 ? " with " + arity + " argument(s)" : "")
                            // A rejection that names a way out is worth more than one that does not;
                            // most have none, so the hint is optional rather than a required field.
                            + (selected.getUnsupportedHint() != null
                                    ? " — " + selected.getUnsupportedHint() : "")));
            return;
        }
        if (function.getWindow() != null && selected.isWindowUnsupported()) {
            // The counterpart of the check above, and it was missing: a dialect could declare that
            // a function has no OVER form (MySQL's GROUP_CONCAT, LISTAGG on Oracle and Snowflake),
            // but only {@code FunctionDefinition.render} acted on it — with a bare
            // UnsupportedOperationException carrying no position and no violation. That never
            // reached {@code ValidateException.isOnlyUnsupported}, so the shared fixture could not
            // be skipped automatically and needed a hand-written ignore= marker instead, which then
            // suppressed the SQL check as well. Same category, so both declarations behave alike.
            violations.add(new Violation(Violation.UNSUPPORTED, function, Range.of(iqlToContext, function),
                    "function '" + function.getFunc()
                            + "' does not support an OVER clause in this dialect"));
            return;
        }
        if (set.stream().anyMatch(d -> d.getSignature() == null)) {
            return; // definitions without arity metadata are unchecked
        }
        int argCount = arity;
        List<FunctionDefinition> arityMatches = set.stream()
                .filter(d -> d.getSignature().matchesArity(argCount))
                .toList();
        if (arityMatches.isEmpty()) {
            violations.add(new Violation("function", function, Range.of(iqlToContext, function),
                    "no overload of '" + function.getFunc() + "' matches " + argCount
                            + " argument(s) — candidates: " + candidates(set)));
            return;
        }
        // Type-overload ambiguity: registration (collides) guarantees same-arity overloads are
        // distinguished by family at some position, so a *typed* argument always selects exactly
        // one. A type-less argument (a NULL literal) at such a distinguishing position cannot —
        // the call would silently resolve to the first registered overload. Reject it instead.
        if (arityMatches.size() > 1) {
            List<Expression> args = function.getArguments();
            for (int i = 0; i < argCount; i++) {
                if (args.get(i).isNull() && distinguishes(arityMatches, i)) {
                    violations.add(new Violation("function", function, Range.of(iqlToContext, function),
                            "ambiguous call to '" + function.getFunc() + "': argument " + (i + 1)
                                    + " is untyped (NULL) but its overloads differ by type — candidates: "
                                    + candidates(arityMatches)));
                    return;
                }
            }
        }
        checkArgumentFamilies(function, arityMatches);
    }

    /**
     * Rejects a call whose typed operands no candidate overload accepts (e.g.
     * {@code round} on a TEXT value), via the family-aware {@link FunctionSignature#matches}.
     * An untyped operand (NULL or unresolvable) is a wildcard, so this never fires
     * on a call it cannot fully type.
     */
    private void checkArgumentFamilies(Function function, List<FunctionDefinition> arityMatches) {
        if (!typeChecks() || scopes.isEmpty()) {
            return;
        }
        ExpressionTypeResolver types = new ExpressionTypeResolver(resolver, scopes.peek(), functions);
        List<TypeFamily> callFamilies = new ArrayList<>();
        for (Expression arg : function.getArguments()) {
            TypeFamily family = null;
            try {
                TypeDescriptor type = types.resolve(arg);
                family = type != null ? type.getTypeFamily() : null;
            } catch (RuntimeException unresolved) {
                family = null;
            }
            callFamilies.add(family);
        }
        if (arityMatches.stream().noneMatch(d -> d.getSignature().matches(callFamilies))) {
            violations.add(new Violation("type", function, Range.of(iqlToContext, function),
                    "no overload of '" + function.getFunc() + "' accepts argument types "
                            + describe(callFamilies) + " — candidates: " + candidates(arityMatches)));
        }
    }

    private static String describe(List<TypeFamily> families) {
        return families.stream().map(f -> f == null ? "?" : f.name())
                .collect(java.util.stream.Collectors.joining(", ", "(", ")"));
    }

    /** Whether the overloads declare more than one distinct family at argument position {@code i}. */
    private static boolean distinguishes(List<FunctionDefinition> overloads, int i) {
        return overloads.stream().map(d -> d.getSignature().familyAt(i)).distinct().count() > 1;
    }

    private static String candidates(List<FunctionDefinition> set) {
        return set.stream()
                .map(d -> d.getName() + d.getSignature())
                .collect(java.util.stream.Collectors.joining(", "));
    }
    @Override
    public boolean visit(Deque<Object> deque, Select select) {

        SqlQueryRenderer.collectOut(select).stream().forEach(o -> {

            Expression e = o.getExpression();

            boolean a = isAggregateOfColumnOrIdentity(e);
            boolean scalar = isScalarOfColumn(e);
            if (a && scalar) {
                violations.add(new Violation("function", e, Range.of(iqlToContext, e), "invalid aggregation"));
            }
        });
        if (typeChecks()) {
            scopes.push((scopes.isEmpty() ? rootVisibility : scopes.peek()).child(select));
        }
        return true;
    }

    @Override
    public void leave(Select select) {
        if (typeChecks() && !scopes.isEmpty()) {
            scopes.pop();
        }
    }

    @Override
    public boolean visit(Deque<Object> deque, UnaryLogicalExpression logicalExpression) {

            boolean aggregat = isAggregate(logicalExpression);
            boolean scalar = isScalar(logicalExpression);
            if (aggregat && scalar) {
                violations.add(new Violation("function", logicalExpression, Range.of(iqlToContext, logicalExpression), "invalid aggregation"));
            }
            checkOperatorArity(logicalExpression);
            checkOperandFamilies(logicalExpression);
            checkIntervalComparison(logicalExpression);
            checkKnownOperator(logicalExpression);
            checkComparisonReconciliation(logicalExpression);
            checkBarePredicate(logicalExpression);
            return true;
        }

    /**
     * A literal zero divisor. Unlike a column that merely happens to contain a zero — which the
     * renderer guards with {@code NULLIF} so every dialect answers NULL — this one is decidable
     * here, and there is no reading of the query under which it was meant. Reporting it at write
     * time beats a report full of blanks.
     *
     * <p>Only a literal is checked. {@code x / (3 - 3)} is left alone rather than constant-folded:
     * the guard makes it NULL, and a validator that half-evaluates arithmetic would be a worse
     * thing to own than the case it catches.
     */
    private void checkDivisionByZero(Function function) {
        if (!MathOp.divide.name().equals(function.getFunc())) {
            return;
        }
        List<Expression> args = function.getArguments();
        for (int i = 1; i < args.size(); i++) {
            Number divisor = args.get(i).getNumber();
            if (divisor != null && divisor.doubleValue() == 0) {
                violations.add(new Violation("type", function, Range.of(iqlToContext, function),
                        "division by zero"));
                return;
            }
        }
    }

    /**
     * The grammar's {@code operator : … | custom=ID} rule is an open escape hatch: an operator with
     * no catalog entry renders verbatim into the SQL (see {@code SqlDialect.renderComparison}'s
     * fallback). That is deliberate, but it was silent — unlike the same escape hatch for functions
     * in {@link #validateCall}. Advisory, not fatal, and worded the same way.
     *
     * <p>A spelling that differs from a real operator only in case is a <em>fatal</em> error, not
     * that warning. KQL keywords are upper case throughout — the lexer defines them as literals, so
     * {@code find} is already a parse error — and {@code DISTINCT} in the {@code operator} rule is
     * one of them. A lower-case {@code distinct} therefore does not reach that alternative at all;
     * it becomes a <em>custom</em> operator that happens to share the name. What followed was worse
     * than the escape hatch it looked like: the pass-through never happened, because
     * {@code negatedOperatorTemplate} normalises its argument and handed back the ANSI
     * {@code IS NOT DISTINCT FROM} — while the MariaDB and Oracle overrides, which compare exactly,
     * did not fire. Both engines reject that form (ERROR 1064, ORA-00908), and the warning that
     * would have hinted at it was suppressed by the same upper-case retry. Nobody writes a custom
     * operator whose name collides with a built-in one; they mistyped the built-in.
     */
    private void checkKnownOperator(UnaryLogicalExpression node) {
        String op = node.getOp();
        if (functions == null || op == null || op.isBlank() || node.getLeft() == null) {
            return;
        }
        if (!functions.overloads(op).isEmpty()) {
            return;
        }
        String canonical = op.toUpperCase(Locale.ROOT);
        if (!canonical.equals(op) && isOperatorName(canonical)) {
            violations.add(new Violation("function", node, Range.of(iqlToContext, node),
                    "'" + op + "' is not a KQL operator — write it as '" + canonical
                            + "'; KQL keywords are upper case"));
            return;
        }
        violations.add(Violation.warning("function", node, Range.of(iqlToContext, node),
                "'" + op + "' is not a KQL operator; it is passed through to SQL "
                        + "unchanged and may not exist on other dialects"));
    }

    /** True if {@code name} is registered as an operator (any fixity other than a plain call). */
    private boolean isOperatorName(String name) {
        return functions.overloads(name).stream().anyMatch(d -> d.getFixity() != Fixity.PREFIX);
    }

    /** Ordered relational operators (a total order is required); the rest are equality/membership. */
    private static final java.util.Set<String> ORDERED = java.util.Set.of("<", "<=", ">", ">=", "BETWEEN");


    /**
     * Ordered comparison of INTERVAL operands (docs/TEMPORAL.md, "Comparisons"):
     * <ul>
     *   <li>a string-encoded interval (INTERVAL_FROM_STRING) is not SQL-orderable — reject;</li>
     *   <li>operands must reduce to the <em>same</em> unit class (clock / days / months);
     *       a mixed-unit duration or two different classes have no anchor-independent order.</li>
     * </ul>
     * Native interval columns carry a DB-defined order, so they are left unconstrained.
     * Only ordered operators are restricted; {@code =}/{@code !=} compare component-wise.
     */
    private void checkIntervalComparison(UnaryLogicalExpression node) {
        if (!typeChecks() || scopes.isEmpty() || node.getOp() == null || node.getLeft() == null) {
            return;
        }
        if (!ORDERED.contains(node.getOp().trim().toUpperCase(Locale.ROOT))) {
            return;
        }
        ExpressionTypeResolver types = new ExpressionTypeResolver(resolver, scopes.peek(), functions);
        List<Expression> operands = new ArrayList<>();
        operands.add(node.getLeft());
        operands.addAll(node.getRight());

        boolean anyInterval = false;
        java.util.EnumSet<IntervalUnitClass> classes = java.util.EnumSet.noneOf(IntervalUnitClass.class);
        for (Expression e : operands) {
            Duration dur = e.getDuration();
            if (dur != null) {
                anyInterval = true;
                java.util.EnumSet<IntervalUnitClass> dc = durationClasses(dur);
                if (dc.size() > 1) {
                    intervalViolation(node, "a mixed-unit DURATION '" + dur + "' has no anchor-independent order — "
                            + "order is defined only within one unit class (clock / days / months)");
                    return;
                }
                classes.addAll(dc);
                continue;
            }
            TypeDescriptor t;
            try {
                t = types.resolve(e);
            } catch (RuntimeException unresolved) {
                continue;
            }
            if (t == null || !CoreTypeFamily.INTERVAL.equals(t.getTypeFamily())) {
                continue;
            }
            anyInterval = true;
            TypeEncoding enc = t.getTypeEncoding();
            if (enc instanceof IntervalStringEncoding || CoreTypeEncoding.INTERVAL_FROM_STRING.equals(enc)) {
                intervalViolation(node, "ordered comparison (<, >, BETWEEN) is not supported on a string-encoded "
                        + "INTERVAL (INTERVAL_FROM_STRING) — store it as a numeric INTERVAL:<unit> or a native "
                        + "interval, or compare with = / !=");
                return;
            }
            if (enc instanceof IntervalTypeEncoding it) {
                classes.add(IntervalUnitClass.of(it.getUnit()));
            }
            // native interval (CoreTypeEncoding.INTERVAL_* or no encoding): DB-defined order — unconstrained
        }
        if (anyInterval && classes.size() > 1) {
            intervalViolation(node, "cannot order DURATIONs of different unit classes " + classes
                    + " — clock, day and month amounts have no anchor-independent common order");
        }
    }

    private void intervalViolation(UnaryLogicalExpression node, String message) {
        violations.add(new Violation("type", node, Range.of(iqlToContext, node), message));
    }

    private static java.util.EnumSet<IntervalUnitClass> durationClasses(Duration dur) {
        java.util.EnumSet<IntervalUnitClass> s = java.util.EnumSet.noneOf(IntervalUnitClass.class);
        for (Duration.Component c : dur.getComponents()) {
            s.add(switch (c.unit()) {
                case MILLISECOND, SECOND, MINUTE, HOUR -> IntervalUnitClass.CLOCK;
                case DAY, WEEK -> IntervalUnitClass.DAY;
                case MONTH, QUARTAL, YEAR -> IntervalUnitClass.MONTH;
            });
        }
        return s;
    }


    /**
     * Enforces an operator's declared argument families against the resolved
     * operand types (e.g. LIKE requires TEXT on both sides). The operator is
     * resolved by surface text, case-insensitively, so the constraint holds for
     * {@code LIKE} and {@code like} alike; a {@code null} declared or resolved
     * family is a wildcard, and an operand that cannot be typed is skipped (no
     * false positive).
     */
    /**
     * An operator's arity against its signature.
     *
     * <p>Function calls have long been checked, operators never -- the grammar makes the operand
     * list optional for EVERY operator ({@code operator (expression? | ...)}), so any arity reached
     * the renderer. {@code FILTER x IN} thereby produced {@code IN ()}: invalid SQL on all eight
     * dialects, without a violation and without a warning. The remaining cases ended in a bare
     * exception from SqlTemplate that shows the author a template instead of a place in their
     * query.
     *
     * <p>Reports a positioned violation instead. An unknown operator (the grammar's open
     * {@code custom=ID}) has no signature and is skipped.
     */
    private void checkOperatorArity(UnaryLogicalExpression node) {
        if (!typeChecks() || node.getOp() == null || node.getLeft() == null) {
            return;
        }
        FunctionSignature signature = operatorSignature(node.getOp());
        if (signature == null) {
            return;
        }
        int arity = 1 + node.getRight().size();
        if (!signature.matchesArity(arity)) {
            violations.add(new Violation("function", node, Range.of(iqlToContext, node),
                    "operator '" + node.getOp() + "' takes "
                            + (signature.maxArgs() == Integer.MAX_VALUE
                                    ? "at least " + signature.minArgs() + " operand(s)"
                                    : signature.minArgs() == signature.maxArgs()
                                            ? signature.minArgs() + " operand(s)"
                                            : signature.minArgs() + " to " + signature.maxArgs() + " operands")
                            + " but got " + arity));
        }
    }

    private void checkOperandFamilies(UnaryLogicalExpression node) {
        if (!typeChecks() || scopes.isEmpty() || node.getOp() == null || node.getLeft() == null) {
            return;
        }
        FunctionSignature signature = operatorSignature(node.getOp());
        if (signature == null) {
            return;
        }
        ExpressionTypeResolver types = new ExpressionTypeResolver(resolver, scopes.peek(), functions);
        requireFamily(node, signature, 0, node.getLeft(), types);
        List<Expression> right = node.getRight();
        for (int i = 0; i < right.size(); i++) {
            requireFamily(node, signature, i + 1, right.get(i), types);
        }
    }

    /**
     * A bare predicate — a boolean expression standing alone, with no operator — must actually be
     * BOOLEAN. The grammar cannot tell {@code FILTER starts_with(c.name, 'A')} from
     * {@code FILTER c.company_name}; only the type can, and without this the second would render as
     * a non-boolean WHERE clause and fail at the database with a message pointing nowhere useful.
     *
     * <p>An operand that cannot be typed is skipped, as everywhere else in this class.
     */
    private void checkBarePredicate(UnaryLogicalExpression node) {
        if (!typeChecks() || scopes.isEmpty() || node.getLeft() == null) {
            return;
        }
        boolean bare = node.getOp() == null && node.getRight().isEmpty()
                && node.getPlaceholder() == null && node.getExists() == null && node.getNode() == null;
        if (!bare) {
            return;
        }
        TypeDescriptor t = resolveOrNull(new ExpressionTypeResolver(resolver, scopes.peek(), functions), node.getLeft());
        if (t == null || t.getTypeFamily() == null || CoreTypeFamily.BOOLEAN.equals(t.getTypeFamily())) {
            return;
        }
        violations.add(new Violation("type", node, Range.of(iqlToContext, node),
                "a filter condition must be a yes/no test, but this is "
                        + t.getTypeFamily().name() + " — compare it with an operator, e.g. `= 'x'` or `> 0`"));
    }

    /** Comparison operators whose operands must meet in one type; all declare {@code Families.ANY}. */
    private static final java.util.Set<String> COMPARABLE =
            java.util.Set.of("=", "<>", "<", "<=", ">", ">=", "BETWEEN", "IN", "DISTINCT");

    /**
     * Cross-operand type compatibility for the comparison operators.
     *
     * <p>{@link #checkOperandFamilies} only tests each operand against its <em>declared</em> family,
     * and every operator here declares {@code Families.ANY} — so nothing checked that the two sides
     * can meet at all, and {@code o.order_date = 'foo'} validated silently. The question is exactly
     * the one {@link ConditionalReconciler} already answers for {@code case}/{@code coalesce}
     * branches (see {@link #checkConditionalReconciliation}): do these types reconcile to one type,
     * with numeric widening and a lossless common encoding?
     *
     * <p>The two failure modes are not equally serious, so they are not reported alike:
     * <ul>
     *   <li>{@code FAMILY_GROUP} — comparing a DATE to TEXT, a number to TEXT, a TIME to a
     *       TIMESTAMP — is an <b>error</b>. There is no reading of the query under which it was
     *       intended, and letting it through is how {@code BETWEEN '1997-01-01' AND '1997-12-31'}
     *       reached the docs comparing a DATE against two strings.</li>
     *   <li>{@code ENCODING} — one family, two storage representations with no lossless meeting
     *       point — is a <b>warning</b>. The representations that <em>do</em> have one are converted
     *       by the renderer (see {@code SqlDialect.renderComparison}), so what is left here really
     *       is "nothing can be done", not "not implemented yet".</li>
     * </ul>
     *
     * <p>{@code ISNULL} (one operand) and {@code LIKE} (already family-checked) are excluded, and an
     * operand that cannot be typed — a NULL literal included — is skipped rather than guessed at.
     */
    private void checkComparisonReconciliation(UnaryLogicalExpression node) {
        if (!typeChecks() || scopes.isEmpty() || node.getOp() == null || node.getLeft() == null) {
            return;
        }
        if (!COMPARABLE.contains(node.getOp().trim().toUpperCase(Locale.ROOT))) {
            return;
        }
        ExpressionTypeResolver types = new ExpressionTypeResolver(resolver, scopes.peek(), functions);
        List<TypeDescriptor> operands = new ArrayList<>();
        operands.add(resolveOrNull(types, node.getLeft()));
        for (Expression e : node.getRight()) {
            operands.add(resolveOrNull(types, e));
        }
        // a single typed operand has nothing to reconcile against
        if (operands.stream().filter(java.util.Objects::nonNull).count() < 2) {
            return;
        }
        try {
            ConditionalReconciler.reconcile(operands);
        } catch (ConditionalReconciler.ReconcileException e) {
            String message = "operands of '" + node.getOp() + "' do not share a common type: " + e.getMessage();
            Range range = Range.of(iqlToContext, node);
            violations.add(e.getKind() == ConditionalReconciler.ReconcileException.Kind.FAMILY_GROUP
                    ? new Violation("type", node, range, message)
                    : Violation.warning("type", node, range, message));
        }
    }

    private FunctionSignature operatorSignature(String op) {
        for (String key : List.of(op, op.toUpperCase(Locale.ROOT))) {
            for (FunctionDefinition d : functions.overloads(key)) {
                if (d.getFixity() != Fixity.PREFIX && d.getSignature() != null) {
                    return d.getSignature();
                }
            }
        }
        return null;
    }

    private void requireFamily(UnaryLogicalExpression node, FunctionSignature signature,
            int position, Expression operand, ExpressionTypeResolver types) {
        TypeFamily declared = signature.familyAt(position);
        if (declared == null) {
            return;
        }
        TypeFamily got;
        try {
            TypeDescriptor type = types.resolve(operand);
            got = type != null ? type.getTypeFamily() : null;
        } catch (RuntimeException unresolved) {
            got = null;
        }
        if (got != null && !declared.accepts(got)) {
            violations.add(new Violation("type", node, Range.of(iqlToContext, node),
                    "operator '" + node.getOp() + "' requires " + declared.name()
                            + " at argument " + (position + 1) + " but got " + got.name()));
        }
    }

    private boolean isAggregate(UnaryLogicalExpression logicalExpression) {
        if (logicalExpression.getLeft() != null) {
            boolean leftAggregat = isAggregateOfColumnOrIdentity(logicalExpression.getLeft());
            if (leftAggregat) {
                return true;
            }
        }

        // EXISTS is a subquery boundary: aggregates inside it never aggregate the outer select
        if (logicalExpression.getExists() != null) {
            return false;
        }

        boolean rightAggregat = logicalExpression.getRight().stream().anyMatch(r -> isAggregateOfColumnOrIdentity(r));
        if (rightAggregat) {
            return true;
        }

        if (logicalExpression.getNode() != null) {
            boolean nodeAggregat = isAggregate(logicalExpression.getNode());
            if (nodeAggregat) {
                return true;
            }
        }
        return false;
    }

    private boolean isScalar(UnaryLogicalExpression logicalExpression) {
        if (logicalExpression.getLeft() != null) {
            boolean leftScalar = isScalarOfColumn(logicalExpression.getLeft());
            if (leftScalar) {
                return true;
            }
        }
        // TODO exists

        boolean rightScalar = logicalExpression.getRight().stream().anyMatch(r -> isScalarOfColumn(r));
        if (rightScalar) {
            return true;
        }

        if (logicalExpression.getNode() != null) {
            boolean nodeScalar = isScalar(logicalExpression.getNode());
            if (nodeScalar) {
                return true;
            }
        }
        if (logicalExpression.getExists() != null) {
            boolean existsScalar = isScalar(logicalExpression.getExists());
            if (existsScalar) {
                return true;
            }
        }
        return false;
    }

    public boolean isAggregate(LogicalExpression logicalExpression) {

        if (logicalExpression.getUnaryRelationalExpression() != null) {
            return isAggregate(logicalExpression.getUnaryRelationalExpression());
        }

        return logicalExpression.getChildren().stream().anyMatch(l -> isAggregate(l));
    }

    public boolean isScalar(LogicalExpression logicalExpression) {

        if (logicalExpression.getUnaryRelationalExpression() != null) {
            return isScalar(logicalExpression.getUnaryRelationalExpression());
        }

        return logicalExpression.getChildren().stream().anyMatch(l -> isScalar(l));
    }

//    public boolean isAggregate(Join join) {
//
//        if (isAggregate(join.getSource())) {
//            return true;
//        }
//        return join.getJoin().stream().anyMatch(j -> isAggregate(j));
//    }

//    public boolean isAggregate(Source table) {
//        if (table.getFilter() != null && isAggregate(table.getFilter())) {
//            return true;
//        }
//        if (table.getHaving() != null && isAggregate(table.getHaving())) {
//            return true;
//        }
//
//        return false;
//    }

    public boolean isScalar(Exists exists) {

        if (exists.getStart().getFilter() != null && isScalar(exists.getStart().getFilter())) {
            return true;
        }
        return exists.getJoin().stream().anyMatch(j -> isScalar(j));
    }

    public boolean isScalar(Join join) {

        if (isScalar(join.getSource())) {
            return true;
        }
        return join.getJoin().stream().anyMatch(j -> isScalar(j));
    }

    public boolean isScalar(Source table) {
        if (table.getFilter() != null && isScalar(table.getFilter())) {
            return true;
        }
        if (table.getHaving() != null && isScalar(table.getHaving())) {
            return true;
        }

        return false;
    }

    public static boolean isAggregate(Function function) {

        // a call with an OVER clause is a window function: it computes per row without
        // collapsing them, so it must not trigger GROUP BY inference or a filter→having move
        if (function.getWindow() != null) {
            return false;
        }
        return StandardFunctions.isAggregate(function.getFunc());
    }

    /** True if the expression contains a windowed call (OVER clause) at any depth. */
    public static boolean containsWindow(Expression expression) {
        if (expression.getFunction() != null) {
            Function f = expression.getFunction();
            if (f.getWindow() != null) {
                return true;
            }
            return f.getArguments().stream().anyMatch(FunctionValidator::containsWindow);
        }
        if (expression.getLogical() != null) {
            return logicalOperands(expression.getLogical()).stream().anyMatch(FunctionValidator::containsWindow);
        }
        return false;
    }

    public static boolean isAggregateOfColumnOrIdentity(Expression expression) {

        if (expression.getFunction() != null && isAggregateOfColumnOrIdentity(expression.getFunction())) {
            return true;
        }
        if (expression.getLogical() != null) {
            return logicalOperands(expression.getLogical()).stream()
                    .anyMatch(FunctionValidator::isAggregateOfColumnOrIdentity);
        }
        return false;
    }

    public static boolean isAggregateOfColumnOrIdentity(Function function) {
        if (isAggregate(function) && hasColumnOrIdentity(function, true)) {
            return true;
        }
        return function.getArguments().stream().anyMatch(e -> isAggregateOfColumnOrIdentity(e));
    }

    private boolean isScalarOfColumn(Expression expression) {

        if (expression.getField() != null) {
            return true;
        } else if (expression.getFunction() != null) {
            return isScalarOfColumn(expression.getFunction());
        } else if (expression.getLogical() != null) {
            return logicalOperands(expression.getLogical()).stream().anyMatch(this::isScalarOfColumn);
        } else {
            return false;
        }
    }

    private boolean isScalarOfColumn(Function function) {

        if (hasColumnOrIdentity(function, false)) {
            return !isAggregate(function) ;
        }

        boolean result = function.getArguments().stream()
                .filter(e -> e.getFunction() != null)
                .map(e -> e.getFunction())
                .filter(f -> !isAggregate(f))
                .anyMatch(f -> isScalarOfColumn(f));
        return result;
    }

    private static boolean hasColumnOrIdentity(Function function, boolean aggregate) {
        return function.getArguments().stream().anyMatch(e -> hasColumnOrIdentity(e, aggregate));
    }

    /** Whether an argument (incl. a logical-expression condition's operands) carries a column or identity. */
    private static boolean hasColumnOrIdentity(Expression e, boolean aggregate) {
        if (e.getField() != null || e.getIdentity() != null) {
            return true;
        }
        if (e.getFunction() != null && (aggregate || !isAggregate(e.getFunction()))) {
            return hasColumnOrIdentity(e.getFunction(), aggregate);
        }
        if (e.getLogical() != null) {
            return logicalOperands(e.getLogical()).stream().anyMatch(op -> hasColumnOrIdentity(op, aggregate));
        }
        return false;
    }

    /** All operand expressions inside a logical (boolean) condition — the left/right of each comparison. */
    private static List<Expression> logicalOperands(LogicalExpression logical) {
        List<Expression> operands = new ArrayList<>();
        collectLogicalOperands(logical, operands);
        return operands;
    }

    private static void collectLogicalOperands(LogicalExpression logical, List<Expression> out) {
        if (logical == null) {
            return;
        }
        if (logical.isValue()) {
            UnaryLogicalExpression unary = logical.getUnaryRelationalExpression();
            if (unary != null) {
                if (unary.getLeft() != null) {
                    out.add(unary.getLeft());
                }
                out.addAll(unary.getRight());
                collectLogicalOperands(unary.getNode(), out);   // parenthesized nested logical
            }
        } else {
            for (LogicalExpression child : logical.getChildren()) {
                collectLogicalOperands(child, out);
            }
        }
    }

    @Override
    public List<Violation> collect() {
        return violations;
    }
}
