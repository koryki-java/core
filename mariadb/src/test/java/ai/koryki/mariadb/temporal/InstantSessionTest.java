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
package ai.koryki.mariadb.temporal;

import ai.koryki.catalog.Util;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.databases.cases.CSVAssert;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.Engine;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.mariadb.MariadbUnavailable;
import ai.koryki.mariadb.iql.SqlQueryRenderer;
import ai.koryki.mariadb.northwind.NorthwindMariadb;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A MariaDB {@code TIMESTAMP} is an instant (stored UTC, session-zone-converted on read). The engine
 * pins the session to {@code +00:00} ({@code MariadbDatabase}) and reads it as a zone-neutral
 * wall-clock so the value never depends on who runs the query (docs/TEMPORAL.md). These tests vary the
 * JVM default zone around the read and assert the instant is unchanged — they would fail if the JVM
 * zone leaked into the conversion (e.g. via {@code Timestamp.toInstant()} or a dropped session pin).
 *
 * <p>{@code timestamp_zoned} for {@code nr = 1} is {@code 2024-04-12 12:14:40Z} (the canonical INSTANT
 * shared across all dialects).
 */
@MariadbUnavailable
public class InstantSessionTest {

    private static final String DB    = "/ai/koryki/mariadb/databases/temporal";
    private static final String MODEL = "/ai/koryki/mariadb/databases/temporal/model";
    private static final String QUERY = "FIND check_temporal c FILTER c.nr = 1 FETCH c.timestamp_zoned";

    /** Fresh engine (and connection) under the current JVM default zone. */
    private static Engine<HeaderInfo, ListWithSqlResult<HeaderInfo>> engine() throws Exception {
        Locale locale = Locale.ENGLISH;
        Schema db = Util.db(DB);
        Model schema = Util.model(MODEL, locale);
        LinkResolver resolver = new LinkResolver(locale, db, schema, true);
        return Engine.builder(new NorthwindMariadb<ListWithSqlResult<HeaderInfo>>(), resolver,
                new SqlQueryRenderer(java.time.ZoneId.of("UTC"))).format(new StableFormat(Locale.ROOT)).build();
    }

    private static String readInstant() throws Exception {
        return new CSVAssert<>(engine(), QUERY, "timestamp_zoned").getCsv();
    }

    @Test
    void instantReadIsIndependentOfJvmDefaultZone() throws Exception {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));   // -07/-08
            String la = readInstant();
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));          // +05:30
            String kolkata = readInstant();

            assertEquals(la, kolkata, "instant read must not depend on the JVM default zone");
            assertTrue(la.contains("2024-04-12") && la.contains("12:14:40"),
                    "instant must read as its UTC wall-clock (session pinned to +00:00), got: " + la);
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
