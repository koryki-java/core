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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.databases.complex.duckdb.ComplexService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.JdbcDialect;
import ai.koryki.iql.SqlQueryRenderer;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The SQL of selectors on DuckDB, from KQL through the whole transpiler on the complex test bed.
 * What the SQL returns is the business of the engine tests ({@code
 * queries/kql/complex/select_*.kql}); this pins its shape, and above all the quoting of a key,
 * which comes from the query.
 */
class DuckdbSelectionTest {

    /** The expression of the one FETCH item, as it stands in the SELECT list. */
    private static String sql(String expression) {
        String sql =
                KQLTranspiler.builder(
                                "FIND check_complex t FETCH " + expression,
                                ComplexService.resolver())
                        .functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer())
                        .build()
                        .getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")));
        String select =
                sql.substring(sql.indexOf("SELECT") + "SELECT".length(), sql.indexOf("FROM"));
        return select.strip();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                // lists: brackets, 1-based, negative from the end, ranges inclusive with open ends
                "t.tags@1|t.tags[1]",
                "t.tags@-1|t.tags[-1]",
                "t.tags@1:2|t.tags[1:2]",
                "t.tags@2:|t.tags[2:]",
                "t.tags@:2|t.tags[:2]",
                "t.matrix@2@1|t.matrix[2][1]",
                // structs: a name in brackets, a position by function
                "t.dims@w|t.dims['w']",
                "t.dims@'w'|t.dims['w']",
                "t.dims@1|struct_extract_at(t.dims, 1)",
                "t.dims@-1|struct_extract_at(t.dims, 2)",
                "t.address@geo@lat|t.address['geo']['lat']",
                "t.address@phones@1|t.address['phones'][1]",
                // maps: a text key and an integer key look alike, the type tells them apart
                "t.attrs@color|t.attrs['color']",
                "t.lookup@1|t.lookup[1]",
                // chains through every kind
                "t.deep@1@m@b@1@1|t.deep[1]['m']['b'][1][1]",
                "t.items@1@opts@2|t.items[1]['opts'][2]",
                // JSON: one path, counted from 0, the last as #-1
                "t.doc@a@b@1|(t.doc -> '$.a.b[0]')",
                "t.doc@1|(t.doc -> '$[0]')",
                "t.doc@-1|(t.doc -> '$[#-1]')",
                "t.doc@-2|(t.doc -> '$[#-2]')",
            })
    void theShapeOfTheSql(String expression, String expected) {
        String actual = sql(expression);

        assertTrue(actual.contains(expected), () -> expected + " not in: " + actual);
    }

    @Test
    void aQuoteInAKeyStaysInsideItsLiteral() {
        // the key comes from the query: it must not leave the literal it is put in
        String quote = sql("t.attrs@'it\\'s'");
        assertTrue(quote.contains("t.attrs['it''s']"), quote);

        String hostile = sql("t.attrs@'x\\'];DROP TABLE t;--'");
        assertTrue(hostile.contains("t.attrs['x''];DROP TABLE t;--']"), hostile);
    }

    @Test
    void aJsonKeyIsQuotedForThePathAndForTheLiteral() {
        // a name that is no identifier is a quoted member of the path
        String spaced = sql("t.doc@'a b'");
        assertTrue(spaced.contains("'$.\"a b\"'"), spaced);

        // a double quote is escaped in the path, a single quote doubles in the SQL literal
        String quoted = sql("t.doc@'say \"hi\" it\\'s'");
        assertTrue(quoted.contains("$.\"say \\\"hi\\\" it''s\""), quoted);
    }

    @Test
    void aDialectThatCannotRenderThemSaysSo() {
        KorykiaiException e =
                assertThrows(
                        KorykiaiException.class,
                        () ->
                                KQLTranspiler.builder(
                                                "FIND check_complex t FETCH t.tags@1",
                                                ComplexService.resolver())
                                        .build()
                                        .getSql(
                                                new SqlQueryRenderer(
                                                        JdbcDialect.INSTANCE, ZoneId.of("UTC"))));

        assertTrue(
                e.getMessage().contains("cannot render selectors on ARRAY(TEXT)"), e.getMessage());
    }

    @Test
    void theBaseIsRenderedBeforeItsSteps() {
        assertTrue(sql("coalesce(t.tags, t.tags)@1").startsWith("coalesce(t.tags, t.tags)[1]"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
            delimiter = '|',
            value = {
                "to_integer(t.doc@n)|CAST((t.doc ->> '$.n') AS INTEGER)",
                "to_text(t.doc@a@b@1)|CAST((t.doc ->> '$.a.b[0]') AS TEXT)",
                "to_text(t.doc)|json_extract_string(t.doc, '$')",
            })
    void aJsonValueIsCastAsTheScalarItHolds(String expression, String expected) {
        String actual = sql(expression);

        org.junit.jupiter.api.Assertions.assertTrue(
                actual.contains(expected), () -> expected + " not in: " + actual);
    }
}
