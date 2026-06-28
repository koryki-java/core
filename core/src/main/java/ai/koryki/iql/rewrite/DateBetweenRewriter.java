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
package ai.koryki.iql.rewrite;

import ai.koryki.iql.logic.NodeType;
import ai.koryki.iql.query.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Rewrites {@code BETWEEN} with a DATE or TIMESTAMP upper literal to a half-open interval.
 *
 * <pre>
 *   expr BETWEEN lower AND upper          (DATE or TIMESTAMP literal)
 *   →  expr &gt;= lower AND expr &lt; exclusiveUpper
 * </pre>
 *
 * DATE upper:      exclusiveUpper = upper + 1 day  (LocalDate.plusDays(1))
 * TIMESTAMP upper: exclusiveUpper = start of next day  (date part + 1 day at midnight)
 *
 * The {@code expr} (left side) is shared by reference in both new conditions — safe because
 * the renderer is read-only and the mutation is rolled back after rendering.
 *
 * The rewrite temporarily mutates filter/having references on Select, Source and Exists
 * containers. {@link #rewrite} returns a {@code Runnable} that restores the original state;
 * callers must invoke it (preferably in a {@code finally} block) after rendering is complete.
 */
public class DateBetweenRewriter {

    /**
     * Applies the BETWEEN → half-open rewrite to the query and returns a restore action.
     * The caller is responsible for calling {@code restore.run()} after rendering.
     */
    public static Runnable rewrite(Query query) {
        List<Runnable> restores = new ArrayList<>();
        query.getBlock().forEach(b -> rewriteSet(b.getSet(), restores));
        rewriteSet(query.getSet(), restores);
        return () -> restores.forEach(Runnable::run);
    }

    private static void rewriteSet(Set set, List<Runnable> restores) {
        if (set == null) return;
        rewriteSet(set.getLeft(), restores);
        rewriteSet(set.getRight(), restores);
        rewriteSelect(set.getSelect(), restores);
    }

    private static void rewriteSelect(Select select, List<Runnable> restores) {
        if (select == null) return;
        setFilter(select, rewriteLogical(select.getFilter(), restores), restores);
        setHaving(select, rewriteLogical(select.getHaving(), restores), restores);
        rewriteSource(select.getStart(), restores);
        select.getJoin().forEach(j -> rewriteJoin(j, restores));
    }

    private static void rewriteJoin(Join join, List<Runnable> restores) {
        if (join == null) return;
        rewriteSource(join.getSource(), restores);
        join.getJoin().forEach(j -> rewriteJoin(j, restores));
    }

    private static void rewriteSource(Source source, List<Runnable> restores) {
        if (source == null) return;
        setFilter(source, rewriteLogical(source.getFilter(), restores), restores);
        setHaving(source, rewriteLogical(source.getHaving(), restores), restores);
    }

    private static void rewriteExists(Exists exists, List<Runnable> restores) {
        if (exists == null) return;
        setFilter(exists, rewriteLogical(exists.getFilter(), restores), restores);
        setHaving(exists, rewriteLogical(exists.getHaving(), restores), restores);
        rewriteSource(exists.getStart(), restores);
        exists.getJoin().forEach(j -> rewriteJoin(j, restores));
    }

    // --- filter/having setters with restore tracking ---

    private static void setFilter(Select s, LogicalExpression newVal, List<Runnable> restores) {
        LogicalExpression old = s.getFilter();
        if (newVal != old) { s.setFilter(newVal); restores.add(() -> s.setFilter(old)); }
    }

    private static void setHaving(Select s, LogicalExpression newVal, List<Runnable> restores) {
        LogicalExpression old = s.getHaving();
        if (newVal != old) { s.setHaving(newVal); restores.add(() -> s.setHaving(old)); }
    }

    private static void setFilter(Source s, LogicalExpression newVal, List<Runnable> restores) {
        LogicalExpression old = s.getFilter();
        if (newVal != old) { s.setFilter(newVal); restores.add(() -> s.setFilter(old)); }
    }

    private static void setHaving(Source s, LogicalExpression newVal, List<Runnable> restores) {
        LogicalExpression old = s.getHaving();
        if (newVal != old) { s.setHaving(newVal); restores.add(() -> s.setHaving(old)); }
    }

    private static void setFilter(Exists e, LogicalExpression newVal, List<Runnable> restores) {
        LogicalExpression old = e.getFilter();
        if (newVal != old) { e.setFilter(newVal); restores.add(() -> e.setFilter(old)); }
    }

    private static void setHaving(Exists e, LogicalExpression newVal, List<Runnable> restores) {
        LogicalExpression old = e.getHaving();
        if (newVal != old) { e.setHaving(newVal); restores.add(() -> e.setHaving(old)); }
    }

    // Returns a (possibly new) LogicalExpression with BETWEEN rewrites applied.
    // Unchanged subtrees are returned by reference (structural sharing).
    private static LogicalExpression rewriteLogical(LogicalExpression expr, List<Runnable> restores) {
        if (expr == null) return null;

        return switch (expr.getType()) {
            case VAR -> {
                UnaryLogicalExpression u = expr.getUnaryRelationalExpression();
                rewriteInUnary(u, restores);
                yield isBetweenWithTemporalUpper(u) ? expand(u) : expr;
            }
            case NOT -> {
                LogicalExpression child = rewriteLogical(expr.getChildren().get(0), restores);
                yield child == expr.getChildren().get(0) ? expr : LogicalExpression.not(child);
            }
            case AND, OR -> {
                List<LogicalExpression> orig = expr.getChildren();
                List<LogicalExpression> next = null;
                for (int i = 0; i < orig.size(); i++) {
                    LogicalExpression r = rewriteLogical(orig.get(i), restores);
                    if (r != orig.get(i)) {
                        if (next == null) next = new ArrayList<>(orig);
                        next.set(i, r);
                    }
                }
                yield next == null ? expr : LogicalExpression.andor(expr.getType(), next);
            }
        };
    }

    // Recurse into nested structures within a unary node (exists, subqueries, logical args).
    private static void rewriteInUnary(UnaryLogicalExpression u, List<Runnable> restores) {
        if (u == null) return;
        rewriteExists(u.getExists(), restores);
        if (u.getNode() != null) {
            LogicalExpression newNode = rewriteLogical(u.getNode(), restores);
            if (newNode != u.getNode()) {
                LogicalExpression oldNode = u.getNode();
                u.setNode(newNode);
                restores.add(() -> u.setNode(oldNode));
            }
        }
        rewriteExpression(u.getLeft(), restores);
        u.getRight().forEach(e -> rewriteExpression(e, restores));
    }

    // Recurse into nested subqueries and logical arguments embedded in an expression.
    private static void rewriteExpression(Expression e, List<Runnable> restores) {
        if (e == null) return;
        if (e.getSelect() != null) rewriteSelect(e.getSelect(), restores);
        if (e.getLogical() != null) {
            LogicalExpression newLogical = rewriteLogical(e.getLogical(), restores);
            if (newLogical != e.getLogical()) {
                LogicalExpression oldLogical = e.getLogical();
                e.setLogical(newLogical);
                restores.add(() -> e.setLogical(oldLogical));
            }
        }
        if (e.getFunction() != null) e.getFunction().getArguments()
                .forEach(arg -> rewriteExpression(arg, restores));
    }

    private static boolean isBetweenWithTemporalUpper(UnaryLogicalExpression u) {
        if (u == null || !"BETWEEN".equals(u.getOp()) || u.getRight().size() != 2) return false;
        Expression upper = u.getRight().get(1);
        return upper.getLocalDate() != null || upper.getLocalDateTime() != null;
    }

    private static LogicalExpression expand(UnaryLogicalExpression between) {
        Expression left  = between.getLeft();          // shared reference — safe
        Expression lower = between.getRight().get(0);  // shared reference — safe

        return LogicalExpression.and(
                LogicalExpression.value(unary(">=", left, lower)),
                LogicalExpression.value(unary("<",  left, exclusiveUpper(between.getRight().get(1))))
        );
    }

    private static UnaryLogicalExpression unary(String op, Expression left, Expression right) {
        UnaryLogicalExpression u = new UnaryLogicalExpression();
        u.setLeft(left);
        u.setOp(op);
        u.setRight(List.of(right));
        return u;
    }

    private static Expression exclusiveUpper(Expression upper) {
        Expression result = new Expression();
        if (upper.getLocalDate() != null) {
            result.setLocalDate(upper.getLocalDate().plusDays(1));
        } else {
            // TIMESTAMP: strip time, move to next day's midnight
            result.setLocalDateTime(upper.getLocalDateTime().toLocalDate().plusDays(1).atStartOfDay());
        }
        return result;
    }
}
