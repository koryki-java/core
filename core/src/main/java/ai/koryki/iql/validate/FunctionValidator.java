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
import ai.koryki.catalog.schema.types.CoreTypeEncoding;
import ai.koryki.catalog.schema.types.CoreTypeFamily;
import ai.koryki.catalog.schema.types.IntervalStringEncoding;
import ai.koryki.catalog.schema.types.IntervalTypeEncoding;
import ai.koryki.catalog.schema.types.TypeDescriptor;
import ai.koryki.catalog.schema.types.TypeEncoding;
import ai.koryki.catalog.schema.types.TypeFamily;
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
import ai.koryki.iql.functions.FunctionSignature;
import ai.koryki.iql.functions.MathOp;
import ai.koryki.iql.functions.StandardFunctions;
import ai.koryki.iql.query.*;
import ai.koryki.iql.types.ExpressionTypeResolver;
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

    public FunctionValidator(Map<Object, RuleContext> iqlToContext) {
        this(iqlToContext, null);
    }

    /** @param functions dialect function catalog for arity/unsupported checks; null = skip those checks */
    public FunctionValidator(Map<Object, RuleContext> iqlToContext, FunctionCatalog functions) {
        this(iqlToContext, functions, null, null);
    }

    /**
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
        return true;
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
                violations.add(new Violation("type", function, Range.range(iqlToContext.get(function)),
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
                violations.add(new Violation("type", function, Range.range(iqlToContext.get(function)),
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
            violations.add(new Violation("type", function, Range.range(iqlToContext.get(function)), e.getMessage()));
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
                    violations.add(new Violation("type", function, Range.range(iqlToContext.get(function)),
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
            violations.add(new Violation("type", function, Range.range(iqlToContext.get(function)),
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
        List<FunctionDefinition> set = functions.overloads(function.getFunc());
        if (set.isEmpty()) {
            return; // unknown names render as-is by design (native passthrough)
        }
        if (set.get(0).isUnsupported()) {
            violations.add(new Violation("function", function, Range.range(iqlToContext.get(function)),
                    "function '" + function.getFunc() + "' is not supported by this dialect"));
            return;
        }
        if (set.stream().anyMatch(d -> d.getSignature() == null)) {
            return; // definitions without arity metadata are unchecked
        }
        int argCount = function.getArguments().size();
        List<FunctionDefinition> arityMatches = set.stream()
                .filter(d -> d.getSignature().matchesArity(argCount))
                .toList();
        if (arityMatches.isEmpty()) {
            violations.add(new Violation("function", function, Range.range(iqlToContext.get(function)),
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
                    violations.add(new Violation("function", function, Range.range(iqlToContext.get(function)),
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
            violations.add(new Violation("type", function, Range.range(iqlToContext.get(function)),
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
                violations.add(new Violation("function", e, Range.range(iqlToContext.get(e)), "invalid aggregation"));
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
                violations.add(new Violation("function", logicalExpression, Range.range(iqlToContext.get(logicalExpression)), "invalid aggregation"));
            }
            checkOperandFamilies(logicalExpression);
            checkIntervalComparison(logicalExpression);
            return true;
        }

    /** Ordered relational operators (a total order is required); the rest are equality/membership. */
    private static final java.util.Set<String> ORDERED = java.util.Set.of("<", "<=", ">", ">=", "BETWEEN");

    /** Orderable interval unit classes — each has an anchor-independent order; across classes there is none. */
    private enum IvClass { CLOCK, DAY, MONTH }

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
        java.util.EnumSet<IvClass> classes = java.util.EnumSet.noneOf(IvClass.class);
        for (Expression e : operands) {
            Duration dur = e.getDuration();
            if (dur != null) {
                anyInterval = true;
                java.util.EnumSet<IvClass> dc = durationClasses(dur);
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
                classes.add(unitClass(it.getUnit()));
            }
            // native interval (CoreTypeEncoding.INTERVAL_* or no encoding): DB-defined order — unconstrained
        }
        if (anyInterval && classes.size() > 1) {
            intervalViolation(node, "cannot order DURATIONs of different unit classes " + classes
                    + " — clock, day and month amounts have no anchor-independent common order");
        }
    }

    private void intervalViolation(UnaryLogicalExpression node, String message) {
        violations.add(new Violation("type", node, Range.range(iqlToContext.get(node)), message));
    }

    private static java.util.EnumSet<IvClass> durationClasses(Duration dur) {
        java.util.EnumSet<IvClass> s = java.util.EnumSet.noneOf(IvClass.class);
        for (Duration.Component c : dur.getComponents()) {
            s.add(switch (c.unit()) {
                case MILLISECOND, SECOND, MINUTE, HOUR -> IvClass.CLOCK;
                case DAY, WEEK -> IvClass.DAY;
                case MONTH, QUARTAL, YEAR -> IvClass.MONTH;
            });
        }
        return s;
    }

    private static IvClass unitClass(java.time.temporal.ChronoUnit unit) {
        return switch (unit) {
            case YEARS, MONTHS -> IvClass.MONTH;
            case WEEKS, DAYS -> IvClass.DAY;
            default -> IvClass.CLOCK;   // HOURS .. NANOS
        };
    }

    /**
     * Enforces an operator's declared argument families against the resolved
     * operand types (e.g. LIKE requires TEXT on both sides). The operator is
     * resolved by surface text, case-insensitively, so the constraint holds for
     * {@code LIKE} and {@code like} alike; a {@code null} declared or resolved
     * family is a wildcard, and an operand that cannot be typed is skipped (no
     * false positive).
     */
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
            violations.add(new Violation("type", node, Range.range(iqlToContext.get(node)),
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

        return StandardFunctions.isAggregate(function.getFunc());
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
