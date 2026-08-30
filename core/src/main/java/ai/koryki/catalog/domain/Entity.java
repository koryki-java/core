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
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

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

    public Optional<Attribute> getAttribute(String name) {
        return attributes.stream().filter(c -> c.getName().equals(name) | (c.getColumn() != null && c.getColumn().equals(name))).findFirst();
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * The database's own name for this entity's table, when it differs from the model's.
     *
     * <p><b>The exact stored spelling.</b> Not a logical name and not a convenience spelling: this
     * value is what the renderer will put between quotes the moment the name cannot go bare, and a
     * quoted name is matched literally. Nothing here introspects a database, so the renderer has no
     * second source to check it against - if the catalog says {@code Betrag} and the entity's table was
     * created unquoted on Oracle, it is stored {@code BETRAG} and the query finds nothing.
     *
     * <p>The distinction is invisible for the ordinary case and only bites at the edges. An
     * all-lowercase name goes unquoted, and every engine either folds an unquoted name or ignores
     * case, so it resolves whatever its stored spelling. A name carrying a space or a special
     * character could never have been created without quotes, so it is stored exactly as typed. It
     * is the merely mixed-case name that is ambiguous, and this contract is what resolves it.
     *
     * <p>Must be unqualified. A schema-qualified value such as {@code public.orders} is rendered as
     * one identifier containing a dot, which resolves to nothing; qualify through the connection
     * instead.
     *
     * @see ai.koryki.iql.SqlDialect#renderIdentifier
     */
    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }
}
