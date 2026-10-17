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
import ai.koryki.catalog.MemberNames;
import ai.koryki.catalog.types.PartNames;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.iql.Collector;
import ai.koryki.iql.IQLVisibilityContext;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.Visitor;
import ai.koryki.iql.functions.FunctionCatalog;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Field;
import ai.koryki.iql.query.Select;
import ai.koryki.iql.query.Source;
import ai.koryki.iql.typing.ExpressionTypeResolver;
import ai.koryki.iql.typing.SelectorException;
import ai.koryki.iql.typing.SelectorLevels;
import ai.koryki.iql.typing.Selectors;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.RuleContext;

/**
 * Checks every selector chain against the type of the value it selects from: a field the struct
 * does not have, a key of the wrong type, a position 0, a step into a scalar. The type of the base
 * is resolved in the scope of the select it stands in, as {@link FunctionValidator} does for its
 * operands. A base whose type cannot be resolved is left to whoever reports why.
 */
public class SelectorValidator implements Visitor, Collector<List<Violation>> {

    private final List<Violation> violations = new ArrayList<>();
    private final Map<Object, RuleContext> iqlToContext;
    private final FunctionCatalog functions;
    private final LinkResolver resolver;
    private final IQLVisibilityContext rootVisibility;
    private final Deque<IQLVisibilityContext> scopes = new ArrayDeque<>();

    /** Without a catalog, resolver or visibility there is nothing to type against: no checks. */
    public SelectorValidator(
            Map<Object, RuleContext> iqlToContext,
            FunctionCatalog functions,
            LinkResolver resolver,
            IQLVisibilityContext visibility) {
        this.iqlToContext = iqlToContext;
        this.functions = functions;
        this.resolver = resolver;
        this.rootVisibility = visibility;
    }

    private boolean typeChecks() {
        return functions != null && resolver != null && rootVisibility != null;
    }

    @Override
    public boolean visit(Deque<Object> deque, Select select) {
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
    public boolean visit(Deque<Object> deque, Expression expression) {
        if (expression.getSelection() == null || !typeChecks() || scopes.isEmpty()) {
            return true;
        }
        ExpressionTypeResolver types =
                new ExpressionTypeResolver(resolver, scopes.peek(), functions);
        TypeDescriptor base;
        try {
            base = types.resolve(expression.getSelection().getBase());
        } catch (RuntimeException unresolved) {
            return true;
        }
        if (base == null) {
            return true;
        }
        try {
            Selectors.apply(base, expression.getSelection().getSelectors(), namesOf(expression));
        } catch (SelectorException e) {
            violations.add(
                    new Violation(
                            "type",
                            expression,
                            Range.of(iqlToContext, expression),
                            e.getMessage()));
        }
        return true;
    }

    /**
     * What the model calls the fields of the column the selection starts from, so that a failure is
     * explained in the names the query was written in. Plain keys where the base is no column of a
     * table, or the model says nothing.
     */
    private PartNames namesOf(Expression expression) {
        Field field = expression.getSelection().getBase().getField();
        IQLVisibilityContext visibility = scopes.peek();
        if (field == null || field.getAlias() == null || visibility == null) {
            return PartNames.PLAIN;
        }
        try {
            Source source = visibility.getSource(field.getAlias());
            if (source == null) {
                return PartNames.PLAIN;
            }
            return new SelectorLevels(resolver, visibility::getBlock, visibility::rowsOf)
                    .of(source, field.getName())
                    .map(MemberNames.Level::partNames)
                    .orElse(PartNames.PLAIN);
        } catch (RuntimeException unresolved) {
            return PartNames.PLAIN;
        }
    }

    @Override
    public List<Violation> collect() {
        return violations;
    }
}
