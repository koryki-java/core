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
package ai.koryki.kql;

import ai.koryki.iql.query.Source;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class KQLVisibilityContext {

    private final Map<String, Source> blockIdToLeadingSourceMap;
    private final Map<String, KQLParser.SourceContext> aliasToSourceMap;
    private final Map<Object, Map<String, KQLParser.SourceContext>> childToAliasesMap;

    public KQLVisibilityContext(Map<String, Source> blockIdToLeadingSourceMap,
                                Map<Object, Map<String, KQLParser.SourceContext>> childToAliasesMap) {

        this(blockIdToLeadingSourceMap, childToAliasesMap, Collections.emptyMap());
    }

    public KQLVisibilityContext(Map<String, Source> blockIdToLeadingSourceMap,
                                Map<Object, Map<String, KQLParser.SourceContext>> childToAliasesMap,
                                Map<String, KQLParser.SourceContext> aliasToSourceMap) {
        this.blockIdToLeadingSourceMap = blockIdToLeadingSourceMap;
        this.childToAliasesMap = childToAliasesMap;
        this.aliasToSourceMap = aliasToSourceMap;
    }

    public KQLVisibilityContext child(Object child) {

        Map<String, KQLParser.SourceContext> childMap = new HashMap<>();
        childMap.putAll(aliasToSourceMap);
        childMap.putAll(childToAliasesMap.get(child));

        return new KQLVisibilityContext(blockIdToLeadingSourceMap, childToAliasesMap, childMap);
    }

    public Source getLeadingSource(String tableName) {
        return blockIdToLeadingSourceMap.get(tableName);
    }

    public KQLParser.SourceContext getSource(String alias) {
        return aliasToSourceMap.get(alias);
    }
}
