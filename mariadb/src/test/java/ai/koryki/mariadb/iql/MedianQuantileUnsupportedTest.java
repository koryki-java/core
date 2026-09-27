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
package ai.koryki.mariadb.iql;

import static org.junit.jupiter.api.Assertions.assertThrows;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.kql.KQLTranspiler;
import java.io.IOException;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * MariaDB has no ordered-set aggregate syntax at all: neither a native {@code MEDIAN}, nor {@code
 * WITHIN GROUP} for {@code PERCENTILE_CONT}/{@code PERCENTILE_DISC}. Measured 2026-09-27 — see
 * {@code AggregateFunctions} in core for the full cross-dialect picture, and {@code
 * CountDistinctWindowTest} in the postgresql module for why this lives in the dialect module and
 * not in core: {@code core} cannot see {@code MariadbDialect}.
 */
class MedianQuantileUnsupportedTest {

    private static LinkResolver resolver;

    @BeforeAll
    static void setUp() throws IOException {
        resolver = NorthwindService.resolver();
    }

    @Test
    void medianIsRejected() {
        assertRejected("FIND products p FETCH p.category_id, median(p.unit_price) m");
    }

    @Test
    void quantileContIsRejected() {
        assertRejected("FIND products p FETCH p.category_id, quantile_cont(0.5, p.unit_price) m");
    }

    @Test
    void quantileDiscIsRejected() {
        assertRejected("FIND products p FETCH p.category_id, quantile_disc(0.5, p.unit_price) m");
    }

    private static void assertRejected(String kql) {
        assertThrows(
                RuntimeException.class,
                () ->
                        KQLTranspiler.builder(kql, resolver)
                                .functions(MariadbDialect.INSTANCE.getFunctionRenderer())
                                .build()
                                .getSql(
                                        new SqlQueryRenderer(
                                                MariadbDialect.INSTANCE, ZoneId.of("UTC"))),
                "MariaDB must not render SQL it cannot execute");
    }
}
