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

package ai.koryki.mssql.complex;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.databases.cases.UnnestJsonContract;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.Engine;
import ai.koryki.kql.EngineBuilder;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.mssql.MssqlUnavailable;
import ai.koryki.mssql.iql.SqlQueryRenderer;
import ai.koryki.mssql.northwind.NorthwindMssql;
import java.io.IOException;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * A JSON array made into rows on Mssql, over the array its catalog declares; see {@link
 * UnnestJsonContract}.
 */
@MssqlUnavailable
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UnnestJsonMssqlEngineTest extends UnnestJsonContract {

    private Engine<HeaderInfo, ListWithSqlResult<HeaderInfo>> engine;

    @BeforeAll
    void readComplexCatalog() throws IOException, SQLException {
        Locale locale = Locale.ENGLISH;
        Schema db = CatalogLoader.db(ComplexCatalogTest.DB);
        Model model = CatalogLoader.model(ComplexCatalogTest.MODEL, locale);
        LinkResolver resolver = new LinkResolver(locale, db, model, true);
        engine =
                EngineBuilder.headers(
                                new NorthwindMssql<ListWithSqlResult<HeaderInfo>>(ZoneId.of("UTC")),
                                resolver,
                                () -> new SqlQueryRenderer(ZoneId.of("UTC")))
                        .valueFormat(new StableFormat(Locale.ROOT))
                        .build();
    }

    @Override
    protected Engine<HeaderInfo, ListWithSqlResult<HeaderInfo>> engine() {
        return engine;
    }

    /** OUTER APPLY takes no condition; it says so instead of failing in the database. */
    @Override
    @Test
    public void aFilterOnTheRowsOfTheOuterJoinKeepsEveryParent() {
        KorykiaiException e =
                assertThrows(
                        KorykiaiException.class,
                        () ->
                                engine().toSql(
                                                "FIND check_complex t, t + unnest(t.doc@a@b) b"
                                                        + " FILTER b.value > 5 FETCH t.nr, b.value"));

        assertTrue(e.getMessage().contains("cannot filter the rows of an outer unnest"));
    }
}
