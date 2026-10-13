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
package ai.koryki.catalog.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class Model {

    private String name;
    private String label;
    private String comment;
    private String description;
    private List<Entity> entities;
    private List<Link> links;

    /**
     * The concepts of the schema in the language of this model, by the name of the concept; see
     * {@link Notion}.
     */
    private Map<String, Notion> notions = new LinkedHashMap<>();

    public Model() {
        this(null);
    }

    public Model(String name) {
        this(name, null, null);
    }

    public Model(String name, String comment, String description) {
        this(name, comment, description, new ArrayList<>(), new ArrayList<>());
    }

    public Model(
            String name,
            String comment,
            String description,
            List<Entity> entities,
            List<Link> links) {
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

    public Optional<Entity> getEntity(String name) {
        return entities.stream().filter(t -> t.getName().equals(name)).findFirst();
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

    public Map<String, Notion> getNotions() {
        return notions;
    }

    public void setNotions(Map<String, Notion> notions) {
        this.notions = notions;
    }

    public static Model deepCopy(Model Locale) {
        Model copy = new Model();
        copy.setName(Locale.getName());
        copy.setLabel(Locale.getLabel());
        copy.setComment(Locale.getComment());
        copy.setDescription(Locale.getDescription());
        copy.setName(Locale.getName());
        copy.setEntities(
                Locale.getEntities().stream().map(Model::deepCopy).collect(Collectors.toList()));
        copy.setLinks(Locale.getLinks().stream().map(Model::deepCopy).collect(Collectors.toList()));
        if (Locale.getNotions() != null) {
            Map<String, Notion> notions = new LinkedHashMap<>();
            Locale.getNotions().forEach((name, notion) -> notions.put(name, deepCopy(notion)));
            copy.setNotions(notions);
        }
        return copy;
    }

    public static Notion deepCopy(Notion notion) {
        Notion copy = new Notion();
        copy.setLabel(notion.getLabel());
        copy.setComment(notion.getComment());
        copy.setDescription(notion.getDescription());
        if (notion.getMembers() != null) {
            Map<String, Member> members = new LinkedHashMap<>();
            notion.getMembers().forEach((key, member) -> members.put(key, deepCopy(member)));
            copy.setMembers(members);
        }
        return copy;
    }

    public static Member deepCopy(Member member) {
        Member copy = new Member();
        copy.setName(member.getName());
        copy.setLabel(member.getLabel());
        copy.setComment(member.getComment());
        copy.setDescription(member.getDescription());
        return copy;
    }

    public static Entity deepCopy(Entity tabpe) {
        Entity copy = new Entity();

        copy.setName(tabpe.getName());
        copy.setLabel(tabpe.getLabel());
        copy.setComment(tabpe.getComment());
        copy.setDescription(tabpe.getDescription());
        copy.setAttributes(
                tabpe.getAttributes().stream().map(Model::deepCopy).collect(Collectors.toList()));
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

    public List<Link> getLinks() {
        return links;
    }

    public void setLinks(List<Link> links) {
        this.links = links;
    }
}
