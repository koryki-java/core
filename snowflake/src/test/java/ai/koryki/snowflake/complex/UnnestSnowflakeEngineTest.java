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

package ai.koryki.snowflake.complex;

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
import ai.koryki.snowflake.SnowflakeUnavailable;
import ai.koryki.snowflake.iql.SqlQueryRenderer;
import ai.koryki.snowflake.northwind.NorthwindSnowflake;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * A list made into rows on Snowflake, over its complex bed; see {@link UnnestContract}. Where it
 * differs: FLATTEN drops a NULL element, there is no ORDINALITY (the position is INDEX), and its
 * outer FLATTEN takes no condition.
 */
@SnowflakeUnavailable
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UnnestSnowflakeEngineTest extends UnnestContract {

    private Engine<HeaderInfo, ListWithSqlResult<HeaderInfo>> engine;

    @BeforeAll
    void readComplexCatalog() throws Exception {
        Locale locale = Locale.ENGLISH;
        Schema db = CatalogLoader.db(ComplexCatalogTest.DB);
        Model model = CatalogLoader.model(ComplexCatalogTest.MODEL, locale);
        LinkResolver resolver = new LinkResolver(locale, db, model, true);
        engine =
                EngineBuilder.headers(
                                new NorthwindSnowflake<ListWithSqlResult<HeaderInfo>>(),
                                resolver,
                                () -> new SqlQueryRenderer(java.time.ZoneId.of("UTC")))
                        .valueFormat(new StableFormat(Locale.ROOT))
                        .build();
    }

    @Override
    protected Engine<HeaderInfo, ListWithSqlResult<HeaderInfo>> engine() {
        return engine;
    }

    /** FLATTEN leaves a NULL element out: {@code ['x', NULL, 'z']} has two rows. */
    @Override
    @Test
    public void aListOfScalarsHasTheColumnValue() {
        List<List<String>> rows =
                rows(
                        "FIND check_complex t, t unnest(t.tags) g"
                                + " FETCH t.nr, g.ordinal, g.value");

        assertEquals(
                List.of(
                        Arrays.asList("1", "1", "red"),
                        Arrays.asList("1", "2", "green"),
                        Arrays.asList("1", "3", "blue"),
                        Arrays.asList("3", "1", "x"),
                        Arrays.asList("3", "3", "z")),
                rows.stream().sorted(java.util.Comparator.comparing(Object::toString)).toList(),
                "the NULL element of the list of nr 3 is not a row");
    }

    /** The position is INDEX, not WITH ORDINALITY, and costs nothing either way. */
    @Override
    @Test
    public void theOrdinalIsAskedOfTheDatabaseOnlyWhereTheQueryReadsIt() {
        String with = sql("FIND check_complex t, t unnest(t.items) i FETCH t.nr, i.ordinal");

        assertTrue(with.contains("INDEX + 1"), with);
    }

    /** Its outer FLATTEN takes no condition; it says so instead of failing in the database. */
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
