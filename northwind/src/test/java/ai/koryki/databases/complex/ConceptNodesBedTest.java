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
package ai.koryki.databases.complex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.ConceptConverter;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.databases.complex.duckdb.ComplexService;
import ai.koryki.iql.query.Selector;
import ai.koryki.iql.typing.ConceptNodes;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * What the concepts of the complex catalog know about the parts of its nested columns, found by a
 * selector chain.
 */
class ConceptNodesBedTest {

    private static final Schema SCHEMA =
            ConceptConverter.convert(ComplexService.resolver().getSchema());

    private static final ConceptNodes NODES = new ConceptNodes(SCHEMA);

    private static Column column(String name) {
        return SCHEMA.getTable("check_complex").orElseThrow().getColumns().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

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
        return NODES.node(column(column), List.of(steps));
    }

    @Test
    void aFieldOfAStructCarriesItsLabelAndUnit() {
        Concept w = node("dims", id("w")).orElseThrow();

        assertEquals("Width", w.getLabel());
        assertEquals("m", w.getUnit());
        assertEquals("length", w.getQuantity());
    }

    @Test
    void aChainWalksThroughStructsAndLists() {
        assertEquals("Latitude", node("address", id("geo"), id("lat")).orElseThrow().getLabel());
        assertEquals("Phone number", node("address", id("phones"), at(2)).orElseThrow().getLabel());

        Concept price = node("items", at(1), id("price")).orElseThrow();
        assertEquals("EUR", price.getUnit());
        assertEquals("money", price.getQuantity());
        assertEquals("DECIMALS:2", price.getPresentation());
    }

    @Test
    void aPositionNamesAFieldOfAStructAndCountsFromTheEnd() {
        assertEquals("Width", node("dims", at(1)).orElseThrow().getLabel());
        assertEquals("Height", node("dims", at(-1)).orElseThrow().getLabel());
        assertTrue(node("dims", at(3)).isEmpty());
    }

    @Test
    void aStructFieldIsFoundWithoutRegardToCase() {
        assertEquals("Width", node("dims", id("W")).orElseThrow().getLabel());
        assertEquals("Odd name", node("odd", id("Odd Name")).orElseThrow().getLabel());
    }

    @Test
    void aMapIsEnteredByAnyKeyAndGivesItsValue() {
        assertEquals("Count", node("counts", id("anything")).orElseThrow().getLabel());
    }

    @Test
    void aJsonMemberIsFoundWhereTheCatalogListsIt() {
        assertEquals("Number", node("doc", id("n")).orElseThrow().getLabel());
        assertEquals("Amount", node("doc", id("d")).orElseThrow().getLabel());
        assertTrue(node("doc", id("unlisted")).isEmpty());
    }

    @Test
    void noStepsIsTheConceptOfTheColumnItself() {
        assertEquals("Dimensions", node("dims").orElseThrow().getLabel());
    }

    @Test
    void aRangeAndAStepIntoAScalarHaveNoNode() {
        Selector range = new Selector();
        range.setFrom(1);
        range.setTo(2);

        assertTrue(node("tags", range).isEmpty());
        assertTrue(node("nr", at(1)).isEmpty());
        assertNull(column("nr").getConcept());
    }
}
