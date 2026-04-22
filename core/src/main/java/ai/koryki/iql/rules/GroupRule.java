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

import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.Visitor;
import ai.koryki.iql.Walker;
import ai.koryki.iql.query.*;
import ai.koryki.iql.validate.FunctionValidator;

import java.util.Deque;
import java.util.List;

/**
 * Add GROUP-Expression, if aggregates are present.
 */
public class GroupRule {

    private final Query query;
    private final Aggregate aggregate;

    public GroupRule(Aggregate aggregate, Query query) {
        this.aggregate = aggregate;
        this.query = query;
    }

    public void apply() {

        GroupVisitor v = new GroupVisitor(aggregate);
        new Walker().walk(query, v);
    }

    private static class GroupVisitor implements Visitor {

        private final Aggregate aggregate;
        public GroupVisitor(Aggregate aggregat) {
            this.aggregate = aggregat;
        }

        @Override
        public boolean visit(Deque<Object> deque, Select select) {
            apply(select);
            return true;
        }

        private void apply(Select select) {

            List<Out> list = SqlQueryRenderer.collectOut(select);
            if (hasAggregate(aggregate, list) || hasHaving(select)) {

                list.forEach(o -> {
                    if (!isAggregate(aggregate, o)) {

                        Group g = new Group();
                        g.setIdx(o.getIdx());
                        g.setExpression(o.getExpression());
                        select.getGroup().add(g);
                    }
                });
            }
        }

        private static boolean hasHaving(Select select) {

            if (select.getHaving() != null) {
                return true;
            }
            if (hasHaving(select.getStart())) {
                return true;
            }

            return select.getJoin().stream().anyMatch(j -> hasHaving(j));
        }

        private static boolean hasHaving(Source table) {
            return table.getHaving() != null;
        }

        private static boolean hasHaving(Join join) {

            if (join.getSource() != null && hasHaving(join.getSource())) {
                return true;
            }
            return join.getJoin().stream().anyMatch(j -> hasHaving(j));
        }

        private static boolean hasAggregate(Aggregate aggregat, List<Out> list) {

            return list.stream().map(o -> isAggregate(aggregat, o)).anyMatch(b -> b);
        }
    }

    private static boolean isAggregate(Aggregate aggregat, Out o) {
        return FunctionValidator.isAggregatOfColumnOrIdentity(o.getExpression(), aggregat);
    }
}
