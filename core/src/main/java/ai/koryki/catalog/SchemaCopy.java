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
package ai.koryki.catalog;

import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Relation;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Copies of the schema classes. They live here and not on the classes, which are plain data: a copy
 * is something done to them.
 */
public final class SchemaCopy {

    private SchemaCopy() {}

    public static Schema deepCopy(Schema schema) {
        Schema copy = new Schema();
        copy.setName(schema.getName());
        copy.setLabel(schema.getLabel());
        copy.setComment(schema.getComment());
        copy.setDescription(schema.getDescription());
        copy.setSchemaPrefix(schema.getSchemaPrefix());

        copy.setTables(
                schema.getTables().stream().map(t -> deepCopy(t)).collect(Collectors.toList()));
        copy.setRelations(
                schema.getRelations().stream().map(r -> deepCopy(r)).collect(Collectors.toList()));
        if (schema.getConcepts() != null) {
            Map<String, Concept> concepts = new LinkedHashMap<>();
            schema.getConcepts().forEach((name, concept) -> concepts.put(name, deepCopy(concept)));
            copy.setConcepts(concepts);
        }

        return copy;
    }

    public static Table deepCopy(Table tabpe) {
        Table copy = new Table();

        copy.setName(tabpe.getName());
        copy.setLabel(tabpe.getLabel());
        copy.setComment(tabpe.getComment());
        copy.setDescription(tabpe.getDescription());
        copy.setColumns(
                tabpe.getColumns().stream().map(c -> deepCopy(c)).collect(Collectors.toList()));

        return copy;
    }

    public static Column deepCopy(Column tabpe) {
        Column copy = new Column();

        copy.setName(tabpe.getName());
        copy.setLabel(tabpe.getLabel());
        copy.setComment(tabpe.getComment());
        copy.setDescription(tabpe.getDescription());

        copy.setTypeFamily(tabpe.getTypeFamily());
        copy.setTypeEncoding(tabpe.getTypeEncoding());
        copy.setDialectType(tabpe.getDialectType());
        copy.setNullable(tabpe.getNullable());
        copy.setPkPos(tabpe.getPkPos());
        copy.setNamePos(tabpe.getNamePos());
        copy.setConceptRef(tabpe.getConceptRef());
        copy.setLength(tabpe.getLength());
        if (tabpe.getFields() != null) {
            copy.setFields(
                    tabpe.getFields().stream().map(c -> deepCopy(c)).collect(Collectors.toList()));
        }
        copy.setElement(tabpe.getElement() == null ? null : deepCopy(tabpe.getElement()));
        copy.setKey(tabpe.getKey() == null ? null : deepCopy(tabpe.getKey()));
        copy.setValue(tabpe.getValue() == null ? null : deepCopy(tabpe.getValue()));

        // Carried too, and not before: a copy that quietly drops the encoding, the unit or the
        // presentation is not a copy, and the omission only shows up far away as a column that
        // formats differently or compares against the wrong encoding.
        copy.setUnit(tabpe.getUnit());
        copy.setQuantity(tabpe.getQuantity());
        copy.setPresentation(tabpe.getPresentation());

        return copy;
    }

    public static Concept deepCopy(Concept concept) {
        Concept copy = new Concept();

        copy.setLabel(concept.getLabel());
        copy.setComment(concept.getComment());
        copy.setDescription(concept.getDescription());

        copy.setTypeFamily(concept.getTypeFamily());
        copy.setTypeEncoding(concept.getTypeEncoding());
        copy.setDialectType(concept.getDialectType());
        copy.setUnit(concept.getUnit());
        copy.setQuantity(concept.getQuantity());
        copy.setPresentation(concept.getPresentation());
        copy.setArraySize(concept.getArraySize());
        // an ordered copy: the order of the entries is the order of the members
        copy.setEntries(
                concept.getEntries() == null ? null : new LinkedHashMap<>(concept.getEntries()));

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
}
