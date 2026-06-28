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
package ai.koryki.mariadb.iql;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.antlr.Range;
import ai.koryki.iql.Collector;
import ai.koryki.iql.query.Join;
import ai.koryki.iql.query.Select;
import ai.koryki.iql.query.Source;
import org.antlr.v4.runtime.RuleContext;

import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Rejects queries that combine ROLLUP with ORDER BY on the same SELECT.
 *
 * <p>MariaDB (verified on 11.8) does not allow this and aborts execution with
 * {@code ERROR 1221 (HY000): Incorrect usage of CUBE/ROLLUP and ORDER BY}. This
 * check catches the combination while the query is still in IQL form and raises
 * a located, readable error instead of letting the server reject the generated
 * SQL at runtime.
 *
 * <p>Run via {@link ai.koryki.iql.Walker#apply}, which visits every SELECT in
 * the query tree (including sub-selects), so nested rollups are validated too.
 * Detection is fail-fast: the first offending SELECT throws.
 */
public class RollupWithOrderByCheck implements Collector<Boolean> {

    private final Map<Object, RuleContext> iqlToContext;
    private boolean violation;

    public RollupWithOrderByCheck(Map<Object, RuleContext> iqlToContext) {
        this.iqlToContext = iqlToContext;
    }

    @Override
    public boolean visit(Deque<Object> deque, Select select) {
        if (select.isRollup() && hasOrderBy(select)) {
            violation = true;
            throw new KorykiaiException(message(select));
        }
        return true;
    }

    @Override
    public Boolean collect() {
        return violation;
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

    private String message(Select select) {
        String location = "";
        RuleContext ctx = iqlToContext != null ? iqlToContext.get(select) : null;
        if (ctx != null) {
            location = " at " + Range.range(ctx);
        }
        return "MariaDB does not support ORDER BY combined with ROLLUP" + location
                + " (server error 1221: \"Incorrect usage of CUBE/ROLLUP and ORDER BY\"); "
                + "remove the ORDER BY, or wrap the rollup query in a sub-select and sort the outer query.";
    }
}
