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
package ai.koryki.iql.validate;

import ai.koryki.antlr.Range;
import ai.koryki.iql.Collector;
import ai.koryki.iql.Visitor;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
import org.antlr.v4.runtime.RuleContext;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An entity used where a value belongs.
 *
 * <p>Naming an entity instead of a column — {@code count(od)} rather than {@code count(od.quantity)}
 * — means "the rows themselves". Two functions can answer that: {@code count} asks how many rows
 * there are, {@code count_distinct} how many different ones. For every other function the phrase has
 * no meaning: there is no smallest order line, and no sum of one.
 *
 * <p>It was accepted anyway, and answered. {@code IdentityRule} replaces an entity with its primary
 * key and never asked which function it stood in, so {@code min(od)} became
 * {@code min(od.order_id)} — measured, 10248: the lowest order number, offered as the answer to a
 * question about order lines. That is the failure worth preventing, because nothing about the result
 * says it answered something else.
 *
 * <p><b>Why this runs before the rewrite rules and not with the other function checks.</b> By the
 * time {@code FunctionValidator} sees the query, {@code IdentityRule} has already turned the entity
 * into a column and there is nothing left to object to. The check has to happen while the identity
 * is still there — the same reason {@link PlaceholderValidator} runs in that stage.
 */
public class IdentityUseValidator implements Visitor, Collector<List<Violation>> {

    /**
     * Own category, like {@code PlaceholderValidator}'s: the query is not wrong about the schema or
     * about a function's signature — it asks a question that has no answer.
     */
    public static final String IDENTITY = "identity";

    /**
     * The two functions an entity is a sensible argument to. {@code count} keeps one key column on
     * purpose (a key is never null, so counting it counts rows — and under an outer join it counts
     * 0 for an unmatched row where {@code COUNT(*)} would count 1); {@code count_distinct} takes all
     * of them, because distinct rows are distinct key combinations.
     */
    private static final Set<String> COUNTING = Set.of("count", "count_distinct");

    private final List<Violation> violations = new ArrayList<>();
    private final Map<Object, RuleContext> iqlToContext;

    public IdentityUseValidator(Map<Object, RuleContext> iqlToContext) {
        this.iqlToContext = iqlToContext;
    }

    @Override
    public boolean visit(Deque<Object> deque, Expression expression) {
        if (expression.getIdentity() == null) {
            return true;
        }
        Function parent = Visitor.getNthElement(deque, 1)
                .map(e -> e instanceof Function f ? f : null).orElse(null);

        if (parent == null) {
            violations.add(new Violation(IDENTITY, expression, Range.of(iqlToContext, expression),
                    "'" + expression.getIdentity() + "' is an entity, not a value — name a column of"
                            + " it, or count it with count(" + expression.getIdentity() + ")"));
            return true;
        }
        if (!COUNTING.contains(parent.getFunc())) {
            violations.add(new Violation(IDENTITY, parent, Range.of(iqlToContext, parent),
                    "'" + parent.getFunc() + "' needs a value, not the entity '"
                            + expression.getIdentity() + "' — name the column to apply it to."
                            + " Only count and count_distinct take an entity."));
        }
        return true;
    }

    @Override
    public List<Violation> collect() {
        return violations;
    }
}
