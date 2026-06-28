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

    public GroupRule(Query query) {

        this.query = query;
    }

    public void apply() {

        GroupVisitor v = new GroupVisitor();
        new Walker().walk(query, v);
    }

    private static class GroupVisitor implements Visitor {

        public GroupVisitor() {

        }

        @Override
        public boolean visit(Deque<Object> deque, Select select) {
            apply(select);
            return true;
        }

        private void apply(Select select) {

            List<Out> list = SqlQueryRenderer.collectOut(select);
            if (hasAggregate(list) || hasHaving(select)) {

                list.forEach(o -> {
                    if (!isAggregate( o)) {

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

            return select.getJoin().stream().anyMatch(GroupVisitor::hasHaving);
        }

        private static boolean hasHaving(Source table) {
            return table.getHaving() != null;
        }

        private static boolean hasHaving(Join join) {

            if (join.getSource() != null && hasHaving(join.getSource())) {
                return true;
            }
            return join.getJoin().stream().anyMatch(GroupVisitor::hasHaving);
        }

        private static boolean hasAggregate(List<Out> list) {

            return list.stream().anyMatch(o -> isAggregate(o));
        }
    }

    private static boolean isAggregate(Out o) {
        return FunctionValidator.isAggregateOfColumnOrIdentity(o.getExpression());
    }
}
