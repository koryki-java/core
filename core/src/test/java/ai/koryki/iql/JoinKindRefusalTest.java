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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.validate.Violation;
import ai.koryki.kql.KQLTranspiler;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** What the join kinds and the relations with a condition refuse, and says so. */
class JoinKindRefusalTest {

    private static LinkResolver resolver;

    @BeforeAll
    static void readNorthwind() {
        resolver = NorthwindService.resolver();
    }

    /** KQL cannot write a CROSS join with columns (the grammar has no such form), IQL can. */
    @Test
    void aCrossJoinWithColumnsIsAViolation() throws java.io.IOException {
        String iql =
                "SELECT\n shippers s\n  JOIN CROSS [shipper_id=category_id] categories c\n  OWNER\n"
                        + "ALL\n OUT count() pairs 1\n";
        List<Violation> violations =
                IQLTranspiler.builder(
                                new ByteArrayInputStream(iql.getBytes(StandardCharsets.UTF_8)),
                                resolver)
                        .build()
                        .violations();
        assertTrue(
                violations.stream().anyMatch(v -> v.getMessage().contains("has no condition")),
                violations.toString());
    }

    @Test
    void aCrossJoinWithAViaIsNotKql() {
        // the grammar leaves the join out of the CROSS form: a syntax error, not a silent drop
        RuntimeException e =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                KQLTranspiler.builder(
                                                "FIND shippers s, s VIA same_shipper CROSS categories c"
                                                        + " FETCH count() pairs",
                                                resolver)
                                        .build()
                                        .getSql(
                                                new SqlQueryRenderer(
                                                        DuckdbBaseDialect.INSTANCE,
                                                        ZoneId.of("UTC"))));
        assertTrue(e.getMessage().contains("CROSS"), e.getMessage());
    }

    /** The condition needs both tables as entities: a block has no such columns to compare. */
    @Test
    void aRelationWithAConditionDoesNotJoinABlock() {
        String kql =
                "WITH b AS (FIND employees e FETCH e.employee_id id, e.city city, e.hire_date hired)"
                        + " FIND employees x, x VIA same_city_later_hire b y FETCH x.last_name";
        RuntimeException e =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                KQLTranspiler.builder(kql, resolver)
                                        .build()
                                        .getSql(
                                                new SqlQueryRenderer(
                                                        DuckdbBaseDialect.INSTANCE,
                                                        ZoneId.of("UTC"))));
        assertTrue(e.getMessage().contains("has a condition"), e.getMessage());
    }

    @Test
    void aQueryWithoutViaDoesNotFindTheConditionRelation() {
        // employees and employees have the condition link and others: none is taken implicitly
        RuntimeException e =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                KQLTranspiler.builder(
                                                "FIND employees e, employees l FETCH count() n",
                                                resolver)
                                        .build()
                                        .getSql(
                                                new SqlQueryRenderer(
                                                        DuckdbBaseDialect.INSTANCE,
                                                        ZoneId.of("UTC"))));
        // the candidates it names are the keys; the link with a condition is not among them
        assertTrue(e.getMessage().contains("VIA"), e.getMessage());
        assertFalse(e.getMessage().contains("same_city_later_hire"), e.getMessage());
    }
}
