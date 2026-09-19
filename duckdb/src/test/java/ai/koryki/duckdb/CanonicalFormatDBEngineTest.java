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
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.jdbc.LocaleFormat;
import ai.koryki.kql.EngineBuilder;
import ai.koryki.kql.HeaderInfo;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.util.Locale;

/**
 * Exercises the type-driven read-layer formatter end-to-end: the engine renders
 * results through {@link LocaleFormat} in canonical (ISO) mode, so output is
 * driven by each column's resolved TypeDescriptor — integers without a forced
 * ".0", decimals at their real scale, dates as ISO. Distinct from the
 * StableFormat suites (which keep the formatter inert to isolate SQL goldens).
 */
public class CanonicalFormatDBEngineTest extends BaseEngineTest<HeaderInfo> {

    @Override protected String schema() { return "format"; }

    // A purely module-local suite: this schema does not exist in the shared corpus, so the roots
    // point into the module.
    @Override protected Path queriesRoot() { return Path.of("src/test/resources/ai/koryki/duckdb/queries/format"); }
    @Override protected Path expectedCsv() { return Path.of("src/test/resources/ai/koryki/duckdb/expected/format/csv"); }
    @Override protected Path expectedSql() { return Path.of("src/test/resources/ai/koryki/duckdb/expected/format/sql"); }


    public CanonicalFormatDBEngineTest() {
        super("duckdb");
    }

    @BeforeAll
    public void setup() throws IOException {
        engine = EngineBuilder.headers(NorthwindDuckdb.<ListWithSqlResult<HeaderInfo>>northwind(), NorthwindService.resolver(),
                new SqlQueryRenderer(java.time.ZoneId.of("UTC"))).valueFormat(new LocaleFormat((Locale)null)).build();
    }
}
