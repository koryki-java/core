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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The view of a schema concept in the language of a model: its label, comment and description, and
 * the names its members are written with in a query.
 *
 * <p>A notion belongs to the concept of the same name in {@code Schema.getConcepts()}. The concept
 * says what a thing is, the same in every language; the notion says what this language calls it and
 * its parts. That is the split already made for tables and columns, where {@link Entity} and {@link
 * Attribute} stand to them as this stands to a concept.
 *
 * <p>The name of the concept itself is an identifier of the catalog and is not translated. A
 * concept of a column's own has no name; what a model says about its parts is in {@link
 * Attribute#getMembers()}, and wins over the notion of a concept the column refers to.
 */
public class Notion {

    private String label;
    private String comment;
    private String description;

    /**
     * The members of the concept by their physical name, the key of the entry in the concept; in
     * the order of the concept. Only members that need a name, a label or a description of their
     * own have an entry.
     */
    private Map<String, Member> members = new LinkedHashMap<>();

    public Notion() {}

    public Notion(String comment, String description) {
        this.comment = comment;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
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

    public Map<String, Member> getMembers() {
        return members;
    }

    public void setMembers(Map<String, Member> members) {
        this.members = members;
    }
}
