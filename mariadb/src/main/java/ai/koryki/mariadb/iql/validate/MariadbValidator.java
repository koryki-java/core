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
package ai.koryki.mariadb.iql.validate;

import ai.koryki.antlr.Range;
import ai.koryki.iql.Collector;
import ai.koryki.iql.query.Join;
import ai.koryki.iql.query.Select;
import ai.koryki.iql.query.Source;
import ai.koryki.iql.validate.Violation;
import org.antlr.v4.runtime.RuleContext;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * MariaDB-specific validation — the dialect analogue of the core validators, a {@link Collector}
 * run via {@code Walker.apply}, which visits every SELECT including sub-selects.
 *
 * <p>Currently the only rule: ROLLUP combined with ORDER BY on the same SELECT. MariaDB (verified
 * on 11.8) rejects it with {@code ERROR 1221 (HY000): Incorrect usage of CUBE/ROLLUP and ORDER BY}.
 * ROLLUP itself is supported — {@code MariadbDialect} renders it as a trailing {@code WITH ROLLUP}
 * — so this is narrower than the SQLite rule, which rejects rollup outright.
 *
 * <p>The category is {@link Violation#UNSUPPORTED}: that is what marks the failure as "this
 * dialect cannot express this query" rather than "this query is wrong", and it is what lets a
 * shared fixture be skipped here instead of failing the suite.
 *
 * <p><b>MySQL caveat.</b> The module is spelled for MariaDB and verified against it. MySQL lifted
 * this restriction in 8.0.12, so if a MySQL dialect is ever split out of this one, this rule must
 * not come along unchanged.
 */
public class MariadbValidator implements Collector<List<Violation>> {

    private final List<Violation> violations = new ArrayList<>();
    private final Map<Object, RuleContext> iqlToContext;
    private final ai.koryki.iql.validate.ValidationContext context;

    public MariadbValidator(ai.koryki.iql.validate.ValidationContext context) {
        this.context = context;
        this.iqlToContext = context.iqlToContext();
    }

    @Override
    public boolean visit(Deque<Object> deque, Select select) {
        if (select.isRollup() && hasOrderBy(select)) {
            violations.add(new Violation(Violation.UNSUPPORTED, select, range(select),
                    "MariaDB does not support ORDER BY combined with ROLLUP"
                            + " (server error 1221: \"Incorrect usage of CUBE/ROLLUP and ORDER BY\"); "
                            + "remove the ORDER BY, or wrap the rollup query in a sub-select and sort the outer query."));
        }
        return true;
    }

    @Override
    public List<Violation> collect() {
        return violations;
    }

    private Range range(Select select) {
        RuleContext ctx = iqlToContext != null ? iqlToContext.get(select) : null;
        return ctx != null ? Range.range(ctx) : null;
    }

    /**
     * Mirrors {@code SqlSelectRenderer}'s ORDER BY assembly: the rendered
     * ORDER BY of a SELECT is the union of the orders declared on the SELECT
     * itself, on its start source and on every (transitively) joined source.
     */
    private static boolean hasOrderBy(Select select) {
        if (!select.getOrder().isEmpty()) {
            return true;
        }
        if (select.getStart() != null
                && select.getStart().getOrder() != null
                && !select.getStart().getOrder().isEmpty()) {
            return true;
        }
        return joinHasOrder(select.getJoin());
    }

    private static boolean joinHasOrder(List<Join> joins) {
        for (Join join : joins) {
            Source source = join.getSource();
            if (source != null && source.getOrder() != null && !source.getOrder().isEmpty()) {
                return true;
            }
            if (joinHasOrder(join.getJoin())) {
                return true;
            }
        }
        return false;
    }
}
