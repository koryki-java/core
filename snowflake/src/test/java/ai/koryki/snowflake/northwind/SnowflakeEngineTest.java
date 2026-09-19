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
package ai.koryki.snowflake.northwind;

import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.kql.HeaderInfo;
import java.util.Locale;
import ai.koryki.databases.cases.TestUtil;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.kql.EngineBuilder;
import ai.koryki.snowflake.SnowflakeUnavailable;
import ai.koryki.snowflake.iql.SqlQueryRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

@SnowflakeUnavailable
public class SnowflakeEngineTest extends BaseEngineTest<HeaderInfo> {

    @Override protected String schema() { return "northwind"; }

    // The parse_* fixtures are dialect-specific here (their own format masks) and therefore live
    // in the module, not in the shared corpus.
    @Override protected Path localQueries() { return Path.of("src/test/resources/ai/koryki/snowflake/queries/northwind"); }
    @Override protected Path localExpectedCsv() { return Path.of("src/test/resources/ai/koryki/snowflake/expected/northwind/csv"); }
    @Override protected Path localExpectedSql() { return Path.of("src/test/resources/ai/koryki/snowflake/expected/northwind/sql"); }


    public SnowflakeEngineTest() {
        super("snowflake", true);
    }

    @BeforeAll
    public void readNorthwindDB() throws Exception {
        engine = EngineBuilder.headers(new NorthwindSnowflake<ListWithSqlResult<HeaderInfo>>(), NorthwindService.resolver(),
                new SqlQueryRenderer(java.time.ZoneId.of("UTC"))).valueFormat(new StableFormat(Locale.ROOT)).build();
    }

    @Test
    public void testSingleFile() throws IOException {
        Path kql = queriesRoot().resolve("expression/str_trim.kql");
        TestUtil.test(kql, suffix(), engine, queriesRoot(), expectedCsv(), expectedSql());
    }
}
