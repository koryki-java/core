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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.validate.Violation;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@code agg} reads a measure and is rewritten before the transpiler; one that arrives here was
 * not, and is refused at the call, with its position, instead of reaching the database.
 */
class ReservedAggTest {

    private static Generator<HeaderInfo> generator;

    @BeforeAll
    static void generator() {
        generator =
                new Generator<>(
                        NorthwindService.resolver(),
                        new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")),
                        HeaderInfo::new);
    }

    @Test
    void anUnrewrittenAggIsRefusedAtTheCall() {
        List<Violation> v =
                generator.validateKQL("FIND orders o FETCH o.order_id, agg(o.total_freight) f");
        Violation agg =
                v.stream().filter(x -> x.getMessage().contains("'agg'")).findFirst().orElseThrow();
        assertEquals(Violation.Severity.ERROR, agg.getSeverity());
        assertEquals(1, agg.getRange().getStart().getLine());
        assertEquals(33, agg.getRange().getStart().getPos());
    }

    @Test
    void otherFunctionsAreNotTouched() {
        List<Violation> v = generator.validateKQL("FIND orders o FETCH count() n");
        assertTrue(v.stream().noneMatch(x -> x.getMessage().contains("'agg'")));
    }
}
