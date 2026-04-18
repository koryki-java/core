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
package ai.koryki.iql;

import ai.koryki.iql.query.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

public class Walker {

    static public <V extends Collector<R>, R> R apply(Query query, V v) {
        new Walker().walk(query, v);
        return v.collect();
    }

    static public <V extends Collector<R>, R> R apply(Block block, V v) {
        new Walker().walk(block, v);
        return v.collect();
    }

    static public <V extends Collector<R>, R> R apply(Set set, V v) {
        new Walker().walk(set, v);
        return v.collect();
    }

    static public <V extends Collector<R>, R> R apply(Select select, V v) {
        new Walker().walk(select, v);
        return v.collect();
    }

    static public <V extends Collector<R>, R> R apply(Join join, V v) {
        new Walker().walk(join, v);
        return v.collect();
    }

    static public <V extends Collector<R>, R> R apply(Source table, V v) {
        new Walker().walk(table, v);
        return v.collect();
    }

    static public <V extends Collector<R>, R> R apply(Out out, V v) {
        new Walker().walk(out, v);
        return v.collect();
    }

    static public <V extends Collector<R>, R> R apply(LogicalExpression logicalExpression, V v) {
        new Walker().walk(logicalExpression, v);
        return v.collect();
    }

    static public <V extends Collector<R>, R> R apply(UnaryLogicalExpression unaryLogicalExpression, V v) {
        new Walker().walk(unaryLogicalExpression, v);
        return v.collect();
    }

    static public <V extends Collector<R>, R> R apply(Group group, V v) {
        new Walker().walk(group, v);
        return v.collect();
    }

    static public <V extends Collector<R>, R> R apply(Order order, V v) {
        new Walker().walk(order, v);
        return v.collect();
    }

    static public <V extends Collector<R>, R> R apply(Expression expression, V v) {
        new Walker().walk(expression, v);
        return v.collect();
    }

    static public <V extends Collector<R>, R> R apply(Function function, V v) {
        new Walker().walk(function, v);
        return v.collect();
    }

    static public <V extends Collector<R>, R> R apply(Field column, V v) {
        new Walker().walk(column, v);
        return v.collect();
    }

    static public <V extends Collector<R>, R> R apply(Exists exists, V v) {
        new Walker().walk(exists, v);
        return v.collect();
    }

    static public <V extends Visitor, R> R apply(Query query, V v, java.util.function.Function<V, R> f) {
        new Walker().walk(query, v);
        return f.apply(v);
    }

    static public <V extends Visitor, R> R apply(Block block, V v, java.util.function.Function<V, R> f) {
        new Walker().walk(block, v);
        return f.apply(v);
    }

    static public <V extends Visitor, R> R apply(Set set, V v, java.util.function.Function<V, R> f) {
        new Walker().walk(set, v);
        return f.apply(v);
    }

    static public <V extends Visitor, R> R apply(Select select, V v, java.util.function.Function<V, R> f) {
        new Walker().walk(select, v);
        return f.apply(v);
    }

    static public <V extends Visitor, R> R apply(Join join, V v, java.util.function.Function<V, R> f) {
        new Walker().walk(join, v);
        return f.apply(v);
    }

    static public <V extends Visitor, R> R apply(Source table, V v, java.util.function.Function<V, R> f) {
        new Walker().walk(table, v);
        return f.apply(v);
    }

    static public <V extends Visitor, R> R apply(Out out, V v, java.util.function.Function<V, R> f) {
        new Walker().walk(out, v);
        return f.apply(v);
    }

    static public <V extends Visitor, R> R apply(LogicalExpression logicalExpression, V v, java.util.function.Function<V, R> f) {
        new Walker().walk(logicalExpression, v);
        return f.apply(v);
    }

    static public <V extends Visitor, R> R apply(UnaryLogicalExpression unaryLogicalExpression, V v, java.util.function.Function<V, R> f) {
        new Walker().walk(unaryLogicalExpression, v);
        return f.apply(v);
    }

    static public <V extends Visitor, R> R apply(Group group, V v, java.util.function.Function<V, R> f) {
        new Walker().walk(group, v);
        return f.apply(v);
    }

    static public <V extends Visitor, R> R apply(Order order, V v, java.util.function.Function<V, R> f) {
        new Walker().walk(order, v);
        return f.apply(v);
    }

    static public <V extends Visitor, R> R apply(Expression expression, V v, java.util.function.Function<V, R> f) {
        new Walker().walk(expression, v);
        return f.apply(v);
    }

    static public <V extends Visitor, R> R apply(Function function, V v, java.util.function.Function<V, R> f) {
        new Walker().walk(function, v);
        return f.apply(v);
    }

    static public <V extends Visitor, R> R apply(Field column, V v, java.util.function.Function<V, R> f) {
        new Walker().walk(column, v);
        return f.apply(v);
    }

    static public <V extends Visitor, R> R apply(Exists exists, V v, java.util.function.Function<V, R> f) {
        new Walker().walk(exists, v);
        return f.apply(v);
    }

    private final Deque<Object> deque = new ArrayDeque<>();

