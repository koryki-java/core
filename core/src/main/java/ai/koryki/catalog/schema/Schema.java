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

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Schema {

    private String name;
    private String label;
    private String comment;
    private String description;
    private String schemaPrefix;
    private List<Table> tables;
    private List<Relation> relations;
    private Map<String, Concept> concepts;

    public Schema() {
        this(null);
    }

    public Schema(String name) {
        this(name, null, null);
    }

    public Schema(String name, String comment, String description) {
        this(name, comment, description, new ArrayList<>(), new ArrayList<>());
    }

    public Schema(
            String name,
            String comment,
            String description,
            List<Table> tables,
            List<Relation> relations) {
        this.name = name;
        this.comment = comment;
        this.description = description;
        this.tables = new ArrayList<>(tables);
        this.relations = new ArrayList<>(relations);
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

    public String getSchemaPrefix() {
        return schemaPrefix;
    }

    public void setSchemaPrefix(String schemaPrefix) {
        this.schemaPrefix = schemaPrefix;
    }

    public List<Table> getTables() {
        return tables;
    }

    public void setTables(List<Table> tables) {
        this.tables = new ArrayList<>(tables);
    }

    public List<Relation> getRelations() {
        return relations;
    }

    public void setRelations(List<Relation> relations) {
        this.relations = new ArrayList<>(relations);
    }

    public void addTable(Table table) {
        this.tables.add(table);
    }

    public void addRelation(Relation relation) {
        this.relations.add(relation);
    }

    public Optional<Table> getTable(String name) {
        return tables.stream().filter(t -> t.getName().equals(name)).findFirst();
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Optional<Relation> getRelation(String name) {
        return relations.stream().filter(r -> r.getName().equals(name)).findFirst();
    }

    public List<Relation> linkRelations(String startTable, String endTable, String foreignKey) {
        Predicate<Relation> predicate =
                (r) ->
                        r.getStartTable().equals(startTable)
                                && r.getEndTable().equals(endTable)
                                && (foreignKey == null || r.getName().equals(foreignKey));

        return getRelations().stream().filter(predicate).collect(Collectors.toList());
    }

    public List<Relation> linkRelations(String startTable, String endTable) {
        return linkRelations(startTable, endTable, null);
    }

    public Map<String, Concept> getConcepts() {
        return concepts;
    }

    public void setConcepts(Map<String, Concept> concepts) {
        this.concepts = concepts;
    }
}
