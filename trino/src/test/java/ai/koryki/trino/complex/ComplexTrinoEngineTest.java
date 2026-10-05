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
package ai.koryki.trino.complex;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.EngineBuilder;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.trino.TrinoUnavailable;
import ai.koryki.trino.iql.SqlQueryRenderer;
import ai.koryki.trino.northwind.NorthwindTrino;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;

/**
 * The complex-types test bed on Trino, in its memory catalog (the MariaDB connector the other
 * Northwind tables are read through has no nested types); {@code ./gradlew :trino:createDatabase}
 * in the northwind project creates the table, and the memory connector forgets it when Trino
 * restarts. The queries live in this module: Trino has no fixed-size array and no position from the
 * end in a JSON path, so the answers and the types are its own.
 */
@TrinoUnavailable
public class ComplexTrinoEngineTest extends BaseEngineTest<HeaderInfo> {

    private static final String DB = "/ai/koryki/trino/databases/complex";
    private static final String MODEL = "/ai/koryki/trino/databases/complex/model";
    private static final String RESOURCES = "src/test/resources/ai/koryki/trino/";

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

    public ComplexTrinoEngineTest() {
        super("trino", true);
    }

    /** The memory catalog of the Trino of {@code trino.northwind.url}: same host, same user. */
    private static NorthwindTrino<ListWithSqlResult<HeaderInfo>> memory() throws SQLException {
        String url =
                System.getProperty("trino.northwind.url")
                        .replaceFirst("(jdbc:trino://[^/]+).*", "$1/memory/default");
        return new NorthwindTrino<>(
                "complex",
                System.getProperty("trino.northwind.user"),
                System.getProperty("trino.northwind.password"),
                url);
    }

    @BeforeAll
    public void readComplexCatalog() throws IOException, SQLException {
        Locale locale = Locale.ENGLISH;
        Schema db = CatalogLoader.db(DB);
        Model model = CatalogLoader.model(MODEL, locale);
        LinkResolver resolver = new LinkResolver(locale, db, model, true);
        engine =
                EngineBuilder.headers(
                                memory(),
                                resolver,
                                () -> new SqlQueryRenderer(java.time.ZoneId.of("UTC")))
                        .valueFormat(new StableFormat(Locale.ROOT))
                        .build();
    }
}
