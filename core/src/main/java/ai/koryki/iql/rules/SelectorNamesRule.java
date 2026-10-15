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
package ai.koryki.iql.rules;

import ai.koryki.catalog.MemberNames;
import ai.koryki.catalog.domain.Entity;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.Visitor;
import ai.koryki.iql.Walker;
import ai.koryki.iql.query.Block;
import ai.koryki.iql.query.Exists;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Field;
import ai.koryki.iql.query.Out;
import ai.koryki.iql.query.Query;
import ai.koryki.iql.query.Select;
import ai.koryki.iql.query.Selector;
import ai.koryki.iql.query.Source;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Translates the names of struct fields in selectors from the language of the model to the physical
 * keys of the column's concept: {@code t.address@breitengrad} becomes {@code t.address@latitude}
 * when the German model calls that entry so. Everything after the rules, the validators and the
 * renderers, speaks physical keys only.
 *
 * <p>The base is a field of a table, of a block (followed to the column it projects) or of a source
 * inside an EXISTS. A name the model does not know stays as written, and so does any other base (a
 * function result, a computed output): a name the language does not translate is a physical key,
 * which is what a query wrote before models had member names.
 */
public class SelectorNamesRule {

    private final LinkResolver resolver;
    private final Map<String, Block> blocks = new HashMap<>();

    public SelectorNamesRule(LinkResolver resolver) {
        this.resolver = resolver;
    }

    public void apply(Query query) {
        query.getBlock().forEach(b -> blocks.put(b.getId(), b));
        new Walker().walk(query, new NamesVisitor(List.of()));
    }

    private class NamesVisitor implements Visitor {

        /** The scopes around the part of the tree this visitor was started on, innermost first. */
        private final List<Object> outer;

        NamesVisitor(List<Object> outer) {
            this.outer = outer;
        }

        /**
         * The walker does not go into the filter of an EXISTS, so it is walked from here, with the
         * scopes it stands in: its own sources first, then those of the query around it.
         */
        @Override
        public boolean visit(Deque<Object> deque, Exists exists) {
            List<Object> scopes = new ArrayList<>();
            scopes.add(exists);
            scopes.addAll(deque);
            scopes.addAll(outer);
            NamesVisitor inner = new NamesVisitor(scopes);
            new Walker().walk(exists.getFilter(), inner);
            new Walker().walk(exists.getHaving(), inner);
            return true;
        }

        @Override
        public boolean visit(Deque<Object> deque, Expression expression) {
            if (expression.getSelection() == null
                    || expression.getSelection().getBase().getField() == null) {
                return true;
            }
            Field field = expression.getSelection().getBase().getField();
            Source source =
                    field.getAlias() == null ? null : sourceOf(deque, outer, field.getAlias());
            if (source != null) {
                translate(
                        levelOf(source, field.getName(), new HashSet<>()).orElse(null),
                        expression.getSelection().getSelectors());
            }
            return true;
        }
    }

    /**
     * The source an alias stands for where the expression is: the innermost scope that has it, so
     * an EXISTS sees its own sources first and then those of the query around it.
     */
    private static Source sourceOf(Deque<Object> deque, List<Object> outer, String alias) {
        List<Object> scopes = new ArrayList<>(deque);
        scopes.addAll(outer);
        for (Object scope : scopes) {
            Source found = null;
            if (scope instanceof Select select) {
                found = Visitor.findSourceInSelect(select, alias);
            } else if (scope instanceof Exists exists) {
                found = Visitor.findSourceInExists(exists, alias);
            }
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * What the model says of the parts of the column a field stands for. A field of a block is the
     * output of the block, so it is followed into the block to the column it projects; an output
     * that is computed (a function, a selection) has no parts the model could have named.
     */
    private Optional<MemberNames.Level> levelOf(Source source, String name, Set<String> seen) {
        Block block = blocks.get(source.getName());
        if (block != null) {
            if (!seen.add(block.getId())) {
                return Optional.empty();
            }
            Select select = SqlQueryRenderer.select(block.getSet());
            for (Out out : SqlQueryRenderer.collectOut(select)) {
                Expression e = out.getExpression();
                String called =
                        out.getHeader() != null
                                ? out.getHeader()
                                : e != null && e.getField() != null ? e.getField().getName() : null;
                if (name.equals(called)) {
                    if (e.getField() == null || e.getField().getAlias() == null) {
                        return Optional.empty();
                    }
                    Source inner = Visitor.findSourceInSelect(select, e.getField().getAlias());
                    return inner == null
                            ? Optional.empty()
                            : levelOf(inner, e.getField().getName(), seen);
                }
            }
            return Optional.empty();
        }
        Optional<Entity> entity = resolver.getModel().getEntity(source.getName());
        return entity.flatMap(e -> e.getAttribute(name))
                .flatMap(
                        a ->
                                new MemberNames(resolver.getSchema(), resolver.getModel())
                                        .of(entity.get(), a));
    }

    private static void translate(MemberNames.Level level, List<Selector> steps) {
        for (Selector step : steps) {
            if (level == null) {
                return;
            }
            String written = step.getId() != null ? step.getId() : step.getName();
            String key = level.key(written, step.getIndex()).orElse(null);
            // only a name the model gave is translated; a field it says nothing about keeps the
            // spelling of the query, as the struct finds it without regard to case
            if (key != null && written != null && level.named(key)) {
                step.translateTo(key);
            }
            level = key == null ? null : level.into(key).orElse(null);
        }
    }
}
