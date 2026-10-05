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
package ai.koryki.oracle.complex;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
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

/**
 * The complex-types test bed on Oracle: collections and object types as they are, JSON for the
 * maps. The queries live in this module: Oracle's maps are JSON objects and cannot be built or
 * sliced as lists of objects, so the corpus and some answers are its own.
 */
@OracleUnavailable
public class ComplexOracleEngineTest extends BaseEngineTest<HeaderInfo> {

    private static final String RESOURCES = "src/test/resources/ai/koryki/oracle/";

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

    public ComplexOracleEngineTest() {
        super("oracle", true);
    }

    @BeforeAll
    public void readComplexCatalog() throws IOException, SQLException {
        Locale locale = Locale.ENGLISH;
        Schema db = CatalogLoader.db(ComplexCatalogTest.DB);
        Model model = CatalogLoader.model(ComplexCatalogTest.MODEL, locale);
        LinkResolver resolver = new LinkResolver(locale, db, model, true);
        engine =
                EngineBuilder.headers(
                                new NorthwindOracle<ListWithSqlResult<HeaderInfo>>(),
                                resolver,
                                () -> new SqlQueryRenderer(java.time.ZoneId.of("UTC")))
                        .valueFormat(new StableFormat(Locale.ROOT))
                        .build();
    }
}
