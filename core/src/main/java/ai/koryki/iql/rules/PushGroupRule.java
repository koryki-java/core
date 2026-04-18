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
import ai.koryki.iql.query.Group;
import ai.koryki.iql.query.Query;
import ai.koryki.iql.query.Select;
import ai.koryki.iql.query.Source;

import java.util.Deque;
import java.util.List;

/**
 * Push out expressions from select.group, to table.group.
 */
public class PushGroupRule {


    public PushGroupRule() {

    }

    public void apply(Query query) {

        PushExpressionVisitor v = new PushExpressionVisitor();
        new Walker().walk(query, v);
    }

    private static class PushExpressionVisitor implements Visitor {


        public PushExpressionVisitor() {
        }

        @Override
        public boolean visit(Deque<Object> deque, Select select) {

            List<Group> group = select.getGroup();

            group.removeIf(g -> {
                String a = PushLogicalExpressionRule.homogenAlias(g.getExpression());
                if (a != null) {
                    Source table = Visitor.findSourceInSelect(select, a);
                    table.getGroup().add(g);
                    return true;
                }
                return true;
            });
            return true;
        }
    }
}
