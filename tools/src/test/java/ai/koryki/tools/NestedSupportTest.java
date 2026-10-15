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
package ai.koryki.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.databases.complex.duckdb.ComplexService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.validate.ValidateException;
import ai.koryki.iql.validate.Violation;
import ai.koryki.kql.KQLTranspiler;
import ai.koryki.mariadb.iql.MariadbDialect;
import ai.koryki.mssql.iql.MssqlDialect;
import ai.koryki.oracle.iql.OracleDialect;
import ai.koryki.postgresql.iql.PostgreSqlDialect;
import ai.koryki.snowflake.iql.SnowflakeDialect;
import ai.koryki.sqlite.iql.SqliteDialect;
import ai.koryki.trino.iql.TrinoDialect;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * What each dialect declares in {@link SqlDialect#nestedSupport()} is a violation of the query when
 * it is used, with a position, and nothing the dialect declares is refused. The declarations are
 * checked against the engines by the corpora; this checks that the validator says what they say.
 */
class NestedSupportTest {

    private static final Map<String, SqlDialect> DIALECTS = dialects();

    private static Map<String, SqlDialect> dialects() {
        LinkedHashMap<String, SqlDialect> m = new LinkedHashMap<>();
        m.put("duckdb", DuckdbBaseDialect.INSTANCE);
        m.put("postgresql", PostgreSqlDialect.INSTANCE);
        m.put("trino", TrinoDialect.INSTANCE);
        m.put("snowflake", SnowflakeDialect.INSTANCE);
        m.put("oracle", OracleDialect.INSTANCE);
        m.put("mssql", MssqlDialect.INSTANCE);
        m.put("mariadb", MariadbDialect.INSTANCE);
        m.put("sqlite", SqliteDialect.INSTANCE);
        return m;
    }

    /** The violations of a query for a dialect; none when it is accepted. */
    private static List<Violation> violations(SqlDialect dialect, String fetch) {
        try {
            KQLTranspiler.builder("FIND check_complex t FETCH " + fetch, ComplexService.resolver())
                    .functions(dialect.getFunctionRenderer())
                    .dialect(dialect)
                    .build()
                    .getSql(new SqlQueryRenderer(dialect, ZoneId.of("UTC")));
            return List.of();
        } catch (ValidateException e) {
            return e.getViolations();
        }
    }

    /** The violations of a whole query body, {@code FILTER ... FETCH ...}. */
    private static List<Violation> violationsOf(SqlDialect dialect, String body) {
        try {
            KQLTranspiler.builder("FIND check_complex t " + body, ComplexService.resolver())
                    .functions(dialect.getFunctionRenderer())
                    .dialect(dialect)
                    .build()
                    .getSql(new SqlQueryRenderer(dialect, ZoneId.of("UTC")));
            return List.of();
        } catch (ValidateException e) {
            return e.getViolations();
        }
    }

    private static boolean unsupported(List<Violation> violations) {
        return violations.stream().anyMatch(Violation::isUnsupported);
    }

    private void expect(String fetch, Set<String> refusedOn, String messagePart) {
        for (Map.Entry<String, SqlDialect> e : DIALECTS.entrySet()) {
            List<Violation> v = violations(e.getValue(), fetch);
            if (refusedOn.contains(e.getKey())) {
                assertTrue(unsupported(v), e.getKey() + " should refuse " + fetch + ": " + v);
                Violation first = v.stream().filter(Violation::isUnsupported).findFirst().get();
                assertNotNull(first.getRange(), e.getKey() + ": a refusal has a position");
                assertTrue(
                        first.getMessage().contains(messagePart),
                        e.getKey() + ": " + first.getMessage());
            } else {
                assertTrue(!unsupported(v), e.getKey() + " should accept " + fetch + ": " + v);
            }
        }
    }

    @Test
    void aListIsOnlyReachedWhereTheEngineHasLists() {
        expect("t.tags@1", Set.of("mssql", "mariadb", "sqlite"), "cannot select from ARRAY(TEXT)");
    }

    @Test
    void aStructIsOnlyReachedWhereTheEngineHasStructs() {
        expect("t.dims@w", Set.of("mssql", "mariadb", "sqlite"), "cannot select from STRUCT");
    }

    @Test
    void aMapIsOnlyReachedWhereTheEngineHasMaps() {
        expect(
                "t.attrs@color",
                Set.of("postgresql", "oracle", "mssql", "mariadb", "sqlite"),
                "cannot select from MAP(TEXT,TEXT)");
    }

    @Test
    void aJsonPositionFromTheEndIsOnlyUsedWhereThePathCountsFromTheEnd() {
        expect("t.doc@-1", Set.of("trino", "snowflake", "mssql"), "no position from the end");
    }

    @Test
    void aLambdaOverAJsonListIsRefusedWhereTheListCannotBeBuiltForIt() {
        for (Map.Entry<String, SqlDialect> e : DIALECTS.entrySet()) {
            if (!e.getValue().getFunctionRenderer().jsonArraysAreLists()) {
                continue; // no lists: the array stays JSON and the function is refused for that
            }
            List<Violation> v = violations(e.getValue(), "list_transform(t.doc@a@b, x -> x * 2)");
            assertEquals(
                    e.getKey().equals("oracle"),
                    unsupported(v),
                    e.getKey() + " list_transform over a JSON array: " + v);
        }
    }

    @Test
    void aListIsCompared_Grouped_OrderedAndExtremedOnlyWhereTheEngineTakesIt() {
        for (String fetch : new String[] {"max(t.scores) m", "min(t.tags) m"}) {
            for (Map.Entry<String, SqlDialect> e : DIALECTS.entrySet()) {
                if (!e.getValue()
                        .nestedSupport()
                        .selectable()
                        .contains(ai.koryki.catalog.types.CoreTypeFamily.ARRAY)) {
                    continue;
                }
                List<Violation> v = violations(e.getValue(), fetch);
                assertEquals(
                        e.getKey().equals("oracle"),
                        unsupported(v),
                        e.getKey() + " " + fetch + ": " + v);
            }
        }
    }

    @Test
    void aListInAComparisonOrAGroupingIsRefusedWhereTheEngineCannotTakeIt() {
        List<Violation> equal =
                violationsOf(OracleDialect.INSTANCE, "FILTER t.tags = t.tags FETCH t.nr");
        List<Violation> group = violationsOf(OracleDialect.INSTANCE, "FETCH t.tags, count(t.nr) n");

        assertTrue(unsupported(equal), equal.toString());
        assertTrue(unsupported(group), group.toString());
        assertTrue(
                equal.stream().anyMatch(v -> v.getMessage().contains("Oracle cannot compare")),
                equal.toString());
        assertTrue(
                group.stream().anyMatch(v -> v.getMessage().contains("cannot group by")),
                group.toString());
        // the engines that take a list take it, and a scalar next to it is never in question
        assertTrue(
                !unsupported(
                        violationsOf(
                                DuckdbBaseDialect.INSTANCE, "FILTER t.tags = t.tags FETCH t.nr")));
        assertTrue(
                !unsupported(violationsOf(OracleDialect.INSTANCE, "FILTER t.nr = 1 FETCH t.nr")));
    }

    @Test
    void theListFunctionsStillTakeAJsonListOnOracle() {
        assertTrue(!unsupported(violations(OracleDialect.INSTANCE, "list_size(t.doc@a@b)")));
    }

    @Test
    void honestQueriesAreNeverRefused() {
        for (SqlDialect d : DIALECTS.values()) {
            assertTrue(!unsupported(violations(d, "t.doc@n")));
            assertTrue(!unsupported(violations(d, "t.doc@a@b@1")));
            assertTrue(!unsupported(violations(d, "t.nr")));
        }
    }

    @Test
    void everyDialectDeclaresItself() {
        assertEquals(8, DIALECTS.size());
        for (SqlDialect d : DIALECTS.values()) {
            assertTrue(
                    d.nestedSupport()
                            .selectable()
                            .contains(ai.koryki.catalog.types.CoreTypeFamily.JSON));
            assertTrue(!d.nestedSupport().name().endsWith("Dialect"), d.nestedSupport().name());
        }
    }
}
