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
package ai.koryki.postgresql.iql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.kql.KQLTranspiler;
import java.io.IOException;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * PostgreSQL has no native {@code MEDIAN}, so it overrides the function to {@code
 * PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY x)} — exact, not an approximation, and measured equal
 * to Oracle's native {@code MEDIAN} on the same input (2026-09-27). {@code quantile_cont} and
 * {@code quantile_disc} already default to the {@code WITHIN GROUP} form and need no override on
 * this dialect; the test below confirms they still render that way once the override for {@code
 * median} is layered on top of the same registry.
 */
class MedianQuantileTest {

    private static LinkResolver resolver;

    @BeforeAll
    static void setUp() throws IOException {
        resolver = NorthwindService.resolver();
    }

    @Test
    void medianIsRewrittenToPercentileContWithinGroup() {
        String sql = sql("FIND products p FETCH p.category_id, median(p.unit_price) m");
        assertTrue(sql.contains("PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY p.unit_price)"), sql);
        assertTrue(!sql.contains("MEDIAN("), sql);
    }

    @Test
    void quantileContStillRendersAsPercentileContWithinGroup() {
        String sql = sql("FIND products p FETCH p.category_id, quantile_cont(0.5, p.unit_price) m");
        assertTrue(sql.contains("PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY p.unit_price)"), sql);
    }

    @Test
    void quantileDiscStillRendersAsPercentileDiscWithinGroup() {
        String sql =
                sql("FIND products p FETCH p.category_id, quantile_disc(0.25, p.unit_price) m");
        assertTrue(sql.contains("PERCENTILE_DISC(0.25) WITHIN GROUP (ORDER BY p.unit_price)"), sql);
    }

    private static String sql(String kql) {
        return KQLTranspiler.builder(kql, resolver)
                .functions(PostgreSqlDialect.INSTANCE.getFunctionRenderer())
                .build()
                .getSql(new SqlQueryRenderer(PostgreSqlDialect.INSTANCE, ZoneId.of("UTC")));
    }
}
