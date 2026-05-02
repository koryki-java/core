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

import ai.koryki.antlr.Range;
import ai.koryki.iql.query.*;
import ai.koryki.iql.query.Set;
import ai.koryki.iql.validate.ValidateException;
import ai.koryki.iql.validate.Violation;
import org.antlr.v4.runtime.RuleContext;

import java.util.*;

public class SelectScopeCollector implements Visitor, Collector<Map<Object, Map<String, Source>>> {

    private Map<Object, Map<String, Source>> selectToAliases = new HashMap<>();
    private Deque<Object> selects = new ArrayDeque<>();
    private List<Violation> violations = new ArrayList<>();
    private Map<Object, RuleContext> iqlToContext;

    public SelectScopeCollector(Map<Object, RuleContext> iqlToContext) {
        this.iqlToContext = iqlToContext;
    }

    @Override
    public void leave(Query query) {
        if (!violations().isEmpty()) {
            throw new ValidateException(violations());
        }
    }

    @Override
    public Map<Object, Map<String, Source>> collect() {
        return selectToAliases;
    }

    @Override
    public boolean visit(Deque<Object> deque, Select select) {
        selects.push(select);

        Map<String, Source> a = aliases(select);
        selectToAliases.put(select, a);
        return true;
    }

    @Override
    public void leave(Select select) {
        selects.pop();
    }

    @Override
    public boolean visit(Deque<Object> deque, Exists exists) {
        selects.push(exists);

        Map<String, Source> aliases = new HashMap<>();
        exists.getJoin().forEach(j -> aliases(j, aliases));
        selectToAliases.put(exists, aliases);
        return true;
    }

    @Override
    public void leave(Exists exists) {
        selects.pop();
    }

    private Map<String, Source> aliases(Select select) {
        Map<String, Source> aliases = new HashMap<>();
        Source t = aliases.put(select.getStart().getAlias(), select.getStart());
        checkAmbiguousAlias(t, select.getStart());
        select.getJoin().forEach(j -> aliases(j, aliases));
        return aliases;
    }

    private void aliases(Join join, Map<String, Source> aliases) {
        if (join.getSource() != null) {
            Source t = aliases.put(join.getSource().getAlias(), join.getSource());
            checkAmbiguousAlias(t, join.getSource());
        } else if (join.getRef() != null) {
            Source t = aliases.put(join.getRef(), aliases.get(join.getRef()));
            if (t == null) {
                violations.add(new Violation("reference", join, Range.range(iqlToContext.get(join)), "invalid reference " + join.getRef()));
            }
        }
        join.getJoin().forEach(j -> aliases(j, aliases));
    }

    public static Source getLeading(Set set) {

        return getLeadingSelect(set).getStart();
    }

    public static Select getLeadingSelect(Set set) {
        if (set.getLeft() != null) {
            return getLeadingSelect(set.getLeft());
        } else {
            return set.getSelect();
        }
    }


    private void checkAmbiguousAlias(Source related, Source next) {
        if (related != null) {
            violations.add(new Violation("ambiguous", next, Range.range(iqlToContext.get(next)), "ambiguous alias: " + next.getAlias(),  Range.range(iqlToContext.get(related))));
        }
    }

    public List<Violation> violations() {
        return violations;
    }
}
