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
package ai.koryki.postgresql.complex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.iql.query.Selector;
import ai.koryki.iql.typing.ConceptNodes;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The parts of the JSON columns of the PostgreSQL bed, where maps and lists are JSON: how a chain
 * enters a container that is stored as JSON.
 */
class ConceptNodesTest {

    private static final Schema SCHEMA = CatalogLoader.db(ComplexCatalogTest.DB);

    private static final ConceptNodes NODES = new ConceptNodes(SCHEMA);

    private static Selector id(String name) {
        Selector s = new Selector();
        s.setId(name);
        return s;
    }

    private static Selector at(int position) {
        Selector s = new Selector();
        s.setIndex(position);
        return s;
    }

    private static Optional<Concept> node(String column, Selector... steps) {
        Column c =
                SCHEMA.getTables().get(0).getColumns().stream()
                        .filter(x -> x.getName().equals(column))
                        .findFirst()
                        .orElseThrow();
        return NODES.node(c, List.of(steps));
    }

    @Test
    void aJsonObjectIsEnteredByMemberNameNotByPosition() {
        assertEquals("Number", node("doc", id("n")).orElseThrow().getLabel());
        assertEquals("Amount", node("doc", id("D")).orElseThrow().getLabel());
        assertTrue(node("doc", id("unlisted")).isEmpty());
        assertTrue(node("doc", at(1)).isEmpty());
    }

    @Test
    void aJsonListIsEnteredByPositionNotByName() {
        assertTrue(node("deep", at(1)).isPresent());
        assertTrue(node("deep", at(-1)).isPresent());
        assertTrue(node("deep", id("anything")).isEmpty());
    }

    @Test
    void aJsonMapWithTextKeysIsEnteredByKeyOnly() {
        assertEquals("Attribute value", node("attrs", id("color")).orElseThrow().getLabel());
        assertTrue(node("attrs", at(1)).isEmpty());
    }

    @Test
    void aJsonMapWithIntegerKeysIsEnteredByPositionToo() {
        assertEquals("Name", node("lookup", at(1)).orElseThrow().getLabel());
        assertEquals("Name", node("lookup", id("1")).orElseThrow().getLabel());
    }

    @Test
    void theValueOfAJsonMapCarriesItsUnit() {
        Concept count = node("counts", id("anything")).orElseThrow();

        assertEquals("Count", count.getLabel());
        assertEquals("1", count.getUnit());
        assertEquals("count", count.getQuantity());
    }

    @Test
    void thePartsOfAJsonListAreFoundBelowIt() {
        // deep is a list of maps whose values are structs: a, b
        assertTrue(node("deep", at(1), id("k"), id("a")).isPresent());
        assertTrue(node("deep", at(1), id("k"), id("a"), at(1)).isPresent());
        assertTrue(node("deep", at(1), id("k"), id("nothing")).isEmpty());
    }
}