    public boolean walk(Query query, Visitor visitor) {

        if (query == null) {
            return true;
        }

        return visitor.visit(deque, query) &&
                visitScoped(query, () ->
                           query.getBlock().stream().allMatch(b -> walk(b, visitor))
                        && walk(query.getSet(), visitor), visitor::leave);
    }

    public boolean walk(Block block, Visitor visitor) {
        if (block == null) {
            return true;
        }
        return visitor.visit(deque, block) &&
                visitScoped(block, () ->
                        walk(block.getSet(), visitor), visitor::leave);
    }


        public boolean walk(Set set, Visitor visitor) {

        if (set == null) {
            return true;
        }

        return visitor.visit(deque, set) &&
                visitScoped(set, () ->
                           walk(set.getLeft(), visitor)
                        && walk(set.getRight(), visitor)
                        && walk(set.getSelect(), visitor), visitor::leave);
    }

    public boolean walk(Select select, Visitor visitor) {

        if (select == null) {
            return true;
        }

        return visitor.visit(deque, select) &&
            visitScoped(select, () ->
                       walk(select.getStart(), visitor)
                    && select.getJoin().stream().allMatch(j -> walk(j, visitor))
                    && walk(select.getFilter(), visitor)
                    && walk(select.getHaving(), visitor)

                    && select.getOut().stream().allMatch(j -> walk(j, visitor))
                    && select.getGroup().stream().allMatch(j -> walk(j, visitor))
                    && select.getOrder().stream().allMatch(j -> walk(j, visitor))
                    , visitor::leave);
    }

    public boolean walk(Join join, Visitor visitor) {

        if (join == null) {
            return true;
        }

        return visitor.visit(deque, join) &&
                visitScoped(join, () ->
                           walk(join.getSource(), visitor)
                        && join.getJoin().stream().allMatch(j -> walk(j, visitor)), visitor::leave);
    }

    public boolean walk(Exists exists, Visitor visitor) {

        if (exists == null) {
            return true;
        }

        return visitor.visit(deque, exists) &&
                visitScoped(exists, () ->
                           walk(exists.getSource(), visitor)
                        && exists.getJoin().stream().allMatch(j -> walk(j, visitor)), visitor::leave);
    }

    public boolean walk(Source table, Visitor visitor) {

        if (table == null) {
            return true;
        }

        return visitor.visit(deque, table) &&
                visitScoped(table, () ->
                           table.getOut().stream().allMatch(o -> walk(o, visitor))
                        && walk(table.getFilter(), visitor)
                        && walk(table.getHaving(), visitor)
                        && table.getGroup().stream().allMatch(g -> walk(g, visitor))
                        && table.getOrder().stream().allMatch(o -> walk(o, visitor)), visitor::leave);
    }

    public boolean walk(Out out, Visitor visitor) {

        if (out == null) {
            return true;
        }

        return visitor.visit(deque, out) &&
                visitScoped(out, () -> walk(out.getExpression(), visitor), visitor::leave);
    }

    public boolean walk(Group group, Visitor visitor) {

        if (group == null) {
            return true;
        }

        return visitor.visit(deque, group) &&
                visitScoped(group, () -> walk(group.getExpression(), visitor), visitor::leave);
    }

    public boolean walk(Order order, Visitor visitor) {

        if (order == null) {
            return true;
        }

        return visitor.visit(deque, order) &&
                visitScoped(order, () -> walk(order.getExpression(), visitor), visitor::leave);
    }

    public boolean walk(LogicalExpression expression, Visitor visitor) {

        if (expression == null) {
            return true;
        }

        return visitor.visit(deque, expression)
                && visitScoped(expression, () ->
                           walk(expression.getUnaryRelationalExpression(), visitor)
                        && expression.getChildren().stream().allMatch(c -> walk(c, visitor)), visitor::leave);
    }

    public boolean walk(UnaryLogicalExpression expression, Visitor visitor) {

        if (expression == null) {
            return true;
        }

        return visitor.visit(deque, expression)
                && visitScoped(expression, () ->
                           walk(expression.getExists(), visitor)
                        && walk(expression.getNode(), visitor)
                        && walk(expression.getLeft(), visitor)
                        && expression.getRight().stream().allMatch(e -> walk(e, visitor)), visitor::leave);
    }

    public boolean walk(Expression expression, Visitor visitor) {

        if (expression == null) {
            return true;
        }

        return visitor.visit(deque, expression) &&
                visitScoped(expression, () ->
                           walk(expression.getSelect(), visitor)
                        && walk(expression.getFunction(), visitor)
                        && walk(expression.getField(), visitor), visitor::leave
        );
    }

    public boolean walk(Function function, Visitor visitor) {

        if (function == null) {
            return true;
        }

        return visitor.visit(deque, function)
                && visitScoped(function, () -> function.getArguments().stream().allMatch(a -> walk(a, visitor))
                , visitor::leave);
    }

    public boolean walk(Field column, Visitor visitor) {

        return column == null || visitor.visit(deque, column);
    }

    private <T> boolean visitScoped(T scope, java.util.function.Supplier<Boolean> walk, Consumer<T> leave) {
        try {
            deque.push(scope);
            Boolean b = walk.get();
            leave.accept(scope);
            return b;
        } finally {
            deque.pop();
        }
    }
}
