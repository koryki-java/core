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
import ai.koryki.iql.query.Block;
import ai.koryki.iql.query.UnaryLogicalExpression;
import org.antlr.v4.runtime.RuleContext;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * A placeholder that is still a placeholder when the query is analysed.
 *
 * <p>A placeholder marks a position the caller fills before the query runs — a value in a
 * comparison ({@code FILTER o.freight > #x}) or a whole sub-query bound to a block
 * ({@code WITH ord #p}). Filling it is the caller's job; a query that still carries one is a
 * template, not a query, and there is nothing to render.
 *
 * <p>Rendering it anyway is what this rule prevents, and the reason it exists: without it the four
 * grammatical positions produced four different wrong answers, two of them silent. Measured against
 * DuckDB before this validator existed:
 *
 * <pre>
 * FILTER o.freight &gt; #x         template {0} &gt; {1} references argument {1} but only 1 were provided
 * FILTER o.freight #x            WHERE o.freight null
 * FILTER c.country IN #laender   WHERE c.country IN ()
 * WITH ord #p                    NullPointerException in SelectScopeCollector.getLeadingSelect
 * </pre>
 *
 * <p>The middle two are the dangerous ones: valid-looking SQL that quietly means something else.
 * {@code WHERE o.freight null} is a syntax error on every engine, but {@code IN ()} parses on some
 * and matches nothing on those — a query that returns zero rows and no complaint.
 *
 * <p>Positioned on the node carrying the placeholder, so the message points at the {@code #name}
 * rather than at the query.
 */
public class PlaceholderValidator implements Visitor, Collector<List<Violation>> {

    /**
     * Category of this violation. Its own, not {@code "schema"} or {@code "function"}: an unbound
     * placeholder does not say the query is wrong — the same text becomes a valid query once the
     * caller supplies the value. A caller that knows how to bind can act on the category.
     */
    public static final String PLACEHOLDER = "placeholder";

    private final List<Violation> violations = new ArrayList<>();
    private final Map<Object, RuleContext> iqlToContext;

    public PlaceholderValidator(Map<Object, RuleContext> iqlToContext) {
        this.iqlToContext = iqlToContext;
    }

    @Override
    public boolean visit(Deque<Object> deque, UnaryLogicalExpression expression) {
        if (expression.getPlaceholder() != null) {
            violations.add(new Violation(PLACEHOLDER, expression, Range.of(iqlToContext, expression),
                    "placeholder '" + expression.getPlaceholder() + "' is not bound — supply a value"
                            + " for it before the query is rendered"));
        }
        return true;
    }

    @Override
    public boolean visit(Deque<Object> deque, Block block) {
        if (block.getPlaceholder() != null) {
            violations.add(new Violation(PLACEHOLDER, block, Range.of(iqlToContext, block),
                    "block '" + block.getId() + "' is bound to placeholder '" + block.getPlaceholder()
                            + "' — supply the sub-query for it before the query is rendered"));
        }
        return true;
    }

    @Override
    public List<Violation> collect() {
        return violations;
    }
}
