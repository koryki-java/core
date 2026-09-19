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
package ai.koryki.trino.iql;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.kql.KQLTranspiler;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trino renders string_agg as array_join(array_agg(..)) — an OVER clause cannot
 * attach to the outer array_join call, so a windowed string_agg must be rejected
 * at transpile time instead of rendering invalid SQL. Transpile-only, no DB needed
 * (the engine harness cannot assert dialect-specific failures).
 */
public class StringAggWindowTest {

    @Test
    public void stringAggWithOverIsRejected() throws IOException {
        KQLTranspiler transpiler = KQLTranspiler.builder(new ByteArrayInputStream("""
                FIND orders o
                FETCH o.order_id, string_agg(o.ship_city, ',') OVER (PARTITION o.customer_id) cities
                """.getBytes(StandardCharsets.UTF_8)), NorthwindService.resolver()).build();
        UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class,
                () -> transpiler.getSql(new SqlQueryRenderer(ZoneId.of("UTC"))));
        assertTrue(e.getMessage().contains("OVER"), e.getMessage());
    }
}
