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
package ai.koryki.snowflake.complex;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.EngineBuilder;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.snowflake.SnowflakeUnavailable;
import ai.koryki.snowflake.iql.SqlQueryRenderer;
import ai.koryki.snowflake.northwind.NorthwindSnowflake;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;

/**
 * The complex-types test bed on Snowflake. The queries live in this module: Snowflake has no
 * fixed-size array and no position from the end in a JSON path, so the answers and the types are
 * its own. The result goldens started as Trino's, which agree with DuckDB's but for JSON spacing;
 * the SQL goldens are written by the first run against the account ({@code -Dfixtures.write=true}).
 */
@SnowflakeUnavailable
public class ComplexSnowflakeEngineTest extends BaseEngineTest<HeaderInfo> {

    private static final String RESOURCES = "src/test/resources/ai/koryki/snowflake/";

    @Override
    protected String schema() {
        return "complex";
    }

    @Override
    protected Path queriesRoot() {
        return Path.of(RESOURCES + "queries/complex");
    }

    @Override
    protected Path expectedCsv() {
        return Path.of(RESOURCES + "expected/complex/csv");
    }

    @Override
    protected Path expectedSql() {
        return Path.of(RESOURCES + "expected/complex/sql");
    }

    @Override
    protected Path expectedViolations() {
        return Path.of(RESOURCES + "expected/complex/violations");
    }

    public ComplexSnowflakeEngineTest() {
        super("snowflake", true);
    }

    @BeforeAll
    public void readComplexCatalog() throws Exception {
        Locale locale = Locale.ENGLISH;
        Schema db = CatalogLoader.db(ComplexCatalogTest.DB);
        Model model = CatalogLoader.model(ComplexCatalogTest.MODEL, locale);
        LinkResolver resolver = new LinkResolver(locale, db, model, true);
        engine =
                EngineBuilder.headers(
                                new NorthwindSnowflake<ListWithSqlResult<HeaderInfo>>(),
                                resolver,
                                () -> new SqlQueryRenderer(java.time.ZoneId.of("UTC")))
                        .valueFormat(new StableFormat(Locale.ROOT))
                        .build();
    }
}
