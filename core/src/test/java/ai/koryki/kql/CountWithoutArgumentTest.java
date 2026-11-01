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
package ai.koryki.kql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import java.io.IOException;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@code count()} reads no column, and is an aggregate all the same: a plain column fetched beside
 * it has to be grouped, as it is beside {@code count(o)}.
 */
public class CountWithoutArgumentTest {

    private static LinkResolver resolver;

    @BeforeAll
    static void setUp() throws IOException {
        resolver = NorthwindService.resolver();
    }

    @Test
    void aColumnBesideItIsGrouped() {
        assertTrue(sql("FIND orders o FETCH o.ship_country, count() n").contains("GROUP BY"));
    }

    @Test
    void aloneItNeedsNoGroup() {
        assertFalse(sql("FIND orders o FETCH count() n").contains("GROUP BY"));
    }

    @Test
    void aFilterOnItIsAHaving() {
        assertTrue(sql("FIND orders o FILTER count() > 5 FETCH o.ship_country").contains("HAVING"));
    }

    private static String sql(String kql) {
        return KQLTranspiler.builder(kql, resolver)
                .functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer())
                .build()
                .getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")));
    }
}
