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
import ai.koryki.scaffold.domain.Model;
import ai.koryki.scaffold.schema.Column;
import ai.koryki.scaffold.schema.Schema;

import java.util.*;

/**
 * Replace Identity-Indicator with first PK-Field.
 * This is used for count(ID).
 */
public class IdentityRule {

    private final LinkResolver resolver;
    private final Map<String, Source> blockIdToLeadingTableMap;

    public IdentityRule(Map<String, Source> blockIdToLeadingTableMap, LinkResolver resolver) {

        this.blockIdToLeadingTableMap = blockIdToLeadingTableMap;
        this.resolver = resolver;
    }

    public void apply(Query query) {

        IdentityVisitor v = new IdentityVisitor(blockIdToLeadingTableMap, resolver);
        new Walker().walk(query, v);
    }

    private static class IdentityVisitor implements Visitor {

        private final LinkResolver resolver;
        private final Map<String, Source> blockIdToLeadingSourceMap;

        public IdentityVisitor(Map<String, Source> blockIdToLeadingSourceMap, LinkResolver resolver) {

            this.blockIdToLeadingSourceMap = blockIdToLeadingSourceMap;
            this.resolver = resolver;
        }

        @Override
        public boolean visit(Deque<Object> deque, Expression expression) {

            if (expression.getIdentity() != null) {
                Visitor.getNthElement(deque, 1).map(e -> e instanceof Function ? (Function)e : null).ifPresent(f -> {

                    Source table = Visitor.findSourceInParentSelect(deque, expression.getIdentity());

                    enrichPkColumn(table, expression, table.getAlias());
                });
            }

            return true;
        }

        private String enrichPkColumn(Source source, Expression expression, String tableAlias) {
            Source blockSource = blockIdToLeadingSourceMap.get(source.getName());
            if (blockSource != null) {
                String column = enrichPkColumn(blockSource, expression, tableAlias);

                String h = blockSource.getOut().stream().filter(o -> o.getHeader() != null && o.getExpression().getField() != null && o.getExpression().getField().getName().equals(column)).map(o -> o.getHeader()).findFirst().orElse(null);

                if (h != null) {
                    return h;
                } else {
                    enrichBlocksource(blockSource, column);
                    return column;
                }
            } else {

                Optional<ai.koryki.scaffold.schema.Table> ot = resolver.getDialectTable(source.getName())
                        .flatMap(table -> resolver.getSchema().getTable(table));

                // check pkPos == 1 only, multipart pk also require first part only !
                String col = ot.flatMap(t -> t.getColumns().stream().filter(
                        c -> c.getPkPos() == 1).map(Column::getName).findFirst()).orElse(null);

                String h = source.getOut().stream()
                        .filter(o -> o.getHeader() != null && o.getExpression().getField() != null && o.getExpression().getField().getName().equals(col)).map(Out::getHeader).findFirst().orElse(null);

                if (h != null) {

                    // create column and remove identity
                    Field c = new Field();
                    c.setAlias(tableAlias);
                    c.setName(h);
                    expression.setField(c);
                    expression.setIdentity(null);
                    return col;
                }

                if (col != null) {

                    // create column and remove identity
                    Field c = new Field();
                    c.setAlias(tableAlias);
                    c.setName(col);
                    expression.setField(c);
                    expression.setIdentity(null);
                }

                return col;
            }
        }

        private static void enrichBlocksource(Source blockSource, String fieldName) {

            if (blockSource.getOut().stream().noneMatch(out -> out.getExpression().getField() != null && out.getExpression().getField().getName().equals(fieldName))) {

                Field field = new Field();
                field.setAlias(blockSource.getAlias());
                field.setName(fieldName);
                Expression expression = new Expression();
                expression.setField(field);
                Out bout = new Out();
                bout.setExpression(expression);
                blockSource.getOut().add(bout);
            }
        }
    }
}
