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
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Schema {

    private String name;
    private String label;
    private String comment;
    private String description;
    private List<Table> tables;
    private List<Relation> relations;

    public Schema() {
        this(null);
    }

    public Schema(String name)  {
        this(name, null, null);
    }

    public Schema(String name, String comment, String description) {
        this(name, comment, description, new ArrayList<>(), new ArrayList<>());
    }

    public Schema(String name, String comment, String description, List<Table> tables, List<Relation> relations)  {
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
        Predicate<Relation> predicate = (r) -> r.getStartTable().equals(startTable) && r.getEndTable().equals(endTable) && (foreignKey == null || r.getName().equals(foreignKey));

        return getRelations().stream().filter(predicate)
                .collect(Collectors.toList());
    }

    public static Schema deepCopy(Schema schema) {
        Schema copy = new Schema();
        copy.setName(schema.getName());
        copy.setLabel(schema.getLabel());
        copy.setComment(schema.getComment());
        copy.setDescription(schema.getDescription());
        copy.setName(schema.getName());

        copy.setTables(schema.getTables().stream().map(t -> deepCopy(t)).collect(Collectors.toList()));
        copy.setRelations(schema.getRelations().stream().map(r -> deepCopy(r)).collect(Collectors.toList()));

        return copy;
    }

    public static Table deepCopy(Table tabpe) {
        Table copy = new Table();

        copy.setName(tabpe.getName());
        copy.setLabel(tabpe.getLabel());
        copy.setComment(tabpe.getComment());
        copy.setDescription(tabpe.getDescription());
        copy.setColumns(tabpe.getColumns().stream().map(c -> deepCopy(c)).collect(Collectors.toList()));

        return copy;
    }

    public static Column deepCopy(Column tabpe) {
        Column copy = new Column();

        copy.setName(tabpe.getName());
        copy.setLabel(tabpe.getLabel());
        copy.setComment(tabpe.getComment());
        copy.setDescription(tabpe.getDescription());

        copy.setGenericType(tabpe.getGenericType());
        copy.setDialectType(tabpe.getDialectType());
        copy.setNullable(tabpe.getNullable());
        copy.setPkPos(tabpe.getPkPos());

        return copy;
    }

    public static Relation deepCopy(Relation relation) {
        Relation copy = new Relation();

        copy.setName(relation.getName());
        copy.setComment(relation.getComment());
        copy.setDescription(relation.getDescription());

        copy.setStartTable(relation.getStartTable());
        copy.setEndTable(relation.getEndTable());
        copy.setSymmetric(relation.isSymmetric());
        copy.setDescription(relation.getDescription());

        copy.setStartColumns(relation.getStartColumns().stream().collect(Collectors.toList()));
        copy.setEndColumns(relation.getEndColumns().stream().collect(Collectors.toList()));

        return copy;
    }

    public List<Relation> linkRelations(String startTable, String endTable) {
        return linkRelations(startTable, endTable, null);
    }

}
