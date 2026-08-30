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

import ai.koryki.iql.Visitor;
import ai.koryki.iql.Walker;
import ai.koryki.iql.logic.NodeType;
import ai.koryki.iql.query.Exists;
import ai.koryki.iql.query.LogicalExpression;
import ai.koryki.iql.query.Query;
import ai.koryki.iql.query.Select;
import ai.koryki.iql.validate.FunctionValidator;

import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Move LogicalExpressions from filter to having, if aggregats are present.
 */
public class HavingRule {

    private final Query query;

    public HavingRule(Query query) {

        this.query = query;
    }

    public void apply() {

        HavingVisitor v = new HavingVisitor();
        new Walker().walk(query, v);
    }

    private static class HavingVisitor implements Visitor {

        public HavingVisitor() {

        }

        @Override
        public boolean visit(Deque<Object> deque, Select select) {
            apply(select);
            return true;
        }

        @Override
        public boolean visit(Deque<Object> deque, Exists exists) {
            apply(exists);
            return true;
        }

        private void apply(Select select) {
            apply(select.getFilter(), select::setFilter, select.getHaving(), select::setHaving);
        }

        private void apply(Exists exists) {
            apply(exists.getFilter(), exists::setFilter, exists.getHaving(), exists::setHaving);
        }

        private void apply(LogicalExpression filter, Consumer<LogicalExpression> setFilter,
                           LogicalExpression having, Consumer<LogicalExpression> setHaving) {

            if (filter == null) {
                return;
            }

            NodeType t = filter.getType();

            if (t.equals(NodeType.VAR)) {


                if (isHaving(filter)) {
                    setFilter.accept(null);
                    setHaving.accept(filter);
                }
            } else if (t.equals(NodeType.AND)) {
                List<LogicalExpression> c = filter.getChildren();

                List<LogicalExpression> havings = c.stream().filter(this::isHaving).collect(Collectors.toList());

                c.removeIf(this::isHaving);
                if (c.isEmpty()) {
                    setFilter.accept(null);
                }

                if (!havings.isEmpty()) {
                    LogicalExpression having2 = LogicalExpression.and(havings);
                    setHaving.accept(LogicalExpression.and(having2, having));
                }
            }
        }


        private boolean isHaving(LogicalExpression logical) {
            boolean h =
                    logical.isValue() &&
                            logical.getUnaryRelationalExpression().getOp() != null &&
                            isAggregate(logical);
            return h;
        }
    }

    private static boolean isAggregate(LogicalExpression logical) {
        return FunctionValidator.isAggregateOfColumnOrIdentity(logical.getUnaryRelationalExpression().getLeft());
    }
}
