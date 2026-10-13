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
import ai.koryki.catalog.ConceptConverter;
import ai.koryki.catalog.schema.Column;
import ai.koryki.catalog.schema.Concept;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.iql.query.Selector;
import ai.koryki.iql.typing.ConceptNodes;
import ai.koryki.iql.typing.NestedNodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Parity with {@link NestedNodes}: for every chain over the complex bed that either of them can
 * start, the concepts say what the nodes of the column said.
 */
class ConceptNodesTest {

    private static final Schema OLD = CatalogLoader.db(ComplexCatalogTest.DB);
    private static final Schema NEW = ConceptConverter.convert(OLD);
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

    /** Every name the bed gives a field, a few that it does not, positions and a range. */
    private static List<Selector> alphabet() {
        TreeSet<String> names = new TreeSet<>(List.of("nothing", "W", "ELEMENT"));
        OLD.getTables().forEach(t -> t.getColumns().forEach(c -> collect(c, names)));
        List<Selector> all = new ArrayList<>();
        names.forEach(n -> all.add(id(n)));
        for (int position : new int[] {1, 2, 3, -1, -2, 9}) {
            all.add(at(position));
        }
        all.add(range());
        return all;
    }

    private static void collect(Column node, TreeSet<String> names) {
        if (node.getFields() != null) {
            node.getFields()
                    .forEach(
                            f -> {
                                names.add(f.getName());
                                collect(f, names);
                            });
        }
        for (Column child : new Column[] {node.getElement(), node.getKey(), node.getValue()}) {
            if (child != null) {
                collect(child, names);
            }
        }
    }

    private int compared;

    private void same(Column old, Column converted, List<Selector> steps, List<Selector> alphabet) {
        Optional<Column> before = NestedNodes.node(old, steps);
        Optional<Concept> after = NODES.node(converted, steps);
        String where =
                old.getName()
                        + " "
                        + steps.stream().map(s -> s.getId() + "/" + s.getIndex()).toList();

        assertEquals(before.isPresent(), after.isPresent(), where);
        compared++;
        if (before.isEmpty()) {
            return;
        }
        assertEquals(before.get().getLabel(), after.get().getLabel(), where + " label");
        assertEquals(before.get().getUnit(), after.get().getUnit(), where + " unit");
        assertEquals(before.get().getQuantity(), after.get().getQuantity(), where + " quantity");
        assertEquals(
                before.get().getPresentation(),
                after.get().getPresentation(),
                where + " presentation");
        assertEquals(
                before.get().getDescription(),
                after.get().getDescription(),
                where + " description");
        if (steps.size() < 4) {
            for (Selector next : alphabet) {
                List<Selector> longer = new ArrayList<>(steps);
                longer.add(next);
                same(old, converted, longer, alphabet);
            }
        }
    }

    @Test
    void everyChainOverTheBedSaysWhatTheNodesSaid() {
        List<Selector> alphabet = alphabet();
        for (int t = 0; t < OLD.getTables().size(); t++) {
            for (int c = 0; c < OLD.getTables().get(t).getColumns().size(); c++) {
                Column old = OLD.getTables().get(t).getColumns().get(c);
                Column converted = NEW.getTables().get(t).getColumns().get(c);
                if (converted.getConcept() != null) {
                    same(old, converted, List.of(), alphabet);
                }
            }
        }
        assertTrue(compared > 5000, "chains compared: " + compared);
    }

    @Test
    void aColumnWithoutAConceptHasNoNode() {
        Column nr = NEW.getTables().get(0).getColumns().get(0);

        assertEquals(Optional.empty(), NODES.node(nr, List.of()));
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
}
