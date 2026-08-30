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
package ai.koryki.sqlite.iql.validate;

import ai.koryki.antlr.Range;
import ai.koryki.iql.Collector;
import ai.koryki.iql.Visitor;
import ai.koryki.catalog.types.WallClockEncoding;
import ai.koryki.iql.IQLVisibilityContext;
import ai.koryki.iql.query.Exists;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Select;
import ai.koryki.iql.typing.ExpressionTypeResolver;
import ai.koryki.iql.validate.Violation;
import org.antlr.v4.runtime.RuleContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * SQLite-specific validation — the dialect analogue of the core validators, a {@link Collector}
 * run via {@code Walker.apply}. It rejects constructs SQLite cannot express, so a query fails
 * with a located {@link Violation} instead of an opaque runtime error from the driver.
 *
 * <p>Currently the only rule: SQLite has no {@code GROUP BY ROLLUP}. Other dialects spell it
 * differently — MariaDB uses a trailing {@code WITH ROLLUP} via
 * {@code SqlDialect#rollupSuffix()} — but that hook only chooses the wording; there is none for
 * refusing it, so without this check SQLite receives the standard {@code ROLLUP (...)} and the
 * driver rejects the finished statement.
 *
 * <p>The category is {@link Violation#UNSUPPORTED}, not something like {@code "rollup"}: that is
 * what marks the failure as "this dialect cannot express this query" rather than "this query is
 * wrong", and it is what lets a shared fixture be skipped here instead of failing the suite.
 */
public class SqliteValidator implements Visitor, Collector<List<Violation>> {

    private final List<Violation> violations = new ArrayList<>();
    private final Map<Object, RuleContext> iqlToContext;
    private final ai.koryki.iql.validate.ValidationContext context;

    private final Deque<IQLVisibilityContext> scopes = new ArrayDeque<>();

    public SqliteValidator(ai.koryki.iql.validate.ValidationContext context) {
        this.context = context;
        this.iqlToContext = context.iqlToContext();
    }

    /**
     * A wall-clock(zone) column carries a naive value plus the zone it was written in, so reading it
     * means converting that zone to the model zone — {@code CONVERT_TZ}, {@code AT TIME ZONE} and
     * their kin. SQLite ships no time-zone database at all, and its {@code 'localtime'} modifier
     * follows the machine's setting rather than a named zone, so the conversion has no expression
     * here. The other seven dialects all implement it.
     *
     * <p>This used to surface as {@code SqlDialect.wallClockToModelZone}'s default throwing a bare
     * {@code KorykiaiException} while rendering — no position, no violation, and so a hand-written
     * {@code ignore=sqlite} marker on the fixture, which suppressed its SQL check along with it. It
     * is the same shape as {@code at_zone}/{@code to_utc}, which are functions and could simply be
     * declared unsupported; a column's <em>storage</em> has no such declaration, and saying it here
     * needs the schema — which is why {@code validators} now receives a
     * {@link ai.koryki.iql.validate.ValidationContext} rather than the position map alone.
     */
    @Override
    public boolean visit(Deque<Object> deque, Expression expression) {
        if (expression.getField() == null || scopes.isEmpty()) {
            return true;
        }
        var type = resolveOrNull(expression);
        if (type != null && type.getTypeEncoding() instanceof WallClockEncoding wc) {
            violations.add(new Violation(Violation.UNSUPPORTED, expression,
                    Range.of(iqlToContext, expression),
                    "column '" + expression.getField().getName() + "' is stored as a wall-clock in "
                            + wc.getZone().getId() + ", and SQLite has no time-zone database to "
                            + "convert it to the model zone"));
        }
        return true;
    }

    /** Best-effort: a field that cannot be typed is not this rule's business. */
    private ai.koryki.catalog.types.TypeDescriptor resolveOrNull(Expression expression) {
        try {
            return new ExpressionTypeResolver(context.resolver(), scopes.peek(), context.functions())
                    .resolve(expression);
        } catch (RuntimeException unresolved) {
            return null;
        }
    }

    @Override
    public boolean visit(Deque<Object> deque, Exists exists) {
        scopes.push(scope().child(exists));
        return true;
    }

    @Override
    public void leave(Exists exists) {
        if (!scopes.isEmpty()) {
            scopes.pop();
        }
    }

    @Override
    public void leave(Select select) {
        if (!scopes.isEmpty()) {
            scopes.pop();
        }
    }

    private IQLVisibilityContext scope() {
        return scopes.isEmpty() ? context.visibility() : scopes.peek();
    }

    @Override
    public boolean visit(Deque<Object> deque, Select select) {
        scopes.push(scope().child(select));
        if (select.isRollup()) {
            violations.add(new Violation(Violation.UNSUPPORTED, select,
                    Range.of(iqlToContext, select),
                    "ROLLUP is not supported by SQLite"));
        }
        return true;
    }

    @Override
    public List<Violation> collect() {
        return violations;
    }
}
