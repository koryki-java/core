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
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Relation;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;

import java.util.stream.Collectors;

/**
 * Derives a default semantic {@link Model} from a physical {@link Schema} when no explicit model
 * is supplied: Table → Entity, Column → Attribute, Relation → Link (name/label/comment/description
 * carried over).
 *
 * <p>Lives at the catalog root as the sole bridge between the two layers, so
 * {@code catalog.domain} (semantic) and {@code catalog.schema} (physical) stay independent DTO
 * packages that don't reference each other.
 */
public final class SchemaToModel {

    private SchemaToModel() {
    }

    public static Model convert(Schema schema) {
        Model model = new Model();
        model.setName(schema.getName());
        model.setLabel(schema.getLabel());
        model.setComment(schema.getComment());
        model.setDescription(schema.getDescription());
        model.setEntities(schema.getTables().stream().map(SchemaToModel::convert).collect(Collectors.toList()));
        model.setLinks(schema.getRelations().stream().map(SchemaToModel::convert).collect(Collectors.toList()));
        return model;
    }

    private static Entity convert(Table table) {
        Entity entity = new Entity();
        entity.setName(table.getName());
        entity.setLabel(table.getLabel());
        entity.setComment(table.getComment());
        entity.setDescription(table.getDescription());
        entity.setAttributes(table.getColumns().stream().map(SchemaToModel::convert).collect(Collectors.toList()));
        return entity;
    }

    private static Attribute convert(Column column) {
        Attribute attribute = new Attribute();
        attribute.setName(column.getName());
        attribute.setLabel(column.getLabel());
        attribute.setComment(column.getComment());
        attribute.setDescription(column.getDescription());
        return attribute;
    }

    private static Link convert(Relation relation) {
        Link link = new Link();
        link.setName(relation.getName());
        link.setComment(relation.getComment());
        link.setDescription(relation.getDescription());
        return link;
    }
}
