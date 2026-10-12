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
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.iql.Collector;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.Visitor;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
import ai.koryki.iql.query.Select;
import ai.koryki.iql.query.Selector;
import ai.koryki.iql.typing.ExpressionTypeResolver;
import ai.koryki.iql.typing.SelectorException;
import ai.koryki.iql.typing.Selectors;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.RuleContext;

/**
 * Reports a use of nested values the engine cannot express, according to what the dialect declares
 * in {@link SqlDialect#nestedSupport()}: a selector on a kind of value it does not have, a JSON
 * position counted from the end where its path cannot, a lambda over a list made from a JSON array
 * where it cannot be built. The dialects keep their own refusals at render time as the backstop;
 * this makes the same refusal a violation of the query, with a position and before any SQL is
 * built.
 *
 * <p>A chain that does not fit its types is {@link SelectorValidator}'s to report, not this one's.
 */
public class NestedSupportValidator implements Visitor, Collector<List<Violation>> {

    private final List<Violation> violations = new ArrayList<>();
    private final Map<Object, RuleContext> iqlToContext;
    private final SqlDialect.NestedSupport support;
    private final ValidationContext context;
    private final Deque<ai.koryki.iql.IQLVisibilityContext> scopes = new ArrayDeque<>();

    public NestedSupportValidator(ValidationContext context, SqlDialect dialect) {
        this.context = context;
        this.iqlToContext = context.iqlToContext();
        this.support = dialect.nestedSupport();
    }

    @Override
    public boolean visit(Deque<Object> deque, Select select) {
        scopes.push((scopes.isEmpty() ? context.visibility() : scopes.peek()).child(select));
        return true;
    }

    @Override
    public void leave(Select select) {
        if (!scopes.isEmpty()) {
            scopes.pop();
        }
    }

    private ExpressionTypeResolver types() {
        return scopes.isEmpty()
                ? null
                : new ExpressionTypeResolver(
                        context.resolver(), scopes.peek(), context.functions());
    }

    @Override
    public boolean visit(Deque<Object> deque, Expression expression) {
        ExpressionTypeResolver types = types();
        if (types == null) {
            return true;
        }
        if (expression.getSelection() != null) {
            selection(types, expression);
        }
        return true;
    }

    private void selection(ExpressionTypeResolver types, Expression expression) {
        TypeDescriptor type;
        try {
            type = types.resolve(expression.getSelection().getBase());
        } catch (RuntimeException unresolved) {
            return;
        }
        for (Selector step : expression.getSelection().getSelectors()) {
            if (type == null || type.getTypeFamily() == null) {
                return;
            }
            if (!support.selectable().contains(type.getTypeFamily())) {
                report(
                        expression,
                        support.name()
                                + " cannot select from "
                                + type.portable()
                                + " with a selector"
                                + (support.selectable()
                                                .equals(java.util.Set.of(CoreTypeFamily.JSON))
                                        ? ": only JSON is a nested value here"
                                        : ""));
                return;
            }
            if (CoreTypeFamily.JSON.equals(type.getTypeFamily())
                    && !support.jsonFromEnd()
                    && step.getIndex() != null
                    && step.getIndex() < 0) {
                report(
                        expression,
                        support.name()
                                + " has no position from the end in JSON: "
                                + ai.koryki.iql.IQLSerializer.selector(step));
                return;
            }
            try {
                type = Selectors.step(type, step);
            } catch (SelectorException e) {
                return;
            }
        }
    }

    @Override
    public boolean visit(Deque<Object> deque, Function function) {
        if (support.lambdaOverJsonList()
                || function.getArguments().isEmpty()
                || function.getArguments().stream().noneMatch(a -> a.getLambda() != null)) {
            return true;
        }
        Expression list = function.getArguments().get(0);
        ExpressionTypeResolver types = types();
        if (types == null || list.getSelection() == null) {
            return true;
        }
        try {
            TypeDescriptor base = types.resolve(list.getSelection().getBase());
            TypeDescriptor result = types.resolve(list);
            if (base != null
                    && CoreTypeFamily.JSON.equals(base.getTypeFamily())
                    && result != null
                    && CoreTypeFamily.ARRAY.equals(result.getTypeFamily())) {
                report(
                        list,
                        function.getFunc()
                                + " cannot be applied to a JSON array on "
                                + support.name()
                                + ": the list is built inside a derived table, where "
                                + support.name()
                                + " returns nothing. The list functions without a lambda work.");
            }
        } catch (RuntimeException unresolved) {
            // not typed: whoever reports that has the position
        }
        return true;
    }

    private void report(Expression expression, String message) {
        violations.add(
                new Violation(
                        Violation.UNSUPPORTED,
                        expression,
                        Range.of(iqlToContext, expression),
                        message));
    }

    @Override
    public List<Violation> collect() {
        return violations;
    }
}
