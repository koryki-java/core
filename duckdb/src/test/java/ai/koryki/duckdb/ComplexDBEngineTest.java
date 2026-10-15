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

import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.databases.complex.duckdb.ComplexService;
import ai.koryki.databases.northwind.duckdb.NorthwindDuckdb;
import ai.koryki.kql.EngineBuilder;
import ai.koryki.kql.HeaderInfo;
import java.io.IOException;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;

/**
 * The complex-types test bed ({@code check_complex}) against DuckDB: ARRAY, STRUCT, MAP and JSON
 * columns, nested to several levels. The queries are the corpus under {@code queries/kql/complex}.
 */
public class ComplexDBEngineTest extends BaseEngineTest<HeaderInfo> {

    @Override
    protected String schema() {
        return "complex";
    }

    public ComplexDBEngineTest() {
        super("duckdb", true);
    }

    @BeforeAll
    public void setup() throws IOException {
        engine =
                EngineBuilder.headers(
                                NorthwindDuckdb.<ListWithSqlResult<HeaderInfo>>northwind(),
                                ComplexService.resolver(),
                                () -> new SqlQueryRenderer(java.time.ZoneId.of("UTC")))
                        .valueFormat(new StableFormat(Locale.ROOT))
                        .build();
    }
}
