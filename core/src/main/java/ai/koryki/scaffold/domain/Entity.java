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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Entity {

    private String name;
    private String label;
    private String comment;
    private String description;
    private String table;
    private List<Attribute> attributes;

    public Entity()  {
        this(null, null, null, new ArrayList<>());
    }

    public Entity(String name)  {
        this(name, null, null, new ArrayList<>());
    }

    public Entity(String name, String comment, String description)  {
        this(name, comment, description, new ArrayList<>());
    }

    public Entity(String name, String comment, String description, List<Attribute> properties)  {
        this.name = name;
        this.comment = comment;
        this.description = description;
        this.attributes = new ArrayList<>(properties);
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

    public List<Attribute> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<Attribute> attributes) {
        this.attributes = new ArrayList<>(attributes);
    }

    public void addColumn(Attribute column) {
        this.attributes.add(column);
    }

    public Optional<Attribute> getColumn(String name) {
        return attributes.stream().filter(c -> c.getName().equals(name)).findFirst();
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getTable() {
        return table;
    }

    public String getDialectTable() {
        return table != null ? table : name;
    }

//    public void setTable(String table) {
//        this.table = table;
//    }
}
