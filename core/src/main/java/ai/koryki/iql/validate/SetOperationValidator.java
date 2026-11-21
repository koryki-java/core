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
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.Visitor;
import ai.koryki.iql.query.Set;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.RuleContext;

/**
 * The branches of a set operation ({@code UNION}, {@code UNION ALL}, {@code MINUS}, {@code
 * INTERSECT}) must return the same number of columns. A database refuses anything else ("queries
 * have different number of columns"), and says so only for the generated SQL, with a line number in
 * a text the author never wrote; this reports it for the query, at the branch that does not fit.
 *
 * <p>Only the number is checked. The names do not have to agree: the result takes those of the
 * first branch. Whether the types of one position fit each other is for the database to say, and
 * differs between them (DuckDB converts a date to text, PostgreSQL does not).
 */
public class SetOperationValidator implements Visitor, Collector<List<Violation>> {

    /** The category: the query is wrong, on every database. */
    public static final String CATEGORY = "set";

    private final List<Violation> violations = new ArrayList<>();
    private final Map<Object, RuleContext> iqlToContext;

    public SetOperationValidator(Map<Object, RuleContext> iqlToContext) {
        this.iqlToContext = iqlToContext;
    }

    @Override
    public boolean visit(Deque<Object> deque, Set set) {
        if (set.getOperator() == null || set.getLeft() == null || set.getRight() == null) {
            return true;
        }
        // A nested set is a branch of its own: the first of its columns is its leftmost branch's,
        // and it is visited itself, so each pair is compared once, against the branch before it.
        int expected = SqlQueryRenderer.collectOut(set.getLeft()).size();
        int actual = SqlQueryRenderer.collectOut(set.getRight()).size();
        if (expected != actual) {
            Object branch =
                    set.getRight().getSelect() != null
                            ? set.getRight().getSelect()
                            : set.getRight();
            violations.add(
                    new Violation(
                            CATEGORY,
                            branch,
                            Range.of(iqlToContext, branch),
                            operator(set)
                                    + " needs the same number of columns in every branch: the first"
                                    + " branch returns "
                                    + expected
                                    + ", this one returns "
                                    + actual));
        }
        return true;
    }

    private static String operator(Set set) {
        return "UNIONALL".equals(set.getOperator()) ? "UNION ALL" : set.getOperator();
    }

    @Override
    public List<Violation> collect() {
        return violations;
    }
}
