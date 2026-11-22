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
package ai.koryki.iql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.antlr.Range;
import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.LinkDefaults;
import ai.koryki.catalog.SchemaToModel;
import ai.koryki.catalog.domain.Link;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.iql.query.Join;
import ai.koryki.iql.query.JoinKind;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * A query that names no link takes the default of the pair, never a relation with a condition, and
 * says which links to choose from when it cannot decide.
 */
class LinkDefaultTest {

    private static final Range R = new Range(1, 1, 1, 1);

    /** a and b with two key relations, and optionally a third with a condition. */
    private static Schema schema(boolean withCondition) {
        String cols = "[ {\"name\": \"id\", \"concept\": {\"typeFamily\": \"INTEGER\", \"dialectType\": \"INTEGER\"}}, {\"name\": \"x\", \"concept\": {\"typeFamily\": \"INTEGER\", \"dialectType\": \"INTEGER\"}},"
                + " {\"name\": \"y\", \"concept\": {\"typeFamily\": \"INTEGER\", \"dialectType\": \"INTEGER\"}} ]";
        return CatalogLoader.readSchemaJson(
                "{ \"name\": \"d\", \"tables\": [ { \"name\": \"a\", \"columns\": " + cols + " },"
                        + " { \"name\": \"b\", \"columns\": " + cols + " } ], \"relations\": ["
                        + rel("fk_x", "x", "id")
                        + ","
                        + rel("fk_y", "y", "id")
                        + (withCondition
                                ? ", { \"name\": \"cond_a_b\", \"startTable\": \"a\", \"endTable\": \"b\","
                                        + " \"condition\": \"start.x < end.x\" }"
                                : "")
                        + " ] }");
    }

    private static String rel(String name, String from, String to) {
        return "{ \"name\": \"" + name + "\", \"startTable\": \"a\", \"endTable\": \"b\","
                + " \"startColumns\": [\"" + from + "\"], \"endColumns\": [\"" + to + "\"] }";
    }

    private static Link link(String name, String... relations) {
        Link l = new Link();
        l.setName(name);
        l.setRelations(List.of(relations));
        return l;
    }

    private static Model model(Schema schema, Link... links) {
        Model m = SchemaToModel.convert(schema);
        m.setLinks(List.of(links));
        return m;
    }

    private static LinkResolver resolver(Schema s, Model m) {
        return new LinkResolver(Locale.ENGLISH, s, m);
    }

    @Test
    void theFlaggedLinkIsTheDefaultOfItsPair() {
        Schema s = schema(false);
        Link y = link("same_y", "fk_y");
        y.setDefault(true);
        Model m = model(s, link("same_x", "fk_x"), y);
        assertEquals(Optional.of("same_y"), resolver(s, m).findLink(R, "a", "b"));
    }

    @Test
    void withoutADefaultAnAmbiguousPairSaysWhichLinksThereAre() {
        Schema s = schema(false);
        Model m = model(s, link("same_x", "fk_x"), link("same_y", "fk_y"));
        String message =
                assertThrows(RuntimeException.class, () -> resolver(s, m).findLink(R, "a", "b"))
                        .getMessage();
        assertTrue(message.contains("same_x") && message.contains("same_y"), message);
        assertTrue(message.contains("VIA") && message.contains("default"), message);
        assertEquals(
                List.of("same_x", "same_y"),
                new LinkDefaults(s, m).pairsWithoutDefault().get("a / b"));
    }

    @Test
    void aRelationWithAConditionIsNeverFoundByTheTablesAlone() {
        // the plain relation is the one, although a conditioned relation joins the same tables
        Schema s = schema(true);
        s.getRelations().removeIf(r -> r.getName().equals("fk_y"));
        Model m = model(s, link("same_x", "fk_x"), link("same_range", "cond_a_b"));
        assertEquals(Optional.of("same_x"), resolver(s, m).findLink(R, "a", "b"));
    }

    @Test
    void aPairWithOnlyAConditionedRelationHasNoImplicitLink() {
        Schema s =
                CatalogLoader.readSchemaJson(
                        "{ \"name\": \"d\", \"tables\": [ { \"name\": \"a\", \"columns\": [ {\"name\":"
                                + " \"x\", \"concept\": {\"typeFamily\": \"INTEGER\", \"dialectType\": \"INTEGER\"}} ] }, { \"name\": \"b\", \"columns\": ["
                                + " {\"name\": \"x\", \"concept\": {\"typeFamily\": \"INTEGER\", \"dialectType\": \"INTEGER\"}} ] } ], \"relations\": [ {"
                                + " \"name\": \"cond_a_b\", \"startTable\": \"a\", \"endTable\": \"b\","
                                + " \"condition\": \"start.x < end.x\" } ] }");
        Model m = model(s, link("same_range", "cond_a_b"));
        assertTrue(resolver(s, m).findLink(R, "a", "b").isEmpty());
        // and still found when named
        assertTrue(resolver(s, m).findLink(R, "a", "b", "same_range").isPresent());
    }

    @Test
    void twoDefaultsOnOnePairAreRefused() {
        Schema s = schema(false);
        Link x = link("same_x", "fk_x");
        Link y = link("same_y", "fk_y");
        x.setDefault(true);
        y.setDefault(true);
        String message =
                assertThrows(KorykiaiException.class, () -> resolver(s, model(s, x, y)))
                        .getMessage();
        assertTrue(message.contains("more than one default link"), message);
    }

    @Test
    void aDefaultOverAConditionIsRefused() {
        Schema s = schema(true);
        Link range = link("same_range", "cond_a_b");
        range.setDefault(true);
        String message =
                assertThrows(
                                KorykiaiException.class,
                                () -> resolver(s, model(s, link("same_x", "fk_x"), range)))
                        .getMessage();
        assertTrue(message.contains("always written with VIA"), message);
    }

    @Test
    void aDefaultNamingAnUnknownRelationIsRefused() {
        Schema s = schema(false);
        Link x = link("same_x", "fk_nope");
        x.setDefault(true);
        assertThrows(KorykiaiException.class, () -> resolver(s, model(s, x)));
    }

    @Test
    void aSchemaBuiltInCodeIsCheckedByTheResolver() {
        // not read by the loader: the resolver is the one place both schema and model come together
        Schema s = schema(false);
        s.getRelations().get(0).setCondition("start.x < end.nope");
        s.getRelations().get(0).setStartColumns(List.of());
        s.getRelations().get(0).setEndColumns(List.of());
        assertThrows(KorykiaiException.class, () -> resolver(s, model(s, link("same_x", "fk_x"))));
    }

    @Test
    void setOptionalDoesNotTurnAFullOrCrossJoinIntoAnotherKind() {
        Join full = new Join();
        full.setKind(JoinKind.FULL);
        full.setOptional(false);
        assertEquals(JoinKind.FULL, full.getKind());
        full.setOptional(true);
        assertEquals(JoinKind.FULL, full.getKind());

        Join cross = new Join();
        cross.setKind(JoinKind.CROSS);
        cross.setOptional(true);
        assertEquals(JoinKind.CROSS, cross.getKind());

        Join inner = new Join();
        inner.setOptional(true);
        assertEquals(JoinKind.LEFT, inner.getKind());
        inner.setOptional(false);
        assertEquals(JoinKind.INNER, inner.getKind());
        assertFalse(inner.isOptional());
    }
}
