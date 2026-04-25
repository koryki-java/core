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
    private Aggregate aggregat;
    private Query query;
    private Map<String, Source> blockIdToLeadingTableMap;
    private Map<Object, RuleContext> iqlToContext;

    public Rules(Aggregate aggregat, LinkResolver resolver, Map<String, Source> blockIdToLeadingTableMap, Query query,
                 Map<Object, RuleContext> iqlToContext) {
        this.aggregat = aggregat;
        this.resolver = resolver;
        this.query = query;
        this.blockIdToLeadingTableMap = blockIdToLeadingTableMap;
        this.iqlToContext = iqlToContext;
    }
    
    public Map<String, Source> apply() {
        new PushOutRule().apply(query);
        new InferJoinColumnsToBlockRule(query, resolver, blockIdToLeadingTableMap, iqlToContext).apply();
        new HavingRule(aggregat, query).apply();
        new GroupRule(aggregat, query).apply();
        new IdentityRule(blockIdToLeadingTableMap, resolver).apply(query);
        new PushLogicalExpressionRule().apply(query);
        new PushGroupRule().apply(query);
        new PushOrderRule().apply(query);
        new CheckOuterJoinFilterRule().apply(query);

        return blockIdToLeadingTableMap;
    }
}