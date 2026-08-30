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

import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.query.Query;
import ai.koryki.iql.query.Source;
import org.antlr.v4.runtime.RuleContext;

import java.util.Map;

public class Rules {

    private LinkResolver resolver;
    private Query query;
    private Map<String, Source> blockIdToLeadingTableMap;
    private Map<Object, RuleContext> iqlToContext;

    public Rules(LinkResolver resolver, Map<String, Source> blockIdToLeadingTableMap, Query query,
                 Map<Object, RuleContext> iqlToContext) {
        this.resolver = resolver;
        this.query = query;
        this.blockIdToLeadingTableMap = blockIdToLeadingTableMap;
        this.iqlToContext = iqlToContext;
    }
    
    public Map<String, Source> apply() {
        new PushOutRule().apply(query);
        new InferJoinColumnsToBlockRule(query, resolver, blockIdToLeadingTableMap, iqlToContext).apply();
        new HavingRule(query).apply();
        new GroupRule(query, iqlToContext).apply();
        new IdentityRule(blockIdToLeadingTableMap, resolver, iqlToContext).apply(query);
        new PushLogicalExpressionRule().apply(query);
        new PushGroupRule().apply(query);
        new PushOrderRule().apply(query);
        new CheckOuterJoinFilterRule(iqlToContext).apply(query);

        return blockIdToLeadingTableMap;
    }

    /**
     * Takes over the origin of {@code origin} for all {@code created} nodes.
     *
     * <p>Does nothing when the trigger itself has no origin — then none better is known for the new
     * node either, and the previous behaviour stands.
     *
     * @param iqlToContext the model node to parser context mapping; may be {@code null}
     * @param origin       the node the new ones came into being for
     * @param created      the newly created nodes
     */
    static void inherit(Map<Object, RuleContext> iqlToContext, Object origin, Object... created) {

        if (iqlToContext == null || origin == null) {
            return;
        }

        RuleContext ctx = iqlToContext.get(origin);
        if (ctx == null) {
            return;
        }

        for (Object node : created) {
            if (node != null) {
                iqlToContext.putIfAbsent(node, ctx);
            }
        }
    }

}