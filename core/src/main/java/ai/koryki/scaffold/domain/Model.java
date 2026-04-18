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
package ai.koryki.scaffold.domain;

import ai.koryki.scaffold.schema.Column;
import ai.koryki.scaffold.schema.Relation;
import ai.koryki.scaffold.schema.Schema;
import ai.koryki.scaffold.schema.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Model {

    private String name;
    private String label;
    private String table;
    private String comment;
    private String description;
    private List<Entity> entities;
    private List<Link> links;

    public Model() {
        this(null);
    }

    public Model(String name)  {
        this(name, null, null);
    }

    public Model(String name, String comment, String description) {
        this(name, comment, description, new ArrayList<>(), new ArrayList<>());
    }

    public Model(String name, String comment, String description, List<Entity> entities, List<Link> links)  {
        this.name = name;
        this.comment = comment;
        this.description = description;
        this.entities = new ArrayList<>(entities);
        this.links = new ArrayList<>(links);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Entity> getEntities() {
        return entities;
    }

    public void setEntities(List<Entity> entities) {
        this.entities = new ArrayList<>(entities);
    }

    public void addEntity(Entity table) {
        this.entities.add(table);
    }

    public Optional<Entity> getEntity(String name) {
        return entities.stream().filter(t -> t.getName().equals(name)).findFirst();
    }

    public String getTable(String entity) {
        Optional<Entity> entity1 = getEntity(entity);
        if (entity1.isEmpty()) {
            // FIXME rethink concept for headers, model-translation
            return entity;
        }
        Entity e = entity1.get();
        return e.getTable() != null ? e.getTable() : e.getName();
    }

    public String getColumn(String entity, String attribute) {
        Optional<Entity> optional = getEntity(entity);

        if (optional.isEmpty()) {
            return attribute;
        }
        Entity e = optional.get();

        Optional<Attribute> first = e.getAttributes().stream().filter(a -> a.getName().equals(attribute)).findFirst();

        if (!first.isPresent()) {
            // FIXME rethink concept for headers, model-translation
            return attribute;
        }

        Attribute attr = first.get();
        return attr.getColumn() != null ? attr.getColumn() : attr.getName();
    }

    public Optional<Link> getLink(String name) {
        return links.stream().filter(t -> t.getName().equals(name)).findFirst();
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public static Model deepCopy(Model Locale) {
        Model copy = new Model();
        copy.setName(Locale.getName());
        copy.setLabel(Locale.getLabel());
        copy.setComment(Locale.getComment());
        copy.setDescription(Locale.getDescription());
        copy.setName(Locale.getName());
        copy.setEntities(Locale.getEntities().stream().map(t -> deepCopy(t)).collect(Collectors.toList()));
        copy.setLinks(Locale.getLinks().stream().map(r -> deepCopy(r)).collect(Collectors.toList()));
        return copy;
    }

    public static Entity deepCopy(Entity tabpe) {
        Entity copy = new Entity();

        copy.setName(tabpe.getName());
        copy.setLabel(tabpe.getLabel());
        copy.setComment(tabpe.getComment());
        copy.setDescription(tabpe.getDescription());
        copy.setAttributes(tabpe.getAttributes().stream().map(c -> deepCopy(c)).collect(Collectors.toList()));
        return copy;
    }

    public static Attribute deepCopy(Attribute tabpe) {
        Attribute copy = new Attribute();

        copy.setName(tabpe.getName());
        copy.setLabel(tabpe.getLabel());
        copy.setComment(tabpe.getComment());
        copy.setDescription(tabpe.getDescription());
        return copy;
    }

    public static Link deepCopy(Link relation) {
        Link copy = new Link();

        copy.setName(relation.getName());
        copy.setComment(relation.getComment());
        copy.setDescription(relation.getDescription());
        return copy;
    }


    public static Model deepCopy(Schema schema) {
        Model copy = new Model();
        copy.setName(schema.getName());
        copy.setLabel(schema.getLabel());
        copy.setComment(schema.getComment());
        copy.setDescription(schema.getDescription());
        copy.setName(schema.getName());

        copy.setEntities(schema.getTables().stream().map(t -> deepCopy(t)).collect(Collectors.toList()));
        copy.setLinks(schema.getRelations().stream().map(r -> deepCopy(r)).collect(Collectors.toList()));

        return copy;
    }

    public static Entity deepCopy(Table tabpe) {
        Entity copy = new Entity();

        copy.setName(tabpe.getName());
        copy.setLabel(tabpe.getLabel());
        copy.setComment(tabpe.getComment());
        copy.setDescription(tabpe.getDescription());
        copy.setAttributes(tabpe.getColumns().stream().map(c -> deepCopy(c)).collect(Collectors.toList()));

        return copy;
    }

    public static Attribute deepCopy(Column tabpe) {
        Attribute copy = new Attribute();

        copy.setName(tabpe.getName());
        copy.setLabel(tabpe.getLabel());
        copy.setComment(tabpe.getComment());
        copy.setDescription(tabpe.getDescription());

        return copy;
    }

    public static Link deepCopy(Relation relation) {
        Link copy = new Link();

        copy.setName(relation.getName());
        copy.setComment(relation.getComment());
        copy.setDescription(relation.getDescription());

        return copy;
    }


    public List<Link> getLinks() {
        return links;
    }

    public void setLinks(List<Link> links) {
        this.links = links;
    }


}
