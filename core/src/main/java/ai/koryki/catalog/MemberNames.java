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
package ai.koryki.catalog;

import ai.koryki.catalog.domain.Attribute;
import ai.koryki.catalog.domain.Entity;
import ai.koryki.catalog.domain.Member;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.domain.Notion;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Entry;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.PartNames;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The names of the parts of a nested column in the language of a model: where a schema says that
 * the struct of {@code address} has the entry {@code geo}, a model may call it {@code position},
 * and a query of that language writes {@code t.address@position}.
 *
 * <p>The schema is the same in every language, so its entries are the physical names; a model says
 * the names of its language in {@link Attribute#getMembers()}, for the concept of the column, and
 * in {@link Model#getNotions()}, for a concept that columns refer to by name. This class is the one
 * place that reads them: it walks the concept and the names together, a {@link Level} at a time,
 * and says for a name written in a query which entry it is, and for an entry what the language
 * calls it.
 *
 * <p>The names are only about the entries of a struct, which is what a query addresses by name. The
 * parts inside a list or a map (under the keys {@code element}, {@code key} and {@code value}) have
 * no name to write, but they can have members of their own: the fields of the structs a list holds.
 */
public final class MemberNames {

    private final Schema schema;
    private final Model model;

    public MemberNames(Schema schema, Model model) {
        this.schema = schema;
        this.model = model;
    }

    /**
     * One level of the nested value of a column: its concept, and what the model says about the
     * parts of it.
     */
    public final class Level {

        private final Concept concept;
        private final Map<String, Member> members;

        private Level(Concept concept, Map<String, Member> members) {
            this.concept = concept;
            this.members = members == null ? Map.of() : members;
        }

        public Concept concept() {
            return concept;
        }

        /**
         * The physical names of the entries a query can write by name: the fields of a struct, in
         * order; none for any other concept.
         */
        public List<String> keys() {
            return concept.getEntries() == null
                            || !CoreTypeFamily.STRUCT.is(concept.getTypeFamily())
                    ? List.of()
                    : new ArrayList<>(concept.getEntries().keySet());
        }

        /** What this language calls the entry: its name for it, else the physical name. */
        public String written(String key) {
            Member member = members.get(key);
            return member != null && member.getName() != null ? member.getName() : key;
        }

        /** The names this language has for the fields, in order. */
        public List<String> names() {
            return keys().stream().map(this::written).toList();
        }

        /**
         * The entry a name of this language stands for, as a query writes it: exactly, else without
         * regard to case, the way a struct field is found. Empty where no field has the name.
         */
        public Optional<String> physical(String name) {
            for (String key : keys()) {
                if (written(key).equals(name)) {
                    return Optional.of(key);
                }
            }
            for (String key : keys()) {
                if (written(key).equalsIgnoreCase(name)) {
                    return Optional.of(key);
                }
            }
            return Optional.empty();
        }

        /**
         * The entry a step of a selector chain goes to: a name is the field it is called, a
         * position on a struct the field at that position, any position or range on a list the
         * element, on a map the value. Empty where the step leads to no entry of this concept.
         */
        public Optional<String> key(String name, Integer index) {
            String family = concept.getTypeFamily();
            if (name != null) {
                return physical(name);
            } else if (CoreTypeFamily.STRUCT.is(family)) {
                List<String> keys = keys();
                if (index == null) {
                    return Optional.empty();
                }
                int i = index > 0 ? index - 1 : keys.size() + index;
                return i >= 0 && i < keys.size() ? Optional.of(keys.get(i)) : Optional.empty();
            } else if (CoreTypeFamily.ARRAY.is(family)) {
                return Optional.of("element");
            } else if (CoreTypeFamily.MAP.is(family)) {
                return Optional.of("value");
            }
            return Optional.empty();
        }

        /** Whether the model gives the entry a name of its own. */
        public boolean named(String key) {
            return member(key).map(m -> m.getName() != null).orElse(false);
        }

        /** These names as the parts of a type are shown to a person: see {@link PartNames}. */
        public PartNames partNames() {
            Level level = this;
            return new PartNames() {
                @Override
                public String of(String key) {
                    return level.written(key);
                }

                @Override
                public PartNames part(String key) {
                    return level.into(key).map(Level::partNames).orElse(PartNames.PLAIN);
                }
            };
        }

        /** The member of this language for the entry, if the model says something about it. */
        public Optional<Member> member(String key) {
            return Optional.ofNullable(members.get(key));
        }

        /**
         * The level inside an entry, for walking on: a concept of its own, or the concept of that
         * name in the schema. Empty for a part that is only a type spelling, which has nothing
         * below it.
         */
        public Optional<Level> into(String key) {
            if (concept.getEntries() == null) {
                return Optional.empty();
            }
            Entry entry = concept.getEntries().get(key);
            if (entry == null) {
                return Optional.empty();
            }
            Member member = members.get(key);
            Map<String, Member> own = member == null ? null : member.getMembers();
            if (entry.isInline()) {
                return Optional.of(new Level(entry.getConcept(), own));
            }
            Concept named =
                    schema.getConcepts() == null
                            ? null
                            : schema.getConcepts().get(entry.getReference());
            if (named == null) {
                return Optional.empty();
            }
            return Optional.of(
                    new Level(named, own != null ? own : notionMembers(entry.getReference())));
        }
    }

    private Map<String, Member> notionMembers(String concept) {
        Notion notion = model.getNotions() == null ? null : model.getNotions().get(concept);
        return notion == null ? null : notion.getMembers();
    }

    /**
     * The top level of an attribute: the concept of its column, and the members the attribute says.
     * Empty where the table, the column or the concept is not there.
     */
    public Optional<Level> of(Entity entity, Attribute attribute) {
        Optional<Column> column = column(entity, attribute);
        if (column.isEmpty()) {
            return Optional.empty();
        }
        Concept concept = column.get().getConcept();
        Map<String, Member> members = attribute.getMembers();
        if (concept == null
                && column.get().getConceptRef() != null
                && schema.getConcepts() != null) {
            concept = schema.getConcepts().get(column.get().getConceptRef());
            if (members == null) {
                members = notionMembers(column.get().getConceptRef());
            }
        }
        return concept == null ? Optional.empty() : Optional.of(new Level(concept, members));
    }

    private Optional<Column> column(Entity entity, Attribute attribute) {
        String table = entity.getTable() != null ? entity.getTable() : entity.getName();
        String column = attribute.getColumn() != null ? attribute.getColumn() : attribute.getName();
        return schema.getTable(table).flatMap(t -> t.getColumn(column));
    }

    /**
     * Whether what a model says about the parts fits the schema: every member is an entry of the
     * concept it is said about, no two fields of a struct have the same name in this language, and
     * a notion is the notion of a concept the schema has.
     *
     * @throws IllegalArgumentException for the first thing that does not fit, naming where it is
     */
    public void check() {
        if (model.getNotions() != null) {
            for (Map.Entry<String, Notion> notion : model.getNotions().entrySet()) {
                Concept concept =
                        schema.getConcepts() == null
                                ? null
                                : schema.getConcepts().get(notion.getKey());
                if (concept == null) {
                    throw new IllegalArgumentException(
                            "notion '"
                                    + notion.getKey()
                                    + "': the schema has no concept of that name; it has "
                                    + (schema.getConcepts() == null
                                                    || schema.getConcepts().isEmpty()
                                            ? "none"
                                            : String.join(", ", schema.getConcepts().keySet())));
                }
                check(
                        "notion '" + notion.getKey() + "'",
                        new Level(concept, notion.getValue().getMembers()));
            }
        }
        if (model.getEntities() == null) {
            return;
        }
        for (Entity entity : model.getEntities()) {
            for (Attribute attribute : entity.getAttributes()) {
                if (attribute.getMembers() == null) {
                    continue;
                }
                String where = "attribute '" + entity.getName() + "." + attribute.getName() + "'";
                Optional<Level> level = of(entity, attribute);
                if (level.isEmpty()) {
                    throw new IllegalArgumentException(
                            where
                                    + ": it names members, but its column has no concept to name them in");
                }
                check(where, level.get());
            }
        }
    }

    private void check(String where, Level level) {
        Set<String> seen = new HashSet<>();
        for (String key : level.keys()) {
            String written = level.written(key);
            if (!seen.add(written.toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException(
                        where + ": two fields are called '" + written + "' in this language");
            }
        }
        for (Map.Entry<String, Member> member : level.members.entrySet()) {
            String at = where + ", member '" + member.getKey() + "'";
            if (level.concept.getEntries() == null
                    || !level.concept.getEntries().containsKey(member.getKey())) {
                throw new IllegalArgumentException(
                        at
                                + " is no entry of its concept; it has "
                                + (level.concept.getEntries() == null
                                        ? "none"
                                        : String.join(", ", level.concept.getEntries().keySet())));
            }
            if (member.getValue() == null) {
                continue;
            }
            if (member.getValue().getName() != null && member.getValue().getName().isBlank()) {
                throw new IllegalArgumentException(at + " has a blank name");
            }
            if (member.getValue().getMembers() != null) {
                Optional<Level> below = level.into(member.getKey());
                if (below.isEmpty()) {
                    throw new IllegalArgumentException(
                            at + " names members, but its entry is a type and has no parts");
                }
                check(at, below.get());
            }
        }
    }
}
