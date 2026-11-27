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
package ai.koryki.catalog.schema;

import java.util.ArrayList;
import java.util.List;

public class Relation {

    private String name;
    private String comment;
    private String description;
    private String startTable;
    private String endTable;
    private boolean symmetric;

    private List<String> startColumns;
    private List<String> endColumns;

    /**
     * What a join over this relation compares instead of equal columns, as text: {@code start.sal
     * BETWEEN end.losal AND end.hisal}. See {@link ai.koryki.catalog.RelationCondition} for the
     * form. {@code null} for an ordinary relation.
     *
     * <p>A relation with a condition has <em>no</em> start and end columns: the condition carries
     * every comparison, equalities included ({@code start.a = end.b}). That is deliberate. A reader
     * that does not know this property ignores it, and a relation without columns renders as a join
     * without a condition, which the database refuses -- the failure is loud. Equality columns next
     * to a condition would instead be rendered alone and give wrong rows without a sign.
     */
    private String condition;

    public Relation() {
        this(null);
    }

    public Relation(String name) {
        this.name = name;
        this.startColumns = new ArrayList<>();
        this.endColumns = new ArrayList<>();
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

    public String getStartTable() {
        return startTable;
    }

    public void setStartTable(String startTable) {
        this.startTable = startTable;
    }

    public String getEndTable() {
        return endTable;
    }

    public void setEndTable(String endTable) {
        this.endTable = endTable;
    }

    public List<String> getStartColumns() {
        return startColumns;
    }

    public void setStartColumns(List<String> startColumns) {
        this.startColumns = new ArrayList<>(startColumns);
    }

    public List<String> getEndColumns() {
        return endColumns;
    }

    public void setEndColumns(List<String> endColumns) {
        this.endColumns = new ArrayList<>(endColumns);
    }

    public void addStartColumn(String startColumn) {
        this.startColumns.add(startColumn);
    }

    public void addEndColumn(String endColumn) {
        this.endColumns.add(endColumn);
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public boolean isSymmetric() {
        return symmetric;
    }

    public void setSymmetric(boolean symmetric) {
        this.symmetric = symmetric;
    }
}
