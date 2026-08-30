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

import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.Visitor;
import ai.koryki.iql.Walker;
import ai.koryki.iql.query.*;
import ai.koryki.catalog.schema.Column;
import org.antlr.v4.runtime.RuleContext;

import java.util.*;

/**
 * Replace Identity-Indicator with first PK-Field.
 * This is used for count(ID).
 */
public class IdentityRule {

    private final LinkResolver resolver;
    private final Map<String, Source> blockIdToLeadingTableMap;
    private final Map<Object, RuleContext> iqlToContext;

    public IdentityRule(Map<String, Source> blockIdToLeadingTableMap, LinkResolver resolver,
                        Map<Object, RuleContext> iqlToContext) {

        this.blockIdToLeadingTableMap = blockIdToLeadingTableMap;
        this.resolver = resolver;
        this.iqlToContext = iqlToContext;
    }

    public void apply(Query query) {

        IdentityVisitor v = new IdentityVisitor(blockIdToLeadingTableMap, resolver, iqlToContext);
        new Walker().walk(query, v);
        // Only now: the walk iterates a function's argument list, and count_distinct grows it. Doing
        // it during the visit raised a ConcurrentModificationException -- found by running it, not
        // by reading it.
        v.applyPendingArguments();
    }

    private static class IdentityVisitor implements Visitor {

        private final LinkResolver resolver;
        private final Map<String, Source> blockIdToLeadingSourceMap;
        private final Map<Object, RuleContext> iqlToContext;

        public IdentityVisitor(Map<String, Source> blockIdToLeadingSourceMap, LinkResolver resolver,
                               Map<Object, RuleContext> iqlToContext) {

            this.blockIdToLeadingSourceMap = blockIdToLeadingSourceMap;
            this.resolver = resolver;
            this.iqlToContext = iqlToContext;
        }

        /** Argument lists grow only after the walk; see {@link IdentityRule#apply}. */
        private final List<Runnable> pending = new ArrayList<>();

        void applyPendingArguments() {
            pending.forEach(Runnable::run);
            pending.clear();
        }

        @Override
        public boolean visit(Deque<Object> deque, Expression expression) {

            if (expression.getIdentity() != null) {
                Visitor.getNthElement(deque, 1).map(e -> e instanceof Function ? (Function)e : null).ifPresent(f -> {

                    Source table = Visitor.findSourceInParentSelect(deque, expression.getIdentity());
                    String alias = table.getAlias();

                    enrichPkColumn(table, expression, alias, 1);

                    // count_distinct asks how many *different* rows, and rows differ by their whole
                    // key. With one column it answered a different question: over order_details,
                    // keyed by (order_id, product_id), COUNT(DISTINCT order_id) counted orders --
                    // 830 where the truth is 2155, and nothing said so.
                    //
                    // count deliberately keeps the single column above: a key is never null, so
                    // counting one counts rows, and under an outer join it counts 0 for an unmatched
                    // row where COUNT(*) would count 1.
                    if ("count_distinct".equals(f.getFunc())) {
                        for (int pos = 2; pos <= keyCount(table); pos++) {
                            // Through the same method as the first column, deliberately: it is the
                            // one that follows a block to its leading source and projects what the
                            // outer query needs. A second implementation would have to relearn that.
                            Expression more = new Expression();
                            more.setIdentity(expression.getIdentity());
                            enrichPkColumn(table, more, alias, pos);
                            Rules.inherit(iqlToContext, expression, more);
                            pending.add(() -> f.getArguments().add(more));
                        }
                    }
                });
            }

            return true;
        }

        /** How many columns make up the source's key, following a block to its leading source. */
        private int keyCount(Source source) {
            Source blockSource = blockIdToLeadingSourceMap.get(source.getName());
            if (blockSource != null) {
                return keyCount(blockSource);
            }
            return resolver.getDialectTable(source.getName())
                    .flatMap(t -> resolver.getSchema().getTable(t))
                    .map(t -> (int) t.getColumns().stream().filter(c -> c.getPkPos() > 0).count())
                    .orElse(0);
        }

        private String enrichPkColumn(Source source, Expression expression, String tableAlias, int pkPos) {
            Source blockSource = blockIdToLeadingSourceMap.get(source.getName());
            if (blockSource != null) {
                String column = enrichPkColumn(blockSource, expression, tableAlias, pkPos);

                String h = blockSource.getOut().stream().filter(o -> o.getHeader() != null && o.getExpression().getField() != null && o.getExpression().getField().getName().equals(column)).map(o -> o.getHeader()).findFirst().orElse(null);

                if (h != null) {
                    return h;
                } else {
                    enrichBlocksource(blockSource, column);
                    return column;
                }
            } else {

                Optional<ai.koryki.catalog.schema.Table> ot = resolver.getDialectTable(source.getName())
                        .flatMap(table -> resolver.getSchema().getTable(table));

                // The pkPos-th key column. count asks for the first only -- a key is never null, so counting
                // one column counts rows. count_distinct asks for every one of them, because two rows differ
                // by their whole key: over order_details it is (order_id, product_id), and the first column
                // alone answered 830 instead of 2155.
                String pkColumn = ot.flatMap(t -> t.getColumns().stream().filter(
                        c -> c.getPkPos() == pkPos).map(Column::getName).findFirst()).orElse(null);

                // The key is read off the schema, but it is about to become a field of the query,
                // and the query speaks model names. The two coincide only while no attribute
                // declares a column override -- under the German model none of them coincide, and
                // this wrote order_id where the model says bestell_id. Converting here rather than
                // at the two uses below keeps the header match, the new field and the returned name
                // in one vocabulary.
                String col = pkColumn == null ? null
                        : resolver.getModelAttribute(source.getName(), pkColumn).orElse(pkColumn);

                String h = source.getOut().stream()
                        .filter(o -> o.getHeader() != null && o.getExpression().getField() != null && o.getExpression().getField().getName().equals(col)).map(Out::getHeader).findFirst().orElse(null);

                if (h != null) {

                    // create column and remove identity
                    Field c = new Field();
                    c.setAlias(tableAlias);
                    c.setName(h);
                    // The column exists because of this count(alias) -- it inherits its position.
                    Rules.inherit(iqlToContext, expression, c);
                    expression.setField(c);
                    expression.setIdentity(null);
                    return col;
                }

                if (col != null) {

                    // create column and remove identity
                    Field c = new Field();
                    c.setAlias(tableAlias);
                    c.setName(col);
                    Rules.inherit(iqlToContext, expression, c);
                    expression.setField(c);
                    expression.setIdentity(null);
                }

                return col;
            }
        }

        private void enrichBlocksource(Source blockSource, String fieldName) {

            if (blockSource.getOut().stream().noneMatch(out -> out.getExpression().getField() != null && out.getExpression().getField().getName().equals(fieldName))) {

                Field field = new Field();
                field.setAlias(blockSource.getAlias());
                field.setName(fieldName);
                Expression expression = new Expression();
                expression.setField(field);
                Out bout = new Out();
                bout.setExpression(expression);
                // The projection exists so the outer access sees the column: its origin is the
                // block source it is placed on.
                Rules.inherit(iqlToContext, blockSource, bout, expression, field);
                blockSource.getOut().add(bout);
            }
        }
    }
}
