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

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.antlr.Range;
import ai.koryki.antlr.RangeException;
import ai.koryki.iql.Identifier;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.query.*;
import ai.koryki.scaffold.schema.Relation;
import org.antlr.v4.runtime.RuleContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Add OUT-Expressions for Join-Columns, references by other queries.
 */
public class InferJoinColumnsToBlockRule {

    private LinkResolver resolver;
    private Query query;
    private Map<String, Source> blockIdToLeadingTableMap;
    private Map<Object, RuleContext> iqlToContext;

    public InferJoinColumnsToBlockRule(Query query, LinkResolver resolver, Map<String, Source> blockIdToLeadingTableMap,
                                       Map<Object, RuleContext> iqlToContext) {
        this.query = query;
        this.resolver = resolver;
        this.blockIdToLeadingTableMap = blockIdToLeadingTableMap;
        this.iqlToContext = iqlToContext;
    }

    public void apply() {
        apply(query);
    }

    private void apply(Query query) {

        query.getBlock().forEach(b -> apply(b));
        apply(null, query.getSet());
    }

    private void apply(Block block) {
        apply(block, block.getSet());
    }

    private void apply(Block block, Set set) {
        if (set.getOperator() != null) {
            apply(block, set.getLeft());
            apply(block, set.getRight());
        } else {
            apply(block, set.getSelect());
        }
    }

    private void apply(Block block, Select select) {

        apply(block, select.getStart(), select.getJoin());
    }

    protected void apply(Block block, Source left, List<Join> join) {

        for (Join j : join) {
            joinColumns(block, left, j);
            apply(block, j.getSource(), j.getJoin());
        }
    }

    private void joinColumns(Block block, Source left, Join join) {

        String msg = left.getName() + (left.getAlias() != null ? " " + left.getAlias() : "");
        String crit = join.getCrit();
        Source right = join.getSource();
        if (right != null) {
            //boolean invers = join.isInvers();
            boolean invers = resolver.isInverse(join.getCrit());

                    Source start = invers ? right : left;
            Source end = invers ? left : right;

            joinColumns(block, start, end, crit, msg, right);
        }
    }

    private void joinColumns(Block block, Source start, Source end, String crit, String msg, Source right) {
        String startTable = start.getName();
        String endTable = end.getName();

        boolean b1 = resolver.isTableInDatabase(startTable);
        Source s = b1 ? start : blockIdToLeadingTableMap.get(startTable);
        startTable = s.getName();
        startTable = Identifier.normal(Identifier.lowercase, startTable);

        boolean b2 = resolver.isTableInDatabase(endTable);
        Source e = b2 ? end : blockIdToLeadingTableMap.get(end.getName());

        endTable = Identifier.normal(Identifier.lowercase, e.getName());

        try {
            Optional<Relation> o = resolver.findRelation(Range.range(iqlToContext.get(start)), startTable, endTable, crit);
            if (!o.isPresent()) {
                throw new KorykiaiException(msg +  " " + crit + " " + right.getName());
            }

            // no need to extend out from previous set.
            Relation r = o.get();

            if (!b1) {
                List<String> cols = r.getStartColumns();
                if (block != null) {
                    enhanceOut(block.getSet(), cols);
                } else {
                    enhanceOut(s, cols);
                }
            }
            if (!b2) {
                List<String> cols = r.getEndColumns();
                if (block != null) {
                    enhanceOut(block.getSet(), cols);
                } else {
                    enhanceOut(e, cols);
                }
            }
        } catch (RangeException ex) {
            //ex.setRange(Range.range(iqlToContext.get(start)));
            throw ex;
        }
    }

    private void enhanceOut(Set set, List<String> cols) {
        if (set.getSelect() != null) {
            enhanceOut(set.getSelect().getStart(), cols);
        } else {
            enhanceOut(set.getLeft(), cols);
            enhanceOut(set.getRight(), cols);
        }
    }

    private void enhanceOut(Source table, List<String> cols) {
        for (String c : cols) {
            if (!hasOut(table, c)) {
                Out out = createOut(table, c);
                table.getOut().add(out);
            }
        }
    }

    private static Out createOut(Source table, String c) {
        Out out = new Out();
        Field col = new Field();
        col.setAlias(table.getAlias());
        col.setName(c);
        Expression e = new Expression();
        e.setField(col);
        out.setExpression(e);
        return out;
    }

    private boolean hasOut(Source t, String column) {

        return t.getOut().stream().filter(c -> c.getExpression().getField() != null)
                .map(o -> o.getExpression().getField())
                .anyMatch(c -> c.getName().equals(column));
    }
}
