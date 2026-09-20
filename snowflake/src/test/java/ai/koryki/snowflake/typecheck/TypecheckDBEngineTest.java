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
package ai.koryki.snowflake.typecheck;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.databases.cases.TestUtil;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.EngineBuilder;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.snowflake.SnowflakeUnavailable;
import ai.koryki.snowflake.iql.SqlQueryRenderer;
import ai.koryki.snowflake.northwind.NorthwindSnowflake;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SnowflakeUnavailable
public class TypecheckDBEngineTest extends BaseEngineTest<HeaderInfo> {

    @Override
    protected String schema() {
        return "typecheck";
    }

    public static final String DB = "/ai/koryki/snowflake/databases/typecheck";
    public static final String MODEL = "/ai/koryki/snowflake/databases/typecheck/model";

    public TypecheckDBEngineTest() {
        super("snowflake", true);
    }

    @BeforeAll
    public void readNorthwindDB() throws Exception {
        Locale locale = Locale.ENGLISH;
        Schema db = CatalogLoader.db(DB);
        Model schema = CatalogLoader.model(MODEL, locale);
        LinkResolver resolver = new LinkResolver(locale, db, schema, true);
        engine =
                EngineBuilder.headers(
                                new NorthwindSnowflake<ListWithSqlResult<HeaderInfo>>(),
                                resolver,
                                new SqlQueryRenderer(java.time.ZoneId.of("UTC")))
                        .valueFormat(new StableFormat(Locale.ROOT))
                        .build();
    }

    @Test
    public void testSingleFile() throws IOException {
        Path kql = queriesRoot().resolve("smallint_tointeger.kql");
        TestUtil.test(kql, suffix(), engine, queriesRoot(), expectedCsv(), expectedSql());
    }
}
