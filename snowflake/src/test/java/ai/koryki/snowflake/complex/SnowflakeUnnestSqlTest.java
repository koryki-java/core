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

package ai.koryki.snowflake.complex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.CatalogLoader;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.KQLTranspiler;
import ai.koryki.snowflake.iql.SnowflakeDialect;
import ai.koryki.snowflake.iql.SqlQueryRenderer;
import java.time.ZoneId;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * The SQL of a list made into rows on Snowflake, from KQL through the whole transpiler on its
 * catalog of the complex bed. No connection is needed; the rows are the business of {@link
 * UnnestSnowflakeEngineTest}.
 */
class SnowflakeUnnestSqlTest {

    private static final LinkResolver RESOLVER =
            new LinkResolver(
                    Locale.ENGLISH,
                    CatalogLoader.db(ComplexCatalogTest.DB),
                    CatalogLoader.model(ComplexCatalogTest.MODEL, Locale.ENGLISH),
                    true);

    private static String sql(String kql) {
        return KQLTranspiler.builder(kql, RESOLVER)
                .functions(SnowflakeDialect.INSTANCE.getFunctionRenderer())
                .build()
                .getSql(new SqlQueryRenderer(ZoneId.of("UTC")))
                .replaceAll("\\s+", " ");
    }

    @Test
    void anInnerUnnestIsALateralFlatten() {
        assertEquals(
                "SELECT t.nr , i.VALUE:sku::VARCHAR FROM check_complex t"
                        + " , LATERAL FLATTEN(INPUT => t.items) i ",
                sql("FIND check_complex t, t unnest(t.items) i FETCH t.nr, i.sku"));
    }

    @Test
    void theOuterFormAsksForTheRowOfNullsThatKeepsTheParent() {
        assertTrue(
                sql("FIND check_complex t, t + unnest(t.items) i FETCH t.nr, i.sku")
                        .contains(", LATERAL FLATTEN(INPUT => t.items, OUTER => TRUE) i"));
    }

    @Test
    void thePositionIsTheIndexFromOne() {
        String sql = sql("FIND check_complex t, t unnest(t.items) i FETCH i.ordinal");

        assertTrue(sql.contains("(i.INDEX + 1)"), sql);
    }

    @Test
    void theFieldsAreCastToTheirTypes() {
        String sql = sql("FIND check_complex t, t unnest(t.items) i FETCH i.qty, i.price");

        assertTrue(sql.contains("i.VALUE:qty::INTEGER"), sql);
        assertTrue(sql.contains("i.VALUE:price::NUMBER(10, 2)"), sql);
    }

    @Test
    void aListOfScalarsIsTheValue() {
        assertTrue(
                sql("FIND check_complex t, t unnest(t.tags) g FETCH g.value")
                        .contains("g.VALUE::VARCHAR"));
    }

    @Test
    void theListOfAnElementIsReadFromItsAlias() {
        String sql =
                sql(
                        "FIND check_complex t, t unnest(t.items) i, i unnest(i.opts) o"
                                + " FETCH o.value");

        assertTrue(
                sql.contains(
                        ", LATERAL FLATTEN(INPUT => t.items) i , LATERAL FLATTEN(INPUT => i.VALUE:opts::ARRAY) o"),
                sql);
    }

    @Test
    void aConditionOnTheRowsOfAnOuterUnnestIsRefused() {
        KorykiaiException e =
                assertThrows(
                        KorykiaiException.class,
                        () ->
                                sql(
                                        "FIND check_complex t, t + unnest(t.items) i"
                                                + " FILTER i.qty > 1 FETCH t.nr, i.sku"));

        assertTrue(e.getMessage().contains("cannot filter the rows of an outer unnest"));
    }
}
