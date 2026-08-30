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

import ai.koryki.iql.query.Block;
import ai.koryki.iql.query.Source;

import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class BlockLeadingSourceCollector implements Visitor, Collector<Map<String, Source>>{

    private Map<String, Source> blockIdToLeadingTableMap = new HashMap<>();

    /**
     * A block bound to a placeholder has no set to lead with, and is skipped rather than entered:
     * {@code getLeading(null)} used to raise an NPE inside {@code SelectScopeCollector} here, before
     * the analysis reached the validator that has something useful to say about it — see
     * {@code PlaceholderValidator}. Leaving the id out of the map is right either way: an unbound
     * block has no leading source, and claiming one would be worse than admitting none.
     */
    @Override
    public boolean visit(Deque<Object> deque, Block block) {
        if (block.getSet() == null) {
            return true;
        }
        blockIdToLeadingTableMap.put(block.getId(), SelectScopeCollector.getLeading(block.getSet()));
        return true;
    }

    @Override
    public Map<String, Source> collect() {
        return blockIdToLeadingTableMap;
    }
}
