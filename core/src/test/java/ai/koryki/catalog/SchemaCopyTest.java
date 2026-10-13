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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.catalog.schema.Table;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A copy of a schema is all of it: the concepts and the reference of a column to one as well. */
class SchemaCopyTest {

    private static Schema schema() {
        Concept address = new Concept();
        address.setLabel("Address");
        address.setComment("where");
        address.setDescription("A postal address");
        address.setTypeFamily("STRUCT");
        address.setTypeEncoding("NATIVE");
        address.setDialectType("STRUCT(street VARCHAR, geo VARCHAR)");
        address.setUnit("m");
        address.setQuantity("length");
        address.setPresentation("DECIMALS:2");
        address.setArraySize(3);
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("street", "VARCHAR");
        entries.put("geo", "position");
        entries.put("alpha", "VARCHAR");
        address.setEntries(entries);

        Map<String, Concept> concepts = new LinkedHashMap<>();
        concepts.put("address", address);
        concepts.put("zero", new Concept());

        Column billing = new Column("billing");
        billing.setConceptRef("address");
        Table table = new Table("orders");
        table.setColumns(new ArrayList<>(List.of(billing)));

        Schema schema = new Schema("s");
        schema.setTables(new ArrayList<>(List.of(table)));
        schema.setRelations(new ArrayList<>());
        schema.setConcepts(concepts);
        return schema;
    }

    @Test
    void aColumnKeepsItsConcept() {
        Schema copy = SchemaCopy.deepCopy(schema());

        assertEquals("address", copy.getTables().get(0).getColumns().get(0).getConceptRef());
    }

    @Test
    void theConceptsAreCopiedWithEveryField() {
        Concept copy = SchemaCopy.deepCopy(schema()).getConcepts().get("address");

        assertEquals("Address", copy.getLabel());
        assertEquals("where", copy.getComment());
        assertEquals("A postal address", copy.getDescription());
        assertEquals("STRUCT", copy.getTypeFamily());
        assertEquals("NATIVE", copy.getTypeEncoding());
        assertEquals("STRUCT(street VARCHAR, geo VARCHAR)", copy.getDialectType());
        assertEquals("m", copy.getUnit());
        assertEquals("length", copy.getQuantity());
        assertEquals("DECIMALS:2", copy.getPresentation());
        assertEquals(3, copy.getArraySize());
        assertEquals("position", copy.getEntries().get("geo"));
    }

    @Test
    void theOrderOfTheConceptsAndOfTheEntriesIsKept() {
        Schema copy = SchemaCopy.deepCopy(schema());

        assertEquals(List.of("address", "zero"), List.copyOf(copy.getConcepts().keySet()));
        assertEquals(
                List.of("street", "geo", "alpha"),
                List.copyOf(copy.getConcepts().get("address").getEntries().keySet()));
    }

    @Test
    void aCopyIsIndependentOfTheOriginal() {
        Schema original = schema();
        Schema copy = SchemaCopy.deepCopy(original);

        assertNotSame(original.getConcepts(), copy.getConcepts());
        assertNotSame(original.getConcepts().get("address"), copy.getConcepts().get("address"));
        copy.getConcepts().get("address").getEntries().put("extra", "VARCHAR");
        copy.getConcepts().get("address").setLabel("changed");

        assertEquals("Address", original.getConcepts().get("address").getLabel());
        assertEquals(3, original.getConcepts().get("address").getEntries().size());
    }

    @Test
    void aSchemaWithoutConceptsHasNoneAfterTheCopy() {
        Schema plain = schema();
        plain.setConcepts(null);

        assertNull(SchemaCopy.deepCopy(plain).getConcepts());
    }

    @Test
    void aConceptWithoutEntriesHasNone() {
        assertNull(SchemaCopy.deepCopy(new Concept()).getEntries());
    }
}
