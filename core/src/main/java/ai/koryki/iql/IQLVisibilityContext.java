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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class IQLVisibilityContext {

    private Map<String, Block> blockIdToBlockMap;
    private final Map<String, Source> blockIdToLeadingSourceMap;
    private final Map<String, Source> aliasToSourceMap;
    private final Map<Object, Map<String, Source>> childToAliasesMap;

    public IQLVisibilityContext(Map<String, Block> blockIdToBlockMap, Map<String, Source> blockIdToLeadingSourceMap,
                                Map<Object, Map<String, Source>> childToAliasesMap) {

        this(blockIdToBlockMap, blockIdToLeadingSourceMap, childToAliasesMap, Collections.emptyMap());
    }

    public IQLVisibilityContext(Map<String, Block> blockIdToBlockMap, Map<String, Source> blockIdToLeadingSourceMap,
                                Map<Object, Map<String, Source>> childToAliasesMap,
                                Map<String, Source> aliasToSourceMap) {

        this.blockIdToBlockMap = blockIdToBlockMap;
        this.blockIdToLeadingSourceMap = blockIdToLeadingSourceMap;
        this.childToAliasesMap = childToAliasesMap;
        this.aliasToSourceMap = aliasToSourceMap;
    }

    public IQLVisibilityContext child(Object child) {

        Map<String, Source> childMap = new HashMap<>();
        childMap.putAll(aliasToSourceMap);
        Map<String, Source> c = childToAliasesMap.get(child);
        if (c != null) {
            childMap.putAll(c);
        }

        return new IQLVisibilityContext(blockIdToBlockMap, blockIdToLeadingSourceMap, childToAliasesMap, childMap);
    }

    public Source getLeadingSource(String source) {
        return blockIdToLeadingSourceMap.get(source);
    }

    public Source getSource(String alias) {
        return aliasToSourceMap.get(alias);
    }

//    public Block getBlock(String name) {
//        return blockIdToBlockMap.get(name);
//    }
//
//    public Source rootTable(Source table) {
//        Source leadingBlockTable = getLeadingSource(table.getName());
//        if (leadingBlockTable != null) {
//            return getLeadingSource(leadingBlockTable.getName());
//        }
//        return table;
//    }
}
