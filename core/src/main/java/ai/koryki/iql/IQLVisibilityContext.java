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
import ai.koryki.iql.typing.RowType;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class IQLVisibilityContext {

    private Map<String, Block> blockIdToBlockMap;
    private final Map<String, Source> blockIdToLeadingSourceMap;
    private final Map<String, Source> aliasToSourceMap;
    private final Map<Object, Map<String, Source>> childToAliasesMap;

    /**
     * What the rows of each list made into rows are, found when the query is analysed. One map for
     * the root and every scope below it, by the identity of the source: it is a result of the
     * analysis of this query, and the beans of the query do not carry it.
     */
    private final Map<Source, RowType> rows;

    public IQLVisibilityContext(
            Map<String, Block> blockIdToBlockMap,
            Map<String, Source> blockIdToLeadingSourceMap,
            Map<Object, Map<String, Source>> childToAliasesMap) {

        this(
                blockIdToBlockMap,
                blockIdToLeadingSourceMap,
                childToAliasesMap,
                Collections.emptyMap());
    }

    public IQLVisibilityContext(
            Map<String, Block> blockIdToBlockMap,
            Map<String, Source> blockIdToLeadingSourceMap,
            Map<Object, Map<String, Source>> childToAliasesMap,
            Map<String, Source> aliasToSourceMap) {

        this(
                blockIdToBlockMap,
                blockIdToLeadingSourceMap,
                childToAliasesMap,
                aliasToSourceMap,
                new IdentityHashMap<>());
    }

    private IQLVisibilityContext(
            Map<String, Block> blockIdToBlockMap,
            Map<String, Source> blockIdToLeadingSourceMap,
            Map<Object, Map<String, Source>> childToAliasesMap,
            Map<String, Source> aliasToSourceMap,
            Map<Source, RowType> rows) {

        this.blockIdToBlockMap = blockIdToBlockMap;
        this.blockIdToLeadingSourceMap = blockIdToLeadingSourceMap;
        this.childToAliasesMap = childToAliasesMap;
        this.aliasToSourceMap = aliasToSourceMap;
        this.rows = rows;
    }

    /** What the rows of a list made into rows are; null for an entity, or a list with no type. */
    public RowType rowsOf(Source source) {
        return rows.get(source);
    }

    /** Records what the rows of a list made into rows are: the result of finding them. */
    public void putRows(Source source, RowType type) {
        rows.put(source, type);
    }

    public IQLVisibilityContext child(Object child) {

        Map<String, Source> childMap = new HashMap<>();
        childMap.putAll(aliasToSourceMap);
        Map<String, Source> c = childToAliasesMap.get(child);
        if (c != null) {
            childMap.putAll(c);
        }

        return new IQLVisibilityContext(
                blockIdToBlockMap, blockIdToLeadingSourceMap, childToAliasesMap, childMap, rows);
    }

    public Source getLeadingSource(String source) {
        return blockIdToLeadingSourceMap.get(source);
    }

    public Source getSource(String alias) {
        return aliasToSourceMap.get(alias);
    }

    /**
     * Every alias bound in this scope, the enclosing ones included.
     *
     * <p>{@link #child} layers an inner scope over its parent, so this is exactly the set an alias
     * written here could legally have named — which makes it the candidate set for a misspelt one.
     */
    public Set<String> aliases() {
        return Collections.unmodifiableSet(aliasToSourceMap.keySet());
    }

    public Block getBlock(String name) {
        return blockIdToBlockMap.get(name);
    }

    public Source rootTable(Source table) {
        Source leadingBlockSource = getLeadingSource(table.getName());
        if (leadingBlockSource != null) {
            return getLeadingSource(leadingBlockSource.getName());
        }
        return table;
    }
}
