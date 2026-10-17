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

package ai.koryki.iql.typing;

import ai.koryki.antlr.Range;
import ai.koryki.catalog.MemberNames;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.iql.IQLVisibilityContext;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.Visitor;
import ai.koryki.iql.Walker;
import ai.koryki.iql.functions.FunctionCatalog;
import ai.koryki.iql.query.Exists;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
import ai.koryki.iql.query.Query;
import ai.koryki.iql.query.Select;
import ai.koryki.iql.query.Selector;
import ai.koryki.iql.query.Source;
import ai.koryki.iql.validate.IdentityUseValidator;
import ai.koryki.iql.validate.Violation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.antlr.v4.runtime.RuleContext;

/**
 * Finds what the rows of every list made into rows are: the type of its elements, from the type of
 * the list its call is given, and what the model says of the parts of them.
 *
 * <p>This is the whole definition of the virtual table of {@code o unnest(o.items) i}: it is made
 * when the query is analysed, from the expression, so a column, a nested list, what a lambda made
 * and the output of a block all work the same, and nothing is added to the catalog. The sources of
 * a select are visited in the order they are written, so the list of an {@code unnest} may be a
 * field of an earlier one: {@code i unnest(i.opts) q}.
 *
 * <p>A call whose argument has no type, or is not a list, is left without rows. The function
 * validator says why, with the position.
 *
 * <p>Also refused here, because it needs the scope: the alias of such a source named as an entity,
 * {@code count(i)}. Its rows have no key, and counting the fields of the element is what is meant.
 */
public final class UnnestElements {

    /** The functions whose result is a list of the elements of their first argument. */
    private static final Set<String> KEEP_ELEMENTS =
            Set.of("list_filter", "list_sort", "list_distinct", "list_concat");

    private final LinkResolver resolver;
    private final FunctionCatalog functions;
    private final IQLVisibilityContext root;
    private final SelectorLevels levels;
    private final Map<Object, RuleContext> iqlToContext;
    private final List<Violation> violations = new ArrayList<>();

    public UnnestElements(
            LinkResolver resolver,
            FunctionCatalog functions,
            IQLVisibilityContext root,
            Map<Object, RuleContext> iqlToContext) {
        this.resolver = resolver;
        this.functions = functions;
        this.root = root;
        this.iqlToContext = iqlToContext;
        this.levels = new SelectorLevels(resolver, root::getBlock, root::rowsOf);
    }

    /** Finds the rows of every list in the query; the violations are those of {@link #apply}. */
    public List<Violation> apply(Query query) {
        new Walker().walk(query, new Scoped());
        return List.copyOf(violations);
    }

    private final class Scoped implements Visitor {

        private final Deque<IQLVisibilityContext> scopes = new ArrayDeque<>();

        @Override
        public boolean visit(Deque<Object> deque, Select select) {
            scopes.push((scopes.isEmpty() ? root : scopes.peek()).child(select));
            return true;
        }

        @Override
        public void leave(Select select) {
            scopes.pop();
        }

        @Override
        public boolean visit(Deque<Object> deque, Exists exists) {
            scopes.push((scopes.isEmpty() ? root : scopes.peek()).child(exists));
            return true;
        }

        @Override
        public void leave(Exists exists) {
            scopes.pop();
        }

        @Override
        public boolean visit(Deque<Object> deque, Expression expression) {
            if (expression.getIdentity() != null && !scopes.isEmpty()) {
                Source named = scopes.peek().getSource(expression.getIdentity());
                if (named != null && named.getCall() != null) {
                    violations.add(
                            new Violation(
                                    IdentityUseValidator.IDENTITY,
                                    expression,
                                    Range.of(iqlToContext, expression),
                                    "'"
                                            + expression.getIdentity()
                                            + "' is a list made into rows and has no key to count:"
                                            + " count one of its fields, as in count("
                                            + expression.getIdentity()
                                            + "."
                                            + example(named)
                                            + ")"));
                }
            }
            return true;
        }

        @Override
        public boolean visit(Deque<Object> deque, Source source) {
            Function call = source.getCall();
            if (call != null && call.getArguments().size() == 1 && !scopes.isEmpty()) {
                Expression list = call.getArguments().get(0);
                RowType type = rows(list, scopes.peek());
                if (type != null) {
                    root.putRows(source, type);
                } else if (UnnestRows.undeclaredJson(typeOf(list, scopes.peek()))) {
                    violations.add(
                            new Violation(
                                    "source",
                                    source,
                                    Range.of(iqlToContext, source),
                                    "unnest needs a list, and this JSON is not declared as one:"
                                            + " the catalog says where the array is, as in .a.b[]"));
                }
            }
            return true;
        }
    }

    private String example(Source source) {
        RowType type = root.rowsOf(source);
        return type == null ? "field" : UnnestRows.columns(type.element()).get(0);
    }

    private TypeDescriptor typeOf(Expression list, IQLVisibilityContext scope) {
        try {
            return new ExpressionTypeResolver(resolver, scope, functions).resolve(list);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private RowType rows(Expression list, IQLVisibilityContext scope) {
        TypeDescriptor type = typeOf(list, scope);
        TypeDescriptor element = UnnestRows.elementOf(type);
        if (element == null) {
            return null;
        }
        return new RowType(
                element, levelOf(list, scope).flatMap(l -> l.into("element")).orElse(null));
    }

    /** What the model says of the parts of the value the expression is, where it says anything. */
    private Optional<MemberNames.Level> levelOf(Expression e, IQLVisibilityContext scope) {
        if (e.getField() != null) {
            Source source = scope.getSource(e.getField().getAlias());
            return source == null ? Optional.empty() : levels.of(source, e.getField().getName());
        }
        if (e.getSelection() != null) {
            Optional<MemberNames.Level> level = levelOf(e.getSelection().getBase(), scope);
            for (Selector step : e.getSelection().getSelectors()) {
                if (level.isEmpty()) {
                    return level;
                }
                MemberNames.Level at = level.get();
                String written = step.getId() != null ? step.getId() : step.getName();
                level = at.key(written, step.getIndex()).flatMap(at::into);
            }
            return level;
        }
        Function f = e.getFunction();
        if (f != null && KEEP_ELEMENTS.contains(f.getFunc()) && !f.getArguments().isEmpty()) {
            return levelOf(f.getArguments().get(0), scope);
        }
        return Optional.empty();
    }
}
