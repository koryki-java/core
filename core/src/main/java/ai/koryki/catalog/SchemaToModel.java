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
import ai.koryki.catalog.domain.Link;
import ai.koryki.catalog.domain.Member;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.domain.Notion;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Relation;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Derives a default semantic {@link Model} from a physical {@link Schema} when no explicit model is
 * supplied: Table → Entity, Column → Attribute, Relation → Link, and a concept of the schema →
 * Notion (name/label/comment/description carried over, and the wording of the parts of a nested
 * column as members).
 *
 * <p>Lives at the catalog root as the sole bridge between the two layers, so {@code catalog.domain}
 * (semantic) and {@code catalog.schema} (physical) stay independent DTO packages that don't
 * reference each other.
 */
public final class SchemaToModel {

    private SchemaToModel() {}

    public static Model convert(Schema schema) {
        Model model = new Model();
        model.setName(schema.getName());
        model.setLabel(schema.getLabel());
        model.setComment(schema.getComment());
        model.setDescription(schema.getDescription());
        model.setEntities(
                schema.getTables().stream()
                        .map(table -> convert(table, schema))
                        .collect(Collectors.toList()));
        if (schema.getConcepts() != null && !schema.getConcepts().isEmpty()) {
            Map<String, Notion> notions = new LinkedHashMap<>();
            schema.getConcepts().forEach((name, concept) -> notions.put(name, notion(concept)));
            model.setNotions(notions);
        }
        model.setLinks(
                schema.getRelations().stream()
                        .map(SchemaToModel::convert)
                        .collect(Collectors.toList()));
        return model;
    }

    private static Entity convert(Table table, Schema schema) {
        Entity entity = new Entity();
        entity.setName(table.getName());
        entity.setLabel(table.getLabel());
        entity.setComment(table.getComment());
        entity.setDescription(table.getDescription());
        entity.setAttributes(
                table.getColumns().stream()
                        .map(column -> convert(column, schema))
                        .collect(Collectors.toList()));
        return entity;
    }

    private static Attribute convert(Column column, Schema schema) {
        Attribute attribute = new Attribute();
        attribute.setName(column.getName());
        Concept concept =
                column.getConcept() != null
                        ? column.getConcept()
                        : column.getConceptRef() == null || schema.getConcepts() == null
                                ? null
                                : schema.getConcepts().get(column.getConceptRef());
        if (concept != null) {
            attribute.setLabel(concept.getLabel());
            attribute.setComment(concept.getComment());
            attribute.setDescription(concept.getDescription());
        }
        // the parts of a concept of the column's own; those of a shared one are in its notion
        attribute.setMembers(members(column.getConcept()));
        return attribute;
    }

    /**
     * What the draft model says of a shared concept: its wording, as the schema has it, and that of
     * its parts. The wording of a concept is in the language of the schema; a model of another
     * language says it again in its own words, here and in the members, and a part the model does
     * not say anything about falls back to the schema's.
     */
    private static Notion notion(Concept concept) {
        Notion notion = new Notion();
        notion.setLabel(concept.getLabel());
        notion.setComment(concept.getComment());
        notion.setDescription(concept.getDescription());
        Map<String, Member> members = members(concept);
        if (members != null) {
            notion.setMembers(members);
        }
        return notion;
    }

    /**
     * The members of a container: the parts that carry wording of their own, and those that contain
     * such parts. A part that is only the name of a concept or a type spelling has none here; the
     * notion of the concept says it. Null where nothing is said.
     */
    private static Map<String, Member> members(Concept concept) {
        if (concept == null || concept.getEntries() == null) {
            return null;
        }
        Map<String, Member> members = new LinkedHashMap<>();
        concept.getEntries()
                .forEach(
                        (key, entry) -> {
                            if (!entry.isInline()) {
                                return;
                            }
                            Concept part = entry.getConcept();
                            Member member = new Member();
                            member.setLabel(part.getLabel());
                            member.setComment(part.getComment());
                            member.setDescription(part.getDescription());
                            member.setMembers(members(part));
                            if (member.getLabel() != null
                                    || member.getComment() != null
                                    || member.getDescription() != null
                                    || member.getMembers() != null) {
                                members.put(key, member);
                            }
                        });
        return members.isEmpty() ? null : members;
    }

    private static Link convert(Relation relation) {
        Link link = new Link();
        link.setName(relation.getName());
        link.setComment(relation.getComment());
        link.setDescription(relation.getDescription());
        return link;
    }
}
