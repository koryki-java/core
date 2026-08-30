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

public class Link {
    private String name;
    private String label;
    private String comment;
    private String description;
    private String nature;
    /**
     * Language-stable canonical identifier used to align this link with its counterpart in another
     * locale's model when translating link names. Unlike name/label/comment/description (which are
     * translated per locale), this value is identical across all locale models; null means the
     * link's own {@code name} is the canonical key. Distinct from {@link #relations} (the FK-relation
     * name list).
     */
    private String canonical;
    private List<String> relations = new ArrayList<>();;

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

    public List<String> getRelations() {
        return relations;
    }

    public void setRelations(List<String> relations) {
        this.relations = relations;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getNature() {
        return nature;
    }

    public void setNature(String inverse) {
        this.nature = inverse;
    }


    public String toString() {
        return name;
    }

    public String getCanonical() {
        return canonical;
    }

    public void setCanonical(String canonical) {
        this.canonical = canonical;
    }
}
