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

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.iql.Visitor;
import ai.koryki.iql.Walker;
import ai.koryki.iql.query.Field;
import ai.koryki.iql.query.Join;
import ai.koryki.iql.query.LogicalExpression;
import ai.koryki.iql.query.Query;

import java.util.Deque;

public class CheckOuterJoinFilterRule {

    public void apply(Query query) {

        CheckExpressionVisitor v = new CheckExpressionVisitor();
        new Walker().walk(query, v);
    }

    private static class CheckExpressionVisitor implements Visitor {
        @Override
        public boolean visit(Deque<Object> deque, Join join) {
            if (join.isOptional()) {

                String alias = join.getSource().getAlias();
                LogicalExpression e = Visitor.parentSelect(deque).getFilter();
                if (e != null) {
                    new Walker().walk(e, new CheckAliasVisitor(alias));
                }
            }
            return true;
        }
    }
    private static class CheckAliasVisitor implements Visitor {
        private String alias;

        public CheckAliasVisitor(String alias) {
            this.alias = alias;
        }

        @Override
        public boolean visit(Deque<Object> deque, Field column) {

            if (column.getAlias().equals(alias)) {
                throw new KorykiaiException("outer joined table must not be used in all-filter: " + alias);
            }
            return true;
        }
    }
}
