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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.databases.cases.UnnestContract;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.Engine;
import ai.koryki.kql.EngineBuilder;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.trino.TrinoUnavailable;
import ai.koryki.trino.iql.SqlQueryRenderer;
import ai.koryki.trino.northwind.NorthwindTrino;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * A list made into rows on Trino, over its complex bed in the memory catalog; see {@link
 * UnnestContract}.
 */
@TrinoUnavailable
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UnnestTrinoEngineTest extends UnnestContract {

    private Engine<HeaderInfo, ListWithSqlResult<HeaderInfo>> engine;

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
    void readComplexCatalog() throws IOException, SQLException {
        Locale locale = Locale.ENGLISH;
        Schema db = CatalogLoader.db("/ai/koryki/trino/databases/complex");
        Model model = CatalogLoader.model("/ai/koryki/trino/databases/complex/model", locale);
        LinkResolver resolver = new LinkResolver(locale, db, model, true);
        engine =
                EngineBuilder.headers(
                                memory(),
                                resolver,
                                () -> new SqlQueryRenderer(java.time.ZoneId.of("UTC")))
                        .valueFormat(new StableFormat(Locale.ROOT))
                        .build();
    }

    /**
     * What Trino cannot do: its outer join to UNNEST takes only ON TRUE, and a lateral subquery
     * over UNNEST is "not supported" (measured, Trino 481). It says so instead of failing in the
     * database.
     */
    @Override
    @Test
    public void aFilterOnTheRowsOfTheOuterJoinKeepsEveryParent() {
        KorykiaiException e =
                assertThrows(
                        KorykiaiException.class,
                        () ->
                                engine().toSql(
                                                "FIND check_complex t, t + unnest(t.items) i"
                                                        + " FILTER i.qty > 1 FETCH t.nr, i.sku"));

        assertTrue(
                e.getMessage().contains("cannot filter the rows of an outer unnest"),
                e.getMessage());
    }

    @Override
    protected Engine<HeaderInfo, ListWithSqlResult<HeaderInfo>> engine() {
        return engine;
    }
}
