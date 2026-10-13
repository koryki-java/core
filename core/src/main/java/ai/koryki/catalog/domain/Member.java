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

/**
 * How one member of a nested concept is named and described in the language of a model: the part of
 * a struct (or of a declared JSON object) that a query writes after {@code @}, such as {@code
 * t.address@geo@lat}.
 *
 * <p>The key under which a member is held in {@link Notion#getMembers()} is the member's physical
 * name, the entry key of the concept in the schema; {@link #getName()} is what the language calls
 * it. Where a member has no entry, or no name, the physical name is used.
 */
public class Member {

    private String name;
    private String label;
    private String comment;
    private String description;

    public Member() {}

    public Member(String name) {
        this.name = name;
    }

    public Member(String name, String comment, String description) {
        this.name = name;
        this.comment = comment;
        this.description = description;
    }

    /** The name a query of this language writes for the member. */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
}
