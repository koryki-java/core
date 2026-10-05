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
package ai.koryki.mariadb.complex;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.CatalogLoader;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.KQLTranspiler;
import ai.koryki.mariadb.iql.MariadbDialect;
import ai.koryki.mariadb.iql.SqlQueryRenderer;
import java.time.ZoneId;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The SQL of selectors on MariaDB, whose one nested value is JSON. No connection is needed; what
 * the SQL returns is the business of {@link ComplexMariadbEngineTest}.
 */
class MariadbSelectionTest {

    private static final LinkResolver RESOLVER =
            new LinkResolver(
                    Locale.ENGLISH,
                    CatalogLoader.db(ComplexCatalogTest.DB),
                    CatalogLoader.model(ComplexCatalogTest.MODEL, Locale.ENGLISH),
                    true);

    private static String sql(String expression) {
        String sql =
                KQLTranspiler.builder("FIND check_complex t FETCH " + expression, RESOLVER)
                        .functions(MariadbDialect.INSTANCE.getFunctionRenderer())
                        .build()
                        .getSql(new SqlQueryRenderer(ZoneId.of("UTC")));
        return sql.substring(sql.indexOf("SELECT") + "SELECT".length(), sql.lastIndexOf("\nFROM"))
                .strip();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                // every step guarded by the JSON_TYPE of what it is applied to: a lax path would
                // read an object as a list of one
                "t.doc@n|CASE WHEN JSON_TYPE(JSON_QUERY(t.doc, '$')) = 'OBJECT' THEN COALESCE(JSON_QUERY(t.doc, '$.n'), JSON_VALUE(t.doc, '$.n')) END",
                "t.doc@a@b@1|JSON_TYPE(JSON_QUERY(t.doc, '$.a.b')) = 'ARRAY' THEN COALESCE(JSON_QUERY(t.doc, '$.a.b[0]'), JSON_VALUE(t.doc, '$.a.b[0]'))",
                // from the end
                "t.doc@-1|'$[last]'",
                "t.doc@-3|'$[last-2]'",
                "t.doc@'a b'|'$.\"a b\"'",
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
                                MariadbDialect.INSTANCE.selection(
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

    @Test
    void aQuoteInAKeyStaysInsideTheLiteralOfItsGuardToo() {
        String actual = sql("t.doc@'it\\'s'@x");

        assertTrue(actual.contains("JSON_QUERY(t.doc, '$.\"it''s\"')"), actual);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
            delimiter = '|',
            value = {
                "to_integer(t.doc@n)|COALESCE(JSON_QUERY(t.doc, '$.n'), JSON_VALUE(t.doc, '$.n')) END AS INTEGER)",
                "to_text(t.doc)|CAST(t.doc AS CHAR)",
            })
    void aJsonValueIsCastAsTheScalarItHolds(String expression, String expected) {
        String actual = sql(expression);

        org.junit.jupiter.api.Assertions.assertTrue(
                actual.contains(expected), () -> expected + " not in: " + actual);
    }
}
