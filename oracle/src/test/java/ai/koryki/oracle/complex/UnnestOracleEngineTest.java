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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import ai.koryki.oracle.OracleUnavailable;
import ai.koryki.oracle.iql.SqlQueryRenderer;
import ai.koryki.oracle.northwind.NorthwindOracle;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * A list made into rows on Oracle, over its complex bed; see {@link UnnestContract}. Where it
 * differs: a collection has no position for its elements in a lateral join, and its OUTER APPLY
 * takes no condition.
 */
@OracleUnavailable
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UnnestOracleEngineTest extends UnnestContract {

    private Engine<HeaderInfo, ListWithSqlResult<HeaderInfo>> engine;

    @BeforeAll
    void readComplexCatalog() throws IOException, SQLException {
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

    @Override
    protected Engine<HeaderInfo, ListWithSqlResult<HeaderInfo>> engine() {
        return engine;
    }

    @Override
    protected boolean ordinal() {
        return false;
    }

    /** A NUMBER has no scale of its own: 24.50 comes back as 24.5. */
    @Override
    protected String money(String twoDigits) {
        return twoDigits.replaceAll("0$", "");
    }

    /**
     * A list of objects cannot be filtered on Oracle (the generic collection types hold only text,
     * numbers and dates), and says so; a list of numbers can.
     */
    @Override
    @Test
    public void theListMayBeWhatALambdaMade() {
        assertEquals(
                java.util.List.of(
                        java.util.List.of("1", "10"),
                        java.util.List.of("1", "20"),
                        java.util.List.of("1", "30")),
                rows(
                        "FIND check_complex t,"
                                + " t unnest(list_filter(t.scores, x -> x > 5)) s FETCH t.nr, s.value"));

        KorykiaiException e =
                assertThrows(
                        KorykiaiException.class,
                        () ->
                                engine().toSql(
                                                "FIND check_complex t,"
                                                        + " t unnest(list_filter(t.items, x -> x@qty > 1)) i"
                                                        + " FETCH t.nr, i.sku"));
        assertTrue(e.getMessage().contains("Oracle cannot build a list of STRUCT"));
    }

    /** The position is refused, in the words of the engine, instead of a wrong number. */
    @Override
    @Test
    public void theOrdinalIsAskedOfTheDatabaseOnlyWhereTheQueryReadsIt() {
        KorykiaiException e =
                assertThrows(
                        KorykiaiException.class,
                        () ->
                                engine().toSql(
                                                "FIND check_complex t, t unnest(t.items) i"
                                                        + " FETCH t.nr, i.ordinal"));

        assertTrue(e.getMessage().contains("no position for the elements of a collection"));
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
                                                "FIND check_complex t, t + unnest(t.items) i"
                                                        + " FILTER i.qty > 1 FETCH t.nr, i.sku"));

        assertTrue(e.getMessage().contains("cannot filter the rows of an outer unnest"));
    }
}
