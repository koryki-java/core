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
package ai.koryki.iql.validate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.kql.KQLTranspiler;
import java.io.IOException;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The branches of a set operation return the same number of columns, or the query is refused with
 * the branch that does not fit named, instead of the database's error about generated SQL.
 */
class SetOperationValidatorTest {

    private static LinkResolver resolver;

    @BeforeAll
    static void setUp() throws IOException {
        resolver = NorthwindService.resolver();
    }

    private static String sql(String kql) {
        return KQLTranspiler.builder(kql, resolver)
                .build()
                .getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")));
    }

    private static ValidateException refused(String kql) {
        return assertThrows(ValidateException.class, () -> sql(kql));
    }

    @Test
    void branchesWithTheSameNumberOfColumnsPass() {
        assertDoesNotThrow(
                () ->
                        sql(
                                "FIND customers a FETCH a.contact_name n, a.city c\n"
                                        + "UNION\n"
                                        + "FIND employees b FETCH b.last_name n, b.city c"));
    }

    @Test
    void theColumnNamesNeedNotAgree() {
        assertDoesNotThrow(
                () ->
                        sql(
                                "FIND customers a FETCH a.contact_name n\n"
                                        + "UNIONALL\n"
                                        + "FIND employees b FETCH b.last_name other"));
    }

    @Test
    void aBranchWithMoreColumnsIsRefusedWithTheCounts() {
        ValidateException e =
                refused(
                        "FIND customers a FETCH a.contact_name n\n"
                                + "UNION\n"
                                + "FIND employees b FETCH b.last_name n, b.city c");
        assertEquals(1, e.getViolations().size());
        Violation v = e.getViolations().get(0);
        assertEquals(SetOperationValidator.CATEGORY, v.getCategory());
        assertTrue(
                v.getMessage().contains("UNION needs the same number of columns"), v.getMessage());
        assertTrue(v.getMessage().contains("returns 1, this one returns 2"), v.getMessage());
    }

    @Test
    void theOperatorIsNamedAsWritten() {
        assertTrue(
                refused(
                                "FIND customers a FETCH a.contact_name n, a.city c\n"
                                        + "UNIONALL\n"
                                        + "FIND employees b FETCH b.last_name n")
                        .getMessage()
                        .contains("UNION ALL needs"));
        assertTrue(
                refused(
                                "FIND customers a FETCH a.contact_name n\n"
                                        + "MINUS\n"
                                        + "FIND employees b FETCH b.last_name n, b.city c")
                        .getMessage()
                        .contains("MINUS needs"));
    }

    /** Three branches: the one that differs is the one named, whichever its place. */
    @Test
    void theBranchThatDiffersIsTheOneNamed() {
        ValidateException e =
                refused(
                        "FIND customers a FETCH a.contact_name n\n"
                                + "UNION\n"
                                + "FIND employees b FETCH b.last_name n\n"
                                + "UNION\n"
                                + "FIND suppliers s FETCH s.contact_name n, s.city c");
        assertEquals(1, e.getViolations().size());
        assertTrue(e.getViolations().get(0).getMessage().contains("this one returns 2"));
    }
}
