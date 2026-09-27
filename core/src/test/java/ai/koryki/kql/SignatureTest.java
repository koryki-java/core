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
package ai.koryki.kql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.jdbc.Database;
import ai.koryki.jdbc.ListResult;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * The signature comment: written once, right behind the first {@code SELECT} keyword, wherever this
 * generator's SQL ends up -- {@link Generator#toSql} and {@link Engine#executeKQL} alike -- and
 * never when there is none to write.
 */
class SignatureTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final String DB = "/ai/koryki/databases/northwind";
    private static final String MODEL = DB + "/model";
    private static final String CUSTOMERS = "FIND customers c FETCH c.company_name ASC";

    private static LinkResolver resolver() {
        return new LinkResolver(
                Locale.ENGLISH,
                CatalogLoader.db(DB),
                CatalogLoader.model(MODEL, Locale.ENGLISH),
                true);
    }

    private static SqlQueryRenderer duckdb() {
        return new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, UTC);
    }

    private static final class EchoDatabase implements Database<ListResult<HeaderInfo>> {
        @Override
        public void executeInto(String sql, ListResult<HeaderInfo> processor) {
            processor.append(List.<Object>of(sql));
        }

        @Override
        public boolean allowsConcurrentExecution() {
            return true;
        }

        @Override
        public void close() {}
    }

    private static String echoed(ListResult<HeaderInfo> result) {
        return (String) result.getRows().get(0).get(0);
    }

    @Test
    void noSignatureLeavesTheSqlUntouched() {
        Generator<HeaderInfo> generator =
                new Generator<>(resolver(), SignatureTest::duckdb, HeaderInfo::new);

        String plain = generator.toSql(CUSTOMERS);
        String withNull = generator.withSignature(null).toSql(CUSTOMERS);
        String withBlank = generator.withSignature("  ").toSql(CUSTOMERS);

        assertEquals(plain, withNull);
        assertEquals(plain, withBlank);
        assertFalse(plain.contains("--"), plain);
    }

    @Test
    void theSignatureIsACommentRightBehindTheFirstSelect() {
        Generator<HeaderInfo> generator =
                new Generator<>(resolver(), SignatureTest::duckdb, HeaderInfo::new)
                        .withSignature("koryki");

        String sql = generator.toSql(CUSTOMERS);

        assertTrue(sql.startsWith("SELECT\n"), sql);
        assertTrue(sql.contains("SELECT\n  -- koryki\n"), sql);
    }

    @Test
    void withSignatureLeavesTheOriginalGeneratorUnchanged() {
        Generator<HeaderInfo> plain =
                new Generator<>(resolver(), SignatureTest::duckdb, HeaderInfo::new);
        Generator<HeaderInfo> signed = plain.withSignature("koryki");

        assertNotSame(plain, signed);
        assertNull(plain.getSignature());
        assertEquals("koryki", signed.getSignature());
        assertFalse(plain.toSql(CUSTOMERS).contains("-- koryki"));
        assertTrue(signed.toSql(CUSTOMERS).contains("-- koryki"));
    }

    @Test
    void executeKqlCarriesTheSameSignatureToTheDatabase() {
        Engine<HeaderInfo, ListResult<HeaderInfo>> engine =
                new Engine<>(new EchoDatabase(), resolver(), SignatureTest::duckdb, HeaderInfo::new)
                        .withSignature("koryki");

        String reached = echoed(engine.executeKQL(CUSTOMERS, ListResult::new));

        assertTrue(reached.contains("-- koryki"), reached);
    }

    @Test
    void engineBuilderExposesTheSignature() {
        Engine<HeaderInfo, ListResult<HeaderInfo>> engine =
                EngineBuilder.headers(new EchoDatabase(), resolver(), SignatureTest::duckdb)
                        .signature("koryki")
                        .build();

        String reached = echoed(engine.executeKQL(CUSTOMERS, ListResult::new));

        assertTrue(reached.contains("-- koryki"), reached);
    }

    @Test
    void withInfoAndWithSignatureEachKeepTheOtherSetting() {
        Engine<HeaderInfo, ListResult<HeaderInfo>> engine =
                new Engine<>(new EchoDatabase(), resolver(), SignatureTest::duckdb, HeaderInfo::new)
                        .withSignature("koryki");

        Engine<HeaderInfo, ListResult<HeaderInfo>> reInfoed =
                engine.withInfo(t -> t.infos(HeaderInfo::new));
        assertEquals("koryki", reInfoed.getSignature());

        Engine<HeaderInfo, ListResult<HeaderInfo>> reSigned = engine.withSignature("other");
        assertEquals(1, reSigned.analyze(CUSTOMERS).size());
    }
}
