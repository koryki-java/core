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
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Where a model has not said in its own words what the schema says.
 *
 * <p>The wording of a concept (label, comment, description) is in the language of the schema, and a
 * model of another language says it again, for the column, for each part of a nested column and for
 * a shared concept. Whatever it leaves out is read from the schema, so nothing breaks, but a German
 * heading that reads "Street" is a sign that nobody said it. This lists those places.
 *
 * <p>Two kinds of gap. {@link Kind#MISSING}: the schema has wording and the model has none. {@link
 * Kind#UNCHANGED}: the model has the very words of the schema, which is how a draft model starts
 * ({@link SchemaToModel}) and what a translation must replace; for a model in the language of the
 * schema that is not a gap, and the caller leaves it out.
 */
public final class WordingAudit {

    private WordingAudit() {}

    public enum Kind {
        MISSING,
        UNCHANGED
    }

    /**
     * @param where the column, part or concept: {@code ort.address/geo/lat}, {@code concept:point}
     * @param field {@code label}, {@code comment} or {@code description}
     */
    public record Gap(String where, String field, Kind kind) {}

    public static List<Gap> audit(Schema schema, Model model) {
        List<Gap> gaps = new ArrayList<>();
        for (Table table : schema.getTables()) {
            // the entity of a table is the one that says so, whatever the language calls it
            Entity entity =
                    model.getEntities().stream()
                            .filter(
                                    e ->
                                            table.getName()
                                                    .equals(
                                                            e.getTable() != null
                                                                    ? e.getTable()
                                                                    : e.getName()))
                            .findFirst()
                            .orElse(null);
            for (Column column : table.getColumns()) {
                Concept concept = column.getConcept();
                if (concept == null || entity == null) {
                    continue;
                }
                Attribute attribute =
                        entity.getAttributes().stream()
                                .filter(
                                        a ->
                                                column.getName()
                                                        .equals(
                                                                a.getColumn() != null
                                                                        ? a.getColumn()
                                                                        : a.getName()))
                                .findFirst()
                                .orElse(null);
                if (attribute == null) {
                    continue;
                }
                String where = table.getName() + "." + column.getName();
                compare(
                        gaps,
                        where,
                        concept,
                        attribute.getLabel(),
                        attribute.getComment(),
                        attribute.getDescription());
                walk(gaps, where, concept, attribute.getMembers());
            }
        }
        if (schema.getConcepts() != null) {
            for (Map.Entry<String, Concept> shared : schema.getConcepts().entrySet()) {
                Notion notion =
                        model.getNotions() == null ? null : model.getNotions().get(shared.getKey());
                String where = "concept:" + shared.getKey();
                compare(
                        gaps,
                        where,
                        shared.getValue(),
                        notion == null ? null : notion.getLabel(),
                        notion == null ? null : notion.getComment(),
                        notion == null ? null : notion.getDescription());
                walk(gaps, where, shared.getValue(), notion == null ? null : notion.getMembers());
            }
        }
        return gaps;
    }

    private static void walk(
            List<Gap> gaps, String where, Concept concept, Map<String, Member> members) {
        if (concept.getEntries() == null) {
            return;
        }
        concept.getEntries()
                .forEach(
                        (key, entry) -> {
                            // a part that only names a concept is worded by the notion of it
                            if (!entry.isInline()) {
                                return;
                            }
                            Member member = members == null ? null : members.get(key);
                            String path = where + "/" + key;
                            compare(
                                    gaps,
                                    path,
                                    entry.getConcept(),
                                    member == null ? null : member.getLabel(),
                                    member == null ? null : member.getComment(),
                                    member == null ? null : member.getDescription());
                            walk(
                                    gaps,
                                    path,
                                    entry.getConcept(),
                                    member == null ? null : member.getMembers());
                        });
    }

    private static void compare(
            List<Gap> gaps,
            String where,
            Concept said,
            String label,
            String comment,
            String description) {
        compare(gaps, where, "label", said.getLabel(), label);
        compare(gaps, where, "comment", said.getComment(), comment);
        compare(gaps, where, "description", said.getDescription(), description);
    }

    private static void compare(
            List<Gap> gaps, String where, String field, String schema, String model) {
        if (schema == null || schema.isBlank()) {
            return;
        }
        if (model == null || model.isBlank()) {
            gaps.add(new Gap(where, field, Kind.MISSING));
        } else if (Objects.equals(schema, model)) {
            gaps.add(new Gap(where, field, Kind.UNCHANGED));
        }
    }
}
