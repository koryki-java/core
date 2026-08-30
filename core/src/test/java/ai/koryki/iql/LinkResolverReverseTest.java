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

import ai.koryki.antlr.Range;
import ai.koryki.catalog.schema.Relation;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A relation asked for in the opposite of its declared direction.
 *
 * <p>{@code findRelation} searches both ways and accepts a reverse match when the relation is
 * symmetric, but used to hand the relation back describing itself in its own direction. Every
 * caller pairs {@code getStartColumns()} with the side it asked about first, so the columns landed
 * on the wrong alias.
 *
 * <p>Almost every relation hides this, because a foreign key usually carries the name of the column
 * it points at — 26 of Northwind's 34 have identical column lists, and for those a swap and no swap
 * are the same SQL. {@code fk_orders_shippers} is the one that does not.
 */
class LinkResolverReverseTest {

    private static final Range ANY = new Range(1, 1, 1, 1);

    private static LinkResolver resolver;

    @BeforeAll
    static void model() {
        resolver = NorthwindService.resolver();
    }

    @Test
    void aReverseMatchFacesTheWayItWasAskedFor() {

        // orders.ship_via -> shippers.shipper_id, declared that way round in db.json. Asked for
        // from the other side, the start columns have to be the shippers' own.
        Relation r = found("shippers", "orders");

        assertEquals("shippers", r.getStartTable());
        assertEquals("orders", r.getEndTable());
        assertEquals(List.of("shipper_id"), r.getStartColumns());
        assertEquals(List.of("ship_via"), r.getEndColumns());
    }

    @Test
    void theDeclaredDirectionIsUntouched() {

        Relation r = found("orders", "shippers");

        assertEquals("orders", r.getStartTable());
        assertEquals(List.of("ship_via"), r.getStartColumns());
        assertEquals(List.of("shipper_id"), r.getEndColumns());
    }

    @Test
    void theSchemasOwnRelationIsNotMutated() {

        // The instance handed out belongs to the shared Schema and is handed to every other query.
        // Turning it in place would leave the next caller with a relation facing the wrong way.
        found("shippers", "orders");

        Relation declared = resolver.getSchema().getRelation("fk_orders_shippers").orElseThrow();
        assertEquals("orders", declared.getStartTable());
        assertEquals(List.of("ship_via"), declared.getStartColumns());
    }

    @Test
    void aRelationWithMatchingNamesIsUnaffected() {

        // orders.customer_id -> customers.customer_id. Both directions have always worked, and
        // must keep giving the columns the side that asked for them.
        assertEquals(List.of("customer_id"), found("customers", "orders").getStartColumns());
        assertEquals(List.of("customer_id"), found("orders", "customers").getStartColumns());
    }

    @Test
    void aSelfJoinIsLeftAlone() {

        // categories.super_category_id -> categories.category_id, both sides the same table. Table
        // names cannot say which way round the query means it, so the direction comes from the
        // link's declared nature instead - parent_of and child_of are the same relation read two
        // ways. Turning it here would fight that, and both must come back as declared.
        for (String link : List.of("parent_of", "child_of")) {
            Relation r = found("categories", "categories", link);

            assertEquals("categories", r.getStartTable(), link);
            assertEquals(List.of("super_category_id"), r.getStartColumns(), link);
            assertEquals(List.of("category_id"), r.getEndColumns(), link);
        }
    }

    @Test
    void whatLinksBetweenPromisesNowHolds() {

        // The javadoc says a link is listed "exactly when a startEntity VIA link endEntity clause
        // would resolve". This is the pair where it did not.
        assertTrue(resolver.linksBetween("shippers", "orders").contains("same_shipper"));

        Relation r = found("shippers", "orders");
        assertEquals("shippers", r.getStartTable(), "listed, so it must face the asked direction");
    }

    private static Relation found(String start, String end) {
        return found(start, end, null);
    }

    /** The link name only where the pair alone is ambiguous — a self-join has two of them. */
    private static Relation found(String start, String end, String link) {

        Optional<Relation> r = resolver.findRelation(ANY, start, end, link);
        assertTrue(r.isPresent(), () -> "no relation between " + start + " and " + end);
        return r.get();
    }
}
