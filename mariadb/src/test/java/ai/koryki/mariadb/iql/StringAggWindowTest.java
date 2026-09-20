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
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.kql.KQLTranspiler;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * MySQL/MariaDB cannot evaluate GROUP_CONCAT as a window function, so a windowed string_agg must be
 * rejected at transpile time instead of rendering "GROUP_CONCAT(...) OVER (...)" that fails on the
 * engine.
 */
public class StringAggWindowTest {

    private static KQLTranspiler transpile(String kql) throws IOException {
        return KQLTranspiler.builder(
                        new ByteArrayInputStream(kql.getBytes(StandardCharsets.UTF_8)),
                        NorthwindService.resolver())
                .build();
    }

    // The rendering itself is covered by the shared fixtures (docs/string_agg.kql runs live on
    // MariaDB; window/window_string_agg_running.kql covers windowed rendering on dialects that
    // support it). Only the rejection needs a unit test: the engine harness skips invalid*
    // fixtures by design, and core's invalid* fixtures transpile with DuckDB only.
    @Test
    public void stringAggWithOverIsRejected() throws IOException {
        KQLTranspiler transpiler =
                transpile(
                        """
                FIND orders o
                FETCH o.order_id, string_agg(o.ship_city, ',') OVER (PARTITION o.customer_id) cities
                """);
        UnsupportedOperationException e =
                assertThrows(
                        UnsupportedOperationException.class,
                        () -> transpiler.getSql(new SqlQueryRenderer(ZoneId.of("UTC"))));
        assertTrue(e.getMessage().contains("OVER"), e.getMessage());
    }
}
