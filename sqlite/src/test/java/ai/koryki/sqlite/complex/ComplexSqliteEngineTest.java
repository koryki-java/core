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
package ai.koryki.sqlite.complex;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.EngineBuilder;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.sqlite.iql.SqlQueryRenderer;
import ai.koryki.sqlite.northwind.NorthwindSqlite;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;

/**
 * The complex-types test bed on SQLite, which has one nested value, JSON. The queries live in this
 * module: the answers are those of json_extract.
 */
public class ComplexSqliteEngineTest extends BaseEngineTest<HeaderInfo> {

    private static final String RESOURCES = "src/test/resources/ai/koryki/sqlite/";

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

    public ComplexSqliteEngineTest() {
        super("sqlite", true);
    }

    @BeforeAll
    public void readComplexCatalog() throws IOException, SQLException {
        Locale locale = Locale.ENGLISH;
        Schema db = CatalogLoader.db(ComplexCatalogTest.DB);
        Model model = CatalogLoader.model(ComplexCatalogTest.MODEL, locale);
        LinkResolver resolver = new LinkResolver(locale, db, model, true);
        engine =
                EngineBuilder.headers(
                                NorthwindSqlite.<ListWithSqlResult<HeaderInfo>>northwind(),
                                resolver,
                                () -> new SqlQueryRenderer(java.time.ZoneId.of("UTC")))
                        .valueFormat(new StableFormat(Locale.ROOT))
                        .build();
    }
}
