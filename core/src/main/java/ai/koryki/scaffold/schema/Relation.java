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
package ai.koryki.scaffold.schema;

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

    public boolean isSymmetric() {
        return symmetric;
    }

    public void setSymmetric(boolean symmetric) {
        this.symmetric = symmetric;
    }
}
