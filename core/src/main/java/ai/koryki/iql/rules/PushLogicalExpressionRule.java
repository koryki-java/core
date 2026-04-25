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
import ai.koryki.iql.query.*;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Push logical expressions from select.filter, to table.filter.
 * We must do this to execute outer joins correctly, otherwise
 * they will behave like inner joins.
 */
public class PushLogicalExpressionRule {

    public PushLogicalExpressionRule() {

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

            LogicalExpression filter = select.getFilter();
            if (filter != null) {

                Consumer<LogicalExpression> allFilterConsumer = c -> select.setFilter(null);
                java.util.function.Function<Source, LogicalExpression> s = Source::getFilter;
                BiConsumer<Source, LogicalExpression> tableFilterConsumer = Source::setFilter;
                pushExpressions(select, filter, tableFilterConsumer, s, allFilterConsumer);
            }
            LogicalExpression having = select.getHaving();
            if (having != null) {

                Consumer<LogicalExpression> allHavingConsumer = c -> select.setHaving(null);
                java.util.function.Function<Source, LogicalExpression> s = Source::getHaving;
                BiConsumer<Source, LogicalExpression> tableHavingConsumer = Source::setHaving;
                pushExpressions(select, having, tableHavingConsumer, s, allHavingConsumer);
            }
            return true;
        }

        private void pushExpressions(Select select, LogicalExpression exp, BiConsumer<Source, LogicalExpression> tableExpressionConsumer, java.util.function.Function<Source, LogicalExpression> tableExpressionFunctoin, Consumer<LogicalExpression> allExpressionConsumer) {
            if (exp.getType().isValue()) {
                String a = homogenAlias(exp);
                if (a != null) {
                    // push exp at all

                    Source table = Visitor.findSourceInSelect(select, a);
                    tableExpressionConsumer.accept(table, LogicalExpression.and(exp, tableExpressionFunctoin.apply(table)));
                    resetAll(allExpressionConsumer);
                }
            } else if (exp.getType().isNot()) {
                // do not push expression
            } else if (exp.getType().equals(NodeType.OR)) {
                // do not push expression
            } else {
                // we have AND-Filter
                List<LogicalExpression> children = new ArrayList<>(exp.getChildren());
                for (LogicalExpression c : children) {
                    String a = homogenAlias(c);
                    if (a != null) {
                        // push children
                        Source table = Visitor.findSourceInSelect(select, a);
                        //table.setFilter(LogicalExpression.and(c, table.getFilter()));
                        tableExpressionConsumer.accept(table, LogicalExpression.and(c, tableExpressionFunctoin.apply(table)));
                        // remove from exp
                        exp.getChildren().remove(c);
                    }
                }
                if (exp.getChildren().isEmpty()) {
                    resetAll(allExpressionConsumer);
                }
            }
        }

        private static void resetAll(Consumer<LogicalExpression> c) {
            c.accept(null);
        }
    }

    private static String homogenAlias(LogicalExpression logicalExpression) {
        if (logicalExpression.getUnaryRelationalExpression() != null) {
            return homogenAlias(logicalExpression.getUnaryRelationalExpression());
        } else {
            List<String> aliases =
                    logicalExpression.getChildren().stream()
                            .map(PushLogicalExpressionRule::homogenAlias)
                            .distinct()
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
            return aliases.size() == 1 ? aliases.get(0) : null;
        }
    }

    private static String homogenAlias(UnaryLogicalExpression u) {

        if (u.getNode() != null) {
            return null;
        } else {

            List<Expression> expressions = new ArrayList<>();
            expressions.add(u.getLeft());
            expressions.addAll(u.getRight());

            List<String> aliases =
                    expressions.stream()
                            .map(PushLogicalExpressionRule::homogenAlias)
                            .distinct()
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
            return aliases.size() == 1 ? aliases.get(0) : null;
        }
    }

    public static String homogenAlias(Expression expression) {

        if (expression.getIdentity() != null) {
            return expression.getIdentity();
        } else if (expression.getField() != null) {
            return expression.getField().getAlias();
        } else if (expression.getFunction() != null) {

            List<String> aliases = expression.getFunction().getArguments().stream()
                    .map(PushLogicalExpressionRule::homogenAlias)
                    .distinct()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            return aliases.size() == 1 ? aliases.get(0) : null;
        }
        return null;
    }
}
