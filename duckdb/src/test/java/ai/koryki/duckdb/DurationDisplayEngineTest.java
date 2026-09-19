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
package ai.koryki.duckdb;

import java.nio.file.Path;
import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.northwind.duckdb.NorthwindDuckdb;
import ai.koryki.databases.temporal.duckdb.TemporalService;
import ai.koryki.jdbc.WordedLocaleFormat;
import ai.koryki.kql.EngineBuilder;
import ai.koryki.kql.HeaderInfo;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.util.Locale;

/**
 * Demonstrates the business-facing duration rendering: same engine/data as the temporal tests, but
 * formatted with {@link WordedLocaleFormat} (WIDE) under {@code Locale.ENGLISH} instead of the
 * canonical {@code StableFormat}. The golden therefore shows the worded / HH:MM:SS form.
 */
public class DurationDisplayEngineTest extends BaseEngineTest<HeaderInfo> {

    @Override protected String schema() { return "display"; }

    // A purely module-local suite: this schema does not exist in the shared corpus, so the roots
    // point into the module.
    @Override protected Path queriesRoot() { return Path.of("src/test/resources/ai/koryki/duckdb/queries/display"); }
    @Override protected Path expectedCsv() { return Path.of("src/test/resources/ai/koryki/duckdb/expected/display/csv"); }
    @Override protected Path expectedSql() { return Path.of("src/test/resources/ai/koryki/duckdb/expected/display/sql"); }


    public DurationDisplayEngineTest() {
        super("duckdb");
    }

    @BeforeAll
    public void setup() throws IOException {
        engine = EngineBuilder.headers(NorthwindDuckdb.<ListWithSqlResult<HeaderInfo>>northwind(), TemporalService.resolver(),
                new SqlQueryRenderer(java.time.ZoneId.of("UTC"))).valueFormat(WordedLocaleFormat.wide(Locale.ENGLISH)).build();
    }
}
