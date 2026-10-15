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

package ai.koryki.trino.shared;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.SharedConceptContract;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.Engine;
import ai.koryki.kql.EngineBuilder;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.trino.TrinoUnavailable;
import ai.koryki.trino.iql.SqlQueryRenderer;
import ai.koryki.trino.northwind.NorthwindTrino;
import java.sql.SQLException;
import java.util.Locale;

/**
 * A concept that columns share on Trino: the table {@code check_shared}, created by {@code
 * ./gradlew :trino:createShared, in the memory catalog, which forgets it when Trino restarts}. A
 * position is a ROW, and route an ARRAY of them.
 */
@TrinoUnavailable
public class SharedConceptTrinoTest extends SharedConceptContract {

    private static final String DB = "/ai/koryki/trino/databases/shared";
    private static final String MODEL = DB + "/model";

    @Override
    protected LinkResolver resolver(Locale locale) {
        return new LinkResolver(
                locale, CatalogLoader.db(DB), CatalogLoader.model(MODEL, locale), true);
    }

    @Override
    protected Engine<HeaderInfo, ListWithSqlResult<HeaderInfo>> engine(Locale locale) {
        try {
            return EngineBuilder.headers(
                            memory(),
                            resolver(locale),
                            () -> new SqlQueryRenderer(java.time.ZoneId.of("UTC")))
                    .valueFormat(new StableFormat(Locale.ROOT))
                    .build();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The memory catalog of the Trino of {@code trino.northwind.url}: same host, same user. */
    private static NorthwindTrino<ListWithSqlResult<HeaderInfo>> memory() throws SQLException {
        String url =
                System.getProperty("trino.northwind.url")
                        .replaceFirst("(jdbc:trino://[^/]+).*", "$1/memory/default");
        return new NorthwindTrino<>(
                "shared",
                System.getProperty("trino.northwind.user"),
                System.getProperty("trino.northwind.password"),
                url);
    }
}
