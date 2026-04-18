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
import java.util.Optional;

public class Table {

    private String name;
    private String label;
    private String comment;
    private String description;
    private List<Column> columns;

    public Table()  {
        this(null, null, null, new ArrayList<>());
    }

    public Table(String name)  {
        this(name, null, null, new ArrayList<>());
    }

    public Table(String name, String comment, String description)  {
        this(name, comment, description, new ArrayList<>());
    }

    public Table(String name, String comment, String description, List<Column> columns)  {
        this.name = name;
        this.comment = comment;
        this.description = description;
        this.columns = new ArrayList<>(columns);
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

    public List<Column> getColumns() {
        return columns;
    }

    public void setColumns(List<Column> columns) {
        this.columns = new ArrayList<>(columns);
    }

    public void addColumn(Column column) {
        this.columns.add(column);
    }

    public Optional<Column> getColumn(String name) {
        return columns.stream().filter(c -> c.getName().equals(name)).findFirst();
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }


}
