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

import java.util.*;

/**
 * Must not check ambiguous alias here, because for legitim cycles in fetch-clause.
 */
public class SelectAliasListener extends KQLBaseListener {

    private Map<Object, Map<String, KQLParser.SourceContext>> selectToAliases = new HashMap<>();
    private Deque<Object> selects = new ArrayDeque<>();

    public SelectAliasListener() {
    }

    public Map<Object, Map<String, KQLParser.SourceContext>> collect() {
        return selectToAliases;
    }

    @Override public void enterSelect(KQLParser.SelectContext select) {

        selects.push(select);

        Map<String, KQLParser.SourceContext> a = aliases(select);
        selectToAliases.put(select, a);
    }

    @Override public void enterExists(KQLParser.ExistsContext exists) {

        selects.push(exists);
        Map<String, KQLParser.SourceContext> aliases = new HashMap<>();
        exists.link().forEach(l -> aliases(l, aliases));
        selectToAliases.put(exists, aliases);
    }

    @Override public void exitSelect(KQLParser.SelectContext ctx) {
        selects.pop();
    }

    private Map<String, KQLParser.SourceContext> aliases(KQLParser.SelectContext select) {
        Map<String, KQLParser.SourceContext> aliases = new HashMap<>();
        KQLParser.SourceContext t = aliases.put(select.source().alias.getText(), select.source());

        select.link().forEach(l -> aliases(l, aliases));
        return aliases;
    }

    private void aliases(KQLParser.LinkContext link, Map<String, KQLParser.SourceContext> aliases) {
        if (link.source() != null) {
            KQLParser.SourceContext t = aliases.put(link.source().alias.getText(), link.source());
        }
    }

    public static KQLParser.SourceContext getLeading(KQLParser.SetContext set) {

        return getLeadingSelect(set).source();
    }

    public static KQLParser.SelectContext getLeadingSelect(KQLParser.SetContext set) {
        if (!set.set().isEmpty()) {
            return getLeadingSelect(set.set(0));
        } else {
            return set.select();
        }
    }

}
