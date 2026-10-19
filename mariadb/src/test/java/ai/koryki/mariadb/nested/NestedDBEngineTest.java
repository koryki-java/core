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
package ai.koryki.mariadb.nested;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.EngineBuilder;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.mariadb.MariadbUnavailable;
import ai.koryki.mariadb.iql.SqlQueryRenderer;
import ai.koryki.mariadb.northwind.NorthwindMariadb;
import java.io.IOException;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;

/**
 * Selectors, lambdas and a list made into rows over the branches of the complex bed, on MariaDB:
 * the queries, the rows, the SQL and what the engine cannot do are the shared fixtures of the
 * schema {@code nested} in the northwind project.
 */
@MariadbUnavailable
public class NestedDBEngineTest extends BaseEngineTest<HeaderInfo> {

    @Override
    protected String schema() {
        return "nested";
    }

    public NestedDBEngineTest() {
        super("mariadb", true);
    }

    @BeforeAll
    public void readComplexCatalog() throws IOException, SQLException {
        Locale locale = Locale.ENGLISH;
        Schema db = CatalogLoader.db("/ai/koryki/mariadb/databases/complex");
        Model model = CatalogLoader.model("/ai/koryki/mariadb/databases/complex/model", locale);
        LinkResolver resolver = new LinkResolver(locale, db, model, true);
        engine =
                EngineBuilder.headers(
                                new NorthwindMariadb<ListWithSqlResult<HeaderInfo>>(
                                        ZoneId.of("UTC")),
                                resolver,
                                () -> new SqlQueryRenderer(ZoneId.of("UTC")))
                        .valueFormat(new StableFormat(Locale.ROOT))
                        .build();
    }
}
