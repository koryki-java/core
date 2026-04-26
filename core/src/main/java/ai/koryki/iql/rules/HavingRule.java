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
import java.util.stream.Collectors;

/**
 * Move LogicalExpressions from filter to having, if aggregats are present.
 */
public class HavingRule {

    private final Query query;
    private final Aggregate aggregate;
    public HavingRule(Aggregate aggregate, Query query) {
        this.aggregate = aggregate;
        this.query = query;
    }

    public void apply() {

        HavingVisitor v = new HavingVisitor(aggregate);
        new Walker().walk(query, v);
    }

    private static class HavingVisitor implements Visitor {

        private final Aggregate aggregate;
        public HavingVisitor(Aggregate aggregate) {
            this.aggregate = aggregate;
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

            LogicalExpression filter = select.getFilter();

            if (filter == null) {
                return;
            }

            NodeType t = filter.getType();

            if (t.equals(NodeType.VAR)) {


                if (isHaving(filter)) {
                    select.setFilter(null);
                    select.setHaving(filter);
                }
            } else if (t.equals(NodeType.AND)) {
                List<LogicalExpression> c = filter.getChildren();

                List<LogicalExpression> havings = c.stream().filter(this::isHaving).collect(Collectors.toList());

                c.removeIf(this::isHaving);

                if (!havings.isEmpty()) {
                    LogicalExpression having = LogicalExpression.and(havings);
                    select.setHaving(LogicalExpression.and(having, select.getHaving()));
                }
            }
        }

        private void apply(Exists exists) {

            LogicalExpression filter = exists.getFilter();

            if (filter == null) {
                return;
            }

            NodeType t = filter.getType();

            if (t.equals(NodeType.VAR)) {


                if (isHaving(filter)) {
                    exists.setFilter(null);
                    exists.setHaving(filter);
                }
            } else if (t.equals(NodeType.AND)) {
                List<LogicalExpression> c = filter.getChildren();

                List<LogicalExpression> havings = c.stream().filter(this::isHaving).collect(Collectors.toList());

                c.removeIf(this::isHaving);

                if (!havings.isEmpty()) {
                    LogicalExpression having = LogicalExpression.and(havings);
                    exists.setHaving(LogicalExpression.and(having, exists.getHaving()));
                }
            }
        }


        private boolean isHaving(LogicalExpression logical) {
            boolean h =
                    logical.isValue() &&
                            logical.getUnaryRelationalExpression().getOp() != null &&
                            isAggregate(logical, aggregate);
            return h;
        }
    }

    private static boolean isAggregate(LogicalExpression logical, Aggregate aggregate) {
        return FunctionValidator.isAggregatOfColumnOrIdentity(logical.getUnaryRelationalExpression().getLeft(), aggregate);
    }
}
