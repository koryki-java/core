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

import ai.koryki.catalog.MemberNames;
import ai.koryki.catalog.domain.Attribute;
import ai.koryki.catalog.domain.Entity;
import ai.koryki.catalog.domain.Model;
import ai.koryki.iql.query.Selector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DictionaryTranslator implements Translator {

    private final Map<String, String> ld;
    private final Map<String, TableDictionary> sd;

    private final Model from;
    private final Model to;
    private final MemberNames fromNames;
    private final MemberNames toNames;

    public DictionaryTranslator(Map<String, String> ld, Map<String, TableDictionary> sd) {
        this(ld, sd, null, null, null);
    }

    /**
     * A translator that also translates the names of struct fields in selectors: what {@code from}
     * and {@code to} say of the parts of a column, found in the concepts of {@code schema}.
     */
    public DictionaryTranslator(
            Map<String, String> ld,
            Map<String, TableDictionary> sd,
            ai.koryki.catalog.schema.Schema schema,
            Model from,
            Model to) {
        this.ld = ld;
        this.sd = sd;
        this.from = from;
        this.to = to;
        this.fromNames = schema == null ? null : new MemberNames(schema, from);
        this.toNames = schema == null ? null : new MemberNames(schema, to);
    }

    @Override
    public List<Selector> selectors(String source, String field, List<Selector> steps) {
        TableDictionary dictionary = sd.get(source);
        if (fromNames == null || dictionary == null) {
            return steps;
        }
        Optional<Entity> fromEntity = from.getEntity(source);
        Optional<Entity> toEntity = to.getEntity(dictionary.getName());
        Optional<Attribute> fromAttribute = fromEntity.flatMap(e -> e.getAttribute(field));
        Optional<Attribute> toAttribute =
                toEntity.flatMap(e -> e.getAttribute(field(source, field)));
        if (fromAttribute.isEmpty()) {
            return steps;
        }
        MemberNames.Level fromLevel =
                fromNames.of(fromEntity.get(), fromAttribute.get()).orElse(null);
        MemberNames.Level toLevel =
                toEntity.isPresent() && toAttribute.isPresent()
                        ? toNames.of(toEntity.get(), toAttribute.get()).orElse(null)
                        : null;

        List<Selector> out = new ArrayList<>();
        for (Selector step : steps) {
            Selector next = step;
            String written = step.getId() != null ? step.getId() : step.getName();
            String key = null;
            if (fromLevel != null) {
                key = fromLevel.key(written, step.getIndex()).orElse(null);
                // a name is translated where either model gives the field one: the model the query
                // comes from may leave the field to its physical key, and the one it goes to name
                // it
                boolean namedBySource = key != null && fromLevel.named(key);
                boolean namedByTarget = key != null && toLevel != null && toLevel.named(key);
                if (key != null && written != null && (namedBySource || namedByTarget)) {
                    next = named(step, toLevel == null ? key : toLevel.written(key));
                }
            }
            out.add(next);
            fromLevel = key == null || fromLevel == null ? null : fromLevel.into(key).orElse(null);
            toLevel = key == null || toLevel == null ? null : toLevel.into(key).orElse(null);
        }
        return out;
    }

    /** The step with another name: an identifier where one fits, a quoted name where not. */
    private static Selector named(Selector step, String name) {
        Selector s = new Selector();
        if (step.getId() != null && name.matches("[a-z_][a-z0-9_]*")) {
            s.setId(name);
        } else {
            s.setName(name);
        }
        return s;
    }

    public String source(String source) {
        TableDictionary dictionary = sd.get(source);
        if (dictionary == null) {
            // no exception, maybe it's a reference to blockid
            return source;
        }
        return dictionary.getName();
    }

    public String field(String source, String field) {
        TableDictionary dictionary = sd.get(source);
        if (dictionary == null) {
            // no exception, maybe it's a reference to blockid
            return field;
        }
        String c = dictionary.getColumns().get(field);
        return c != null ? c : field;
    }

    public String crit(String crit) {
        return ld.get(crit);
    }

    public Map<String, String> getLinkDictionary() {
        return ld;
    }

    public Map<String, TableDictionary> getSchemaDictionary() {
        return sd;
    }
}
