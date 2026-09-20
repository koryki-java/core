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
package ai.koryki.oracle.typecheck;

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
import ai.koryki.oracle.OracleUnavailable;
import ai.koryki.oracle.iql.SqlQueryRenderer;
import ai.koryki.oracle.northwind.NorthwindOracle;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@OracleUnavailable
public class TypecheckDBEngineTest extends BaseEngineTest<HeaderInfo> {

    @Override
    protected String schema() {
        return "typecheck";
    }

    public static final String DB = "/ai/koryki/oracle/databases/typecheck";
    public static final String MODEL = "/ai/koryki/oracle/databases/typecheck/model";

    public TypecheckDBEngineTest() {
        super("oracle", true);
    }

    @BeforeAll
    public void readNorthwindDB() throws IOException, SQLException {
        Locale locale = Locale.ENGLISH;
        Schema db = CatalogLoader.db(DB);
        Model schema = CatalogLoader.model(MODEL, locale);
        LinkResolver resolver = new LinkResolver(locale, db, schema, true);
        engine =
                EngineBuilder.headers(
                                new NorthwindOracle<ListWithSqlResult<HeaderInfo>>(),
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
