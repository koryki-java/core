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
package ai.koryki.sqlite.complex;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.CatalogLoader;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.KQLTranspiler;
import ai.koryki.sqlite.iql.SqlQueryRenderer;
import ai.koryki.sqlite.iql.SqliteDialect;
import java.time.ZoneId;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The SQL of selectors on SQLite, whose one nested value is JSON. No connection is needed; what the
 * SQL returns is the business of {@link ComplexSqliteEngineTest}.
 */
class SqliteSelectionTest {

    private static final LinkResolver RESOLVER =
            new LinkResolver(
                    Locale.ENGLISH,
                    CatalogLoader.db(ComplexCatalogTest.DB),
                    CatalogLoader.model(ComplexCatalogTest.MODEL, Locale.ENGLISH),
                    true);

    private static String sql(String expression) {
        String sql =
                KQLTranspiler.builder("FIND check_complex t FETCH " + expression, RESOLVER)
                        .functions(SqliteDialect.INSTANCE.getFunctionRenderer())
                        .build()
                        .getSql(new SqlQueryRenderer(ZoneId.of("UTC")));
        return sql.substring(sql.indexOf("SELECT") + "SELECT".length(), sql.lastIndexOf("\nFROM"))
                .strip();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                // one -> path, counted from 0, # for the end; JSON text, so a string keeps its
                // quotes
                "t.doc@n|(t.doc -> '$.n')",
                "t.doc@a@b@1|(t.doc -> '$.a.b[0]')",
                "t.doc@-1|(t.doc -> '$[#-1]')",
                "t.doc@-3|(t.doc -> '$[#-3]')",
                "t.doc@'a b'|(t.doc -> '$.\"a b\"')",
            })
    void theShapeOfTheSelection(String expression, String expected) {
        String actual = sql(expression);

        assertTrue(actual.contains(expected), () -> expected + " not in: " + actual);
    }

    @Test
    void aQuoteInAKeyStaysInsideItsLiteral() {
        String actual = sql("t.doc@'it\\'s'");

        assertTrue(actual.contains("'$.\"it''s\"'"), actual);
    }

    @Test
    void onlyJsonIsANestedValue() {
        KorykiaiException e =
                assertThrows(
                        KorykiaiException.class,
                        () ->
                                SqliteDialect.INSTANCE.selection(
                                        "x",
                                        ai.koryki.catalog.types.TypeDescriptor.array(
                                                new ai.koryki.catalog.types.TypeDescriptor(
                                                        "INTEGER",
                                                        null,
                                                        ai.koryki.catalog.types.CoreTypeFamily
                                                                .INTEGER)),
                                        java.util.List.of()));

        assertTrue(e.getMessage().contains("only JSON"), e.getMessage());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
            delimiter = '|',
            value = {
                "to_integer(t.doc@n)|ROUND((t.doc ->> '$.n'), 0)",
                "to_text(t.doc)|json_extract(t.doc, '$')",
            })
    void aJsonValueIsCastAsTheScalarItHolds(String expression, String expected) {
        String actual = sql(expression);

        org.junit.jupiter.api.Assertions.assertTrue(
                actual.contains(expected), () -> expected + " not in: " + actual);
    }
}
