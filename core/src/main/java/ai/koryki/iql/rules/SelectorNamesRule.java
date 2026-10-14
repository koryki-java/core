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

import ai.koryki.catalog.MemberNames;
import ai.koryki.catalog.domain.Attribute;
import ai.koryki.catalog.domain.Entity;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.Visitor;
import ai.koryki.iql.Walker;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Query;
import ai.koryki.iql.query.Selector;
import ai.koryki.iql.query.Source;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Translates the names of struct fields in selectors from the language of the model to the physical
 * keys of the column's concept: {@code t.address@breitengrad} becomes {@code t.address@latitude}
 * when the German model calls that entry so. Everything after the rules, the validators and the
 * renderers, speaks physical keys only.
 *
 * <p>A name the model does not know stays as written, and a base that is not a plain field of a
 * table (a block's output, a function result) is left alone: a name the language does not translate
 * is a physical key, which is what a query wrote before models had member names.
 */
public class SelectorNamesRule {

    private final LinkResolver resolver;
    private final Map<String, Source> blocks;

    public SelectorNamesRule(Map<String, Source> blockIdToLeadingTableMap, LinkResolver resolver) {
        this.blocks = blockIdToLeadingTableMap;
        this.resolver = resolver;
    }

    public void apply(Query query) {
        new Walker().walk(query, new NamesVisitor());
    }

    private class NamesVisitor implements Visitor {

        @Override
        public boolean visit(Deque<Object> deque, Expression expression) {
            if (expression.getSelection() == null
                    || expression.getSelection().getBase().getField() == null) {
                return true;
            }
            var field = expression.getSelection().getBase().getField();
            var select = Visitor.parentSelect(deque);
            Source source =
                    select == null || field.getAlias() == null
                            ? null
                            : Visitor.findSourceInSelect(select, field.getAlias());
            if (source == null || blocks.containsKey(source.getName())) {
                return true;
            }
            Optional<Entity> entity = resolver.getModel().getEntity(source.getName());
            Optional<Attribute> attribute = entity.flatMap(e -> e.getAttribute(field.getName()));
            if (attribute.isEmpty()) {
                return true;
            }
            Optional<MemberNames.Level> level =
                    new MemberNames(resolver.getSchema(), resolver.getModel())
                            .of(entity.get(), attribute.get());
            translate(level.orElse(null), expression.getSelection().getSelectors());
            return true;
        }
    }

    private static void translate(MemberNames.Level level, List<Selector> steps) {
        for (Selector step : steps) {
            if (level == null) {
                return;
            }
            String key = null;
            if (step.getId() != null || step.getName() != null) {
                String written = step.getId() != null ? step.getId() : step.getName();
                key = level.physical(written).orElse(null);
                // only a name the model gave is translated; a field it says nothing about keeps
                // the spelling of the query, as the struct finds it without regard to case
                if (key != null && level.member(key).map(m -> m.getName() != null).orElse(false)) {
                    if (step.getId() != null) {
                        step.setId(key);
                    } else {
                        step.setName(key);
                    }
                }
            } else if ("STRUCT".equals(level.concept().getTypeFamily())) {
                if (step.getIndex() != null) {
                    List<String> keys = level.keys();
                    int i =
                            step.getIndex() > 0
                                    ? step.getIndex() - 1
                                    : keys.size() + step.getIndex();
                    key = i >= 0 && i < keys.size() ? keys.get(i) : null;
                }
            } else if ("ARRAY".equals(level.concept().getTypeFamily())) {
                key = "element";
            } else if ("MAP".equals(level.concept().getTypeFamily())) {
                key = "value";
            }
            level = key == null ? null : level.into(key).orElse(null);
        }
    }
}
