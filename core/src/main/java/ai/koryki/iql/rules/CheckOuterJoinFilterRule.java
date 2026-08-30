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

import ai.koryki.antlr.Range;
import ai.koryki.antlr.RangeException;
import ai.koryki.iql.Visitor;
import ai.koryki.iql.Walker;
import ai.koryki.iql.query.Exists;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Field;
import ai.koryki.iql.query.Join;
import ai.koryki.iql.query.LogicalExpression;
import ai.koryki.iql.query.Query;
import ai.koryki.iql.query.Select;
import ai.koryki.iql.query.UnaryLogicalExpression;

import org.antlr.v4.runtime.RuleContext;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The last line of defence for optional joins.
 *
 * <p>{@link PushLogicalExpressionRule} moves a filter into the join's {@code ON} clause when it
 * names exactly one source. A predicate spanning two sources has no single table to move to, so it
 * stays in the WHERE — and a WHERE predicate on the null-extended side of a LEFT JOIN discards the
 * unmatched rows, quietly turning the optional join into a required one. That is the one outcome
 * the optional join exists to prevent, so it is refused rather than rendered.
 *
 * <p>Runs last in {@link Rules#apply()}, after every rule that might still have moved the predicate
 * somewhere legitimate.
 */
public class CheckOuterJoinFilterRule {

    private final Map<Object, RuleContext> iqlToContext;

    public CheckOuterJoinFilterRule() {
        this(Map.of());
    }

    /**
     * @param iqlToContext model node → parser context, so the failure can point at the offending
     *                     predicate instead of only naming the alias.
     */
    public CheckOuterJoinFilterRule(Map<Object, RuleContext> iqlToContext) {
        this.iqlToContext = iqlToContext != null ? iqlToContext : Map.of();
    }

    public void apply(Query query) {

        CheckExpressionVisitor v = new CheckExpressionVisitor(iqlToContext);
        new Walker().walk(query, v);
    }

    private static class CheckExpressionVisitor implements Visitor {

        private final Map<Object, RuleContext> iqlToContext;

        CheckExpressionVisitor(Map<Object, RuleContext> iqlToContext) {
            this.iqlToContext = iqlToContext;
        }

        @Override
        public boolean visit(Deque<Object> deque, Join join) {
            if (join.isOptional() && join.getSource() != null) {

                String alias = join.getSource().getAlias();
                LogicalExpression e = scopeFilter(deque);
                if (e != null) {
                    new Walker().walk(e, new CheckAliasVisitor(alias, join, iqlToContext));
                }
            }
            return true;
        }

        /**
         * The filter of the join's own scope: an optional join inside an EXISTS is
         * checked against that exists' filter, not the enclosing select's (whose
         * filter contains the whole exists subtree and produced false positives).
         */
        private static LogicalExpression scopeFilter(Deque<Object> deque) {
            for (Object o : deque) {
                if (o instanceof Exists exists) {
                    return exists.getFilter();
                }
                if (o instanceof Select select) {
                    return select.getFilter();
                }
            }
            return null;
        }
    }

    private static class CheckAliasVisitor implements Visitor {
        private final String alias;
        private final Join join;
        private final Map<Object, RuleContext> iqlToContext;

        CheckAliasVisitor(String alias, Join join, Map<Object, RuleContext> iqlToContext) {
            this.alias = alias;
            this.join = join;
            this.iqlToContext = iqlToContext;
        }

        @Override
        public boolean visit(Deque<Object> deque, Exists exists) {
            // an EXISTS is its own scope: alias references inside it are pushed
            // filters or correlations of the subselect, not row filters of this WHERE
            return false;
        }

        @Override
        public boolean visit(Deque<Object> deque, Field column) {

            if (column.getAlias().equals(alias)) {
                UnaryLogicalExpression predicate = enclosingPredicate(deque);
                throw new RangeException(rangeOf(predicate), message(predicate));
            }
            return true;
        }

        /** The comparison the offending column sits in, so the message can quote it. */
        private static UnaryLogicalExpression enclosingPredicate(Deque<Object> deque) {
            for (Object o : deque) {
                if (o instanceof UnaryLogicalExpression u) {
                    return u;
                }
            }
            return null;
        }

        private Range rangeOf(UnaryLogicalExpression predicate) {
            RuleContext ctx = predicate != null ? iqlToContext.get(predicate) : null;
            if (ctx == null) {
                ctx = iqlToContext.get(join);
            }
            return ctx != null ? Range.range(ctx) : null;
        }

        /**
         * Says which condition is at fault, why it cannot stay, and the two ways out — in the
         * vocabulary of the query rather than of the model. The old wording named only the alias
         * and called the WHERE the "all-filter", which appears nowhere in KQL.
         */
        private String message(UnaryLogicalExpression predicate) {
            String table = join.getSource() != null && join.getSource().getName() != null
                    ? join.getSource().getName() + " " + alias
                    : alias;
            String condition = describe(predicate);

            StringBuilder sb = new StringBuilder();
            sb.append("condition ");
            if (condition != null) {
                sb.append('`').append(condition).append("` ");
            }
            sb.append("filters the optional table `").append(table)
                    .append("`, which would drop the rows that have no match and make the join a "
                            + "required one. ");
            sb.append("Either compare only columns of `").append(alias)
                    .append("` so the condition can move into the join, or make the join required");
            if (join.getSource() != null && join.getSource().getName() != null) {
                sb.append(" (write `").append(join.getSource().getName()).append(' ').append(alias)
                        .append("` instead of `+").append(join.getSource().getName()).append(' ')
                        .append(alias).append("`)");
            }
            sb.append('.');
            return sb.toString();
        }

        /** {@code o.ship_city = c.city} — the columns and operator, without a full serializer. */
        private static String describe(UnaryLogicalExpression predicate) {
            if (predicate == null || predicate.getOp() == null) {
                return null;
            }
            List<String> operands = new ArrayList<>();
            if (predicate.getLeft() != null) {
                operands.add(operand(predicate.getLeft()));
            }
            for (Expression e : predicate.getRight()) {
                operands.add(operand(e));
            }
            if (operands.stream().anyMatch(java.util.Objects::isNull)) {
                return null;
            }
            return String.join(" " + predicate.getOp() + " ", operands);
        }

        private static String operand(Expression e) {
            if (e == null) {
                return null;
            }
            if (e.getField() != null) {
                return e.getField().getAlias() + "." + e.getField().getName();
            }
            if (e.getIdentity() != null) {
                return e.getIdentity();
            }
            if (e.getFunction() != null) {
                Set<String> aliases = new LinkedHashSet<>();
                for (Expression a : e.getFunction().getArguments()) {
                    String s = operand(a);
                    if (s != null) {
                        aliases.add(s);
                    }
                }
                return e.getFunction().getFunc() + "(" + String.join(", ", aliases) + ")";
            }
            return null;   // a literal or something we cannot name — the message drops the quote
        }
    }
}
