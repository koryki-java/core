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

import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.databases.complex.duckdb.ComplexService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.SqlQueryRenderer;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** An {@code unnest} source in a block of a {@code WITH} and in the link of an {@code EXISTS}. */
public class UnnestBlockTest {

    private static String sql(String kql) {
        return KQLTranspiler.builder(kql, ComplexService.resolver())
                .functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer())
                .build()
                .getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")));
    }

    @Test
    void anUnnestInABlockIsRendered() {
        String sql =
                sql(
                        "WITH b AS (FIND check_complex t, t unnest(t.items) i"
                                + " FETCH t.nr nr, i.sku sku) FIND b x FETCH x.nr, x.sku");
        assertTrue(sql.toLowerCase().contains("unnest"), sql);
    }

    @Test
    void anUnnestInAnExistsIsRendered() {
        String sql =
                sql(
                        "FIND check_complex t FILTER EXISTS (t VIA same_check check_complex u,"
                                + " u unnest(u.items) i FILTER i.qty > 1) FETCH t.nr");
        assertTrue(sql.toLowerCase().contains("unnest"), sql);
    }
}
