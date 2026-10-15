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
import ai.koryki.catalog.schema.Entry;
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

    private static Concept leaf(String family, String label) {
        Concept leaf = new Concept();
        leaf.setTypeFamily(family);
        leaf.setLabel(label);
        return leaf;
    }

    private static Concept struct(String label, Object... keyAndEntry) {
        Concept struct = leaf("STRUCT", label);
        java.util.Map<String, Entry> entries = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyAndEntry.length; i += 2) {
            entries.put((String) keyAndEntry[i], (Entry) keyAndEntry[i + 1]);
        }
        struct.setEntries(entries);
        return struct;
    }

    /**
     * The wording of the parts of a nested column is the wording of the members of the draft model,
     * so a model of another language has something to say again in its own words: the label of a
     * part is in the schema only in the language the schema was written in.
     */
    @Test
    void theWordingOfThePartsOfAColumnIsTheWordingOfItsMembers() {
        Concept lat = leaf("FLOAT", "Latitude");
        lat.setDescription("Degrees north");
        Column address = new Column("address");
        address.setConcept(
                struct(
                        "Address",
                        "street",
                        Entry.of(leaf("TEXT", "Street")),
                        "geo",
                        Entry.of(struct("Position", "lat", Entry.of(lat))),
                        "kind",
                        Entry.of("VARCHAR")));

        Attribute attribute = attribute(schema(address), "address");

        assertEquals(List.of("street", "geo"), List.copyOf(attribute.getMembers().keySet()));
        assertEquals("Street", attribute.getMembers().get("street").getLabel());
        assertNull(attribute.getMembers().get("street").getName());
        assertEquals("Position", attribute.getMembers().get("geo").getLabel());
        assertEquals(
                "Degrees north",
                attribute.getMembers().get("geo").getMembers().get("lat").getDescription());
    }

    @Test
    void aSharedConceptIsANotionAndItsColumnsLeaveTheMembersToIt() {
        Concept point = struct("Position", "lat", Entry.of(leaf("FLOAT", "Latitude")));
        point.setComment("a point on earth");
        Column pos = new Column("pos");
        pos.setConceptRef("point");
        Schema schema = schema(pos);
        schema.setConcepts(new java.util.LinkedHashMap<>(java.util.Map.of("point", point)));

        Model model = SchemaToModel.convert(schema);

        assertEquals("Position", model.getNotions().get("point").getLabel());
        assertEquals("a point on earth", model.getNotions().get("point").getComment());
        assertEquals(
                "Latitude", model.getNotions().get("point").getMembers().get("lat").getLabel());
        assertNull(attribute(schema, "pos").getMembers());
        new MemberNames(schema, model).check();
    }
}
