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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.schema.Relation;
import ai.koryki.catalog.schema.Schema;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The condition of a relation: what it reads, what it refuses, and how the loader checks it. */
class RelationConditionTest {

    @Test
    void betweenIsTwoComparisons() {
        RelationCondition c = RelationCondition.parse("start.sal BETWEEN end.losal AND end.hisal");
        assertEquals("start.sal >= end.losal AND start.sal <= end.hisal", c.text());
        assertEquals(2, c.terms().size());
    }

    @Test
    void comparisonsAreJoinedWithAnd() {
        RelationCondition c =
                RelationCondition.parse(
                        "start.mgr = end.empno and start.sal != end.sal AND start.a BETWEEN end.b AND end.c");
        assertEquals(
                "start.mgr = end.empno AND start.sal <> end.sal AND start.a >= end.b AND start.a <= end.c",
                c.text());
    }

    @Test
    void flippingSwapsTheRolesAndKeepsTheOperators() {
        RelationCondition c = RelationCondition.parse("start.sal BETWEEN end.losal AND end.hisal");
        assertEquals("end.sal >= start.losal AND end.sal <= start.hisal", c.flipped().text());
        assertEquals(c.text(), c.flipped().flipped().text());
    }

    @Test
    void whatIsNotAConjunctionOfComparisonsIsRefused() {
        for (String bad :
                List.of(
                        "start.a = end.b OR start.c = end.d",
                        "start.a = 1",
                        "start.a + 1 = end.b",
                        "a = b",
                        "start.a = end.b AND",
                        "")) {
            assertThrows(IllegalArgumentException.class, () -> RelationCondition.parse(bad), bad);
        }
    }

    private static String db(String relation) {
        return "{ \"name\": \"d\", \"tables\": ["
                + "{ \"name\": \"emp\", \"columns\": [ {\"name\": \"sal\", \"type\": \"INTEGER\"} ] },"
                + "{ \"name\": \"grade\", \"columns\": [ {\"name\": \"losal\", \"type\": \"INTEGER\"},"
                + " {\"name\": \"hisal\", \"type\": \"INTEGER\"} ] } ],"
                + " \"relations\": [ "
                + relation
                + " ] }";
    }

    @Test
    void aConditionIsReadAndCopied() {
        Schema s =
                CatalogLoader.readSchemaJson(
                        db(
                                "{ \"name\": \"r\", \"startTable\": \"emp\", \"endTable\": \"grade\","
                                        + " \"condition\": \"start.sal BETWEEN end.losal AND end.hisal\" }"));
        Relation r = s.getRelation("r").orElseThrow();
        assertEquals("start.sal BETWEEN end.losal AND end.hisal", r.getCondition());
        assertEquals(r.getCondition(), SchemaCopy.deepCopy(r).getCondition());
    }

    @Test
    void aConditionNamingAMissingColumnIsRefusedAtLoad() {
        String message =
                assertThrows(
                                RuntimeException.class,
                                () ->
                                        CatalogLoader.readSchemaJson(
                                                db(
                                                        "{ \"name\": \"r\", \"startTable\": \"emp\", \"endTable\": \"grade\","
                                                                + " \"condition\": \"start.sal BETWEEN end.lo AND end.hisal\" }")))
                        .getMessage();
        assertTrue(message.contains("no column 'lo' in table grade"), message);
    }

    @Test
    void aConditionAndColumnsTogetherAreRefusedAtLoad() {
        String message =
                assertThrows(
                                RuntimeException.class,
                                () ->
                                        CatalogLoader.readSchemaJson(
                                                db(
                                                        "{ \"name\": \"r\", \"startTable\": \"emp\", \"endTable\": \"grade\","
                                                                + " \"startColumns\": [\"sal\"], \"endColumns\": [\"losal\"],"
                                                                + " \"condition\": \"start.sal <= end.hisal\" }")))
                        .getMessage();
        assertTrue(message.contains("replaces the start and end columns"), message);
    }
}
