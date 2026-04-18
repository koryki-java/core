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

import ai.koryki.antlr.Bag;
import ai.koryki.iql.query.*;

import java.util.Deque;
import java.util.Iterator;
import java.util.Optional;

public interface Visitor {

    default boolean visit(Deque<Object> deque, Query query) {return true;}
    default boolean visit(Deque<Object> deque, Block block) {return true;}
    default boolean visit(Deque<Object> deque, Set set) {return true;}
    default boolean visit(Deque<Object> deque, Select select) {return true;}
    default boolean visit(Deque<Object> deque, Join join) {return true;}
    default boolean visit(Deque<Object> deque, Source source) {return true;}
    default boolean visit(Deque<Object> deque, Out out) {return true;}
    default boolean visit(Deque<Object> deque, LogicalExpression logicalExpression) {return true;}
    default boolean visit(Deque<Object> deque, UnaryLogicalExpression logicalExpression) {return true;}
    default boolean visit(Deque<Object> deque, Group group) {return true;}
    default boolean visit(Deque<Object> deque, Order order) {return true;}
    default boolean visit(Deque<Object> deque, Expression expression) {return true;}
    default boolean visit(Deque<Object> deque, Function function) {return true;}
    default boolean visit(Deque<Object> deque, Field column) {return true;}
    default boolean visit(Deque<Object> deque, Exists column) {return true;}


    default void leave(Query query) {}
    default void leave(Block block) {}
    default void leave(Set set) {}
    default void leave(Select select) {}
    default void leave(Join join) {}
    default void leave(Source source) {}
    default void leave(Out out) {}
    default void leave(LogicalExpression logicalExpression) {}
    default void leave(UnaryLogicalExpression logicalExpression) {}
    default void leave(Group group) {}
    default void leave(Order order) {}
    default void leave(Expression expression) {}
    default void leave(Function function) {}
    default void leave(Field column) {}
    default void leave(Exists column) {}


    /**
     * Returns the n-th element from the deque.
     * n = 1 → element
     * n = 2 → second element
     *
     * @param deque the deque to read from
     * @param n     1-based index from the strat
     * @param <E>   element type
     * @return Optional with the n-th element or Optional.empty()
     */
    static <E> Optional<E> getNthElement(Deque<E> deque, int n) {
        if (deque == null || n <= 0 || n > deque.size()) {
            return Optional.empty();
        }

        Iterator<E> iter = deque.iterator();
        for (int i = 1; i < n; i++) {
            Object o = iter.next();
        }
        E result = iter.next();
        return Optional.of(result);
    }

    static <E> Select parentSelect(Deque<E> deque) {

        Iterator<E> iter = deque.iterator();
        while(true) {
            if (!iter.hasNext()) {
                return null;
            }
            Object e = iter.next();
            if (e instanceof Select) {
                return (Select)e;
            }
        }
    }

    static Source findSourceInSelect(Select select, String alias) {

        Bag<Source> t = new Bag<>();
        Visitor v = new Visitor() {
            @Override
            public boolean visit(Deque<Object> deque, Source table) {
                if (table.getAlias().equals(alias)) {
                    t.setItem(table);
                }
                return true;
            }
        };
        new Walker().walk(select, v);

        return t.getItem();
    }

    static <E> Source findSourceInParentSelect(Deque<E> deque, String alias) {
        return findSourceInSelect(parentSelect(deque), alias);
    }
}
