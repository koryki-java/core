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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import ai.koryki.catalog.domain.Attribute;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The wording of a column, as the attribute of the draft model says it. */
class SchemaToModelTest {

    private static Schema schema(Column... columns) {
        Table table = new Table("t");
        table.setColumns(new ArrayList<>(List.of(columns)));
        Schema schema = new Schema("s");
        schema.addTable(table);
        return schema;
    }

    private static Attribute attribute(Schema schema, String name) {
        Model model = SchemaToModel.convert(schema);
        return model.getEntities().get(0).getAttributes().stream()
                .filter(a -> a.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void theWordingOfAConceptOfItsOwnIsTheWordingOfTheAttribute() {
        Concept concept = new Concept();
        concept.setLabel("Price");
        concept.setComment("net");
        concept.setDescription("What it costs");
        Column price = new Column("price");
        price.setConcept(concept);

        Attribute attribute = attribute(schema(price), "price");

        assertEquals("Price", attribute.getLabel());
        assertEquals("net", attribute.getComment());
        assertEquals("What it costs", attribute.getDescription());
    }

    @Test
    void theWordingOfAConceptTheColumnRefersToIsTheSame() {
        Concept concept = new Concept();
        concept.setLabel("Price");
        concept.setComment("net");
        concept.setDescription("What it costs");
        Column price = new Column("price");
        price.setConceptRef("money");
        Schema schema = schema(price);
        schema.setConcepts(new java.util.LinkedHashMap<>(java.util.Map.of("money", concept)));

        Attribute attribute = attribute(schema, "price");

        assertEquals("Price", attribute.getLabel());
        assertEquals("net", attribute.getComment());
        assertEquals("What it costs", attribute.getDescription());
    }

    @Test
    void aColumnThatSaysNothingGivesAnAttributeThatSaysNothing() {
        Attribute attribute = attribute(schema(new Column("x")), "x");

        assertNull(attribute.getLabel());
        assertNull(attribute.getComment());
        assertNull(attribute.getDescription());
    }
}
