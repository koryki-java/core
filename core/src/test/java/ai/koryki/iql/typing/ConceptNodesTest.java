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
package ai.koryki.iql.typing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.ConceptConverter;
import ai.koryki.catalog.Entries;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.databases.complex.duckdb.ComplexService;
import ai.koryki.iql.query.Selector;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** What {@link ConceptNodes} finds in the concepts of the complex bed. */
class ConceptNodesTest {

    private static final Schema NEW = ConceptConverter.convert(CatalogLoader.db(ComplexService.DB));
    private static final ConceptNodes NODES = new ConceptNodes(NEW);

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

    private static Selector range() {
        Selector s = new Selector();
        s.setFrom(1);
        return s;
    }

    @Test
    void aColumnWithoutAConceptHasNoNode() {
        assertEquals(Optional.empty(), NODES.node(new Column("nothing"), List.of()));
    }

    @Test
    void aScalarColumnHasItsConceptAndNothingBelowIt() {
        Column nr = NEW.getTables().get(0).getColumns().get(0);

        assertEquals("Nr", NODES.node(nr, List.of()).orElseThrow().getLabel());
        assertEquals(Optional.empty(), NODES.node(nr, List.of(at(1))));
    }

    @Test
    void aFieldOfAStructCarriesItsLabelAndUnit() {
        Column dims =
                NEW.getTables().get(0).getColumns().stream()
                        .filter(c -> c.getName().equals("dims"))
                        .findFirst()
                        .orElseThrow();

        Concept w = NODES.node(dims, List.of(id("w"))).orElseThrow();

        assertEquals("Width", w.getLabel());
        assertEquals("m", w.getUnit());
        assertEquals("length", w.getQuantity());
    }

    private static Column column(String name) {
        return NEW.getTables().get(0).getColumns().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void anEntryThatIsASpellingSaysItsDialectTypeAndFamily() {
        // the elements of the rows of matrix are the plain spelling DOUBLE
        Concept element = NODES.node(column("matrix"), List.of(at(1), at(1))).orElseThrow();

        assertEquals("FLOAT", element.getTypeFamily());
        assertEquals("DOUBLE", element.getDialectType());
        assertEquals(null, element.getLabel());
    }

    @Test
    void aSpellingOfAContainerSaysItsDialectTypeAndNoFamily() {
        Concept spelled =
                new ConceptNodes(NEW)
                        .fields(inline("STRUCT", java.util.Map.of("v", "VARCHAR[]")))
                        .get(0);

        assertEquals("VARCHAR[]", spelled.getDialectType());
        assertEquals(null, spelled.getTypeFamily());
    }

    private static Concept inline(String family, java.util.Map<String, String> entries) {
        Concept concept = new Concept();
        concept.setTypeFamily(family);
        concept.setEntries(Entries.of(entries));
        return concept;
    }

    @Test
    void aChainMayStartAtAConcept() {
        Concept address = column("address").getConcept();

        Concept lat = NODES.node(address, List.of(id("geo"), id("lat"))).orElseThrow();

        assertEquals("Latitude", lat.getLabel());
        assertEquals(Optional.of(address), NODES.node(address, List.of()));
    }

    @Test
    void aColumnThatRefersToAConceptIsWalkedLikeOneWithItsOwn() {
        Schema referenced = ConceptConverter.convert(CatalogLoader.db(ComplexService.DB));
        referenced.setConcepts(new java.util.LinkedHashMap<>());
        referenced.getConcepts().put("dims", column("dims").getConcept());
        Column refers = new Column("refers");
        refers.setConceptRef("dims");

        Concept w = new ConceptNodes(referenced).node(refers, List.of(id("w"))).orElseThrow();

        assertEquals("Width", w.getLabel());
        assertEquals("m", w.getUnit());
    }
}
