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
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.KQLTranspiler;
import ai.koryki.trino.iql.SqlQueryRenderer;
import ai.koryki.trino.iql.TrinoDialect;
import java.time.ZoneId;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The SQL of selectors and lambdas on Trino, from KQL through the whole transpiler on its catalog
 * of the complex bed. No connection is needed; what the SQL returns is the business of {@link
 * ComplexTrinoEngineTest}.
 */
class TrinoSelectionTest {

    private static final LinkResolver RESOLVER =
            new LinkResolver(
                    Locale.ENGLISH,
                    CatalogLoader.db("/ai/koryki/trino/databases/complex"),
                    CatalogLoader.model("/ai/koryki/trino/databases/complex/model", Locale.ENGLISH),
                    true);

    private static String sql(String expression) {
        String sql =
                KQLTranspiler.builder("FIND check_complex t FETCH " + expression, RESOLVER)
                        .functions(TrinoDialect.INSTANCE.getFunctionRenderer())
                        .build()
                        .getSql(new SqlQueryRenderer(ZoneId.of("UTC")));
        return sql.substring(sql.indexOf("SELECT") + "SELECT".length(), sql.lastIndexOf("\nFROM"))
                .strip();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                // lists: element_at counts from 1, from the end when negative, and is NULL outside
                "t.tags@1|element_at(t.tags, 1)",
                "t.tags@-1|element_at(t.tags, -1)",
                "t.matrix@2@1|element_at(element_at(t.matrix, 2), 1)",
                // ranges are slice(list, start, length): inclusive bounds, open ends
                "t.tags@1:2|slice(t.tags, 1, 2)",
                "t.tags@2:|slice(t.tags, 2, greatest(cardinality(t.tags) - (2) + 1, 0))",
                "t.tags@:2|slice(t.tags, 1, greatest(2 - (1) + 1, 0))",
                // a start before the list is the first element, not 0 (an error) or negative (the
                // end)
                "t.tags@-5:|slice(t.tags, greatest(cardinality(t.tags) + -4, 1), greatest(cardinality(t.tags) - (greatest(cardinality(t.tags) + -4, 1)) + 1, 0))",
                // rows: a field by name or by position, in parentheses
                "t.dims@w|(t.dims).w",
                "t.dims@1|(t.dims)[1]",
                "t.dims@-1|(t.dims)[2]",
                "t.address@geo@lat|((t.address).geo).lat",
                "t.odd@'ODD NAME'|(t.odd).\"Odd Name\"",
                // maps: a text key and an integer key, NULL for a key the map does not have
                "t.attrs@color|element_at(t.attrs, 'color')",
                "t.lookup@1|element_at(t.lookup, 1)",
                // chains through every kind
                "t.deep@1@m@b@1@1|element_at(element_at((element_at(element_at(t.deep, 1), 'm')).b, 1), 1)",
                "t.items@1@opts@2|element_at((element_at(t.items, 1)).opts, 2)",
                // JSON: one path, counted from 0
                "t.doc@a@b@1|json_extract(t.doc, '$.a.b[0]')",
                "t.doc@'a b'|json_extract(t.doc, '$[\"a b\"]')",
            })
    void theShapeOfTheSelection(String expression, String expected) {
        String actual = sql(expression);

        assertTrue(actual.contains(expected), () -> expected + " not in: " + actual);
    }

    @Test
    void aQuoteInAKeyStaysInsideItsLiteral() {
        String actual = sql("t.attrs@'it\\'s'");

        assertTrue(actual.contains("element_at(t.attrs, 'it''s')"), actual);
    }

    @Test
    void aJsonPathHasNoPositionFromTheEnd() {
        KorykiaiException e = assertThrows(KorykiaiException.class, () -> sql("t.doc@-1"));

        assertTrue(e.getMessage().contains("no position from the end"), e.getMessage());
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "list_transform(t.tags, x -> upper(x))|transform(t.tags, _l0_x -> upper(_l0_x))",
                "list_filter(t.scores, x -> x > 10)|filter(t.scores, _l0_x -> _l0_x > 10)",
                // the position: the list zipped with 1..n, cut to the length for an empty list
                "list_transform(t.scores, (x, i) -> x * i)|zip_with(t.scores, slice(sequence(1, greatest(cardinality(t.scores), 1)), 1, cardinality(t.scores)), (_l0_x, _l0_i) -> _l0_x * _l0_i)",
                // reduce starts with the first element, as in DuckDB
                "list_reduce(t.scores, (a, b) -> a + b)|reduce(slice(t.scores, 2, cardinality(t.scores)), element_at(t.scores, 1), (_l0_a, _l0_b) -> _l0_a + _l0_b, s -> s)",
                // a step on the parameter: a row field, in parentheses
                "list_transform(t.items, it -> it@price)|transform(t.items, _l0_it -> (_l0_it).price)",
                "list_transform(t.scores, x -> list_transform(t.scores, y -> x + y))|transform(t.scores, _l0_x -> transform(t.scores, _l1_y -> _l0_x + _l1_y))",
            })
    void theShapeOfTheLambda(String expression, String expected) {
        String actual = sql(expression);

        assertTrue(actual.contains(expected), () -> expected + " not in: " + actual);
    }

    @Test
    void theFilterTakesNoPosition() {
        KorykiaiException e =
                assertThrows(
                        KorykiaiException.class,
                        () -> sql("list_filter(t.scores, (x, i) -> x > i)"));

        assertTrue(e.getMessage().contains("not supported on Trino"), e.getMessage());
    }

    private static String nestedTransforms(int depth) {
        String list = "t.scores";
        for (int i = 0; i < depth; i++) {
            list = "list_transform(" + list + ", (x, i) -> x + i)";
        }
        return list;
    }

    @Test
    void nestingDoesNotMultiplyTheSql() {
        int three = sql(nestedTransforms(3)).length();
        int six = sql(nestedTransforms(6)).length();

        // twice the depth, about twice the SQL; a list named three times at every level would
        // make it twenty-seven times
        assertTrue(six < three * 2.6, () -> three + " characters at depth 3, " + six + " at 6");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
            delimiter = '|',
            value = {
                "to_integer(t.doc@n)|CAST(json_extract_scalar(t.doc, '$.n') AS INTEGER)",
                "to_text(t.doc@a@b@1)|json_extract_scalar(t.doc, '$.a.b[0]')",
                "to_text(t.doc)|json_extract_scalar(t.doc, '$')",
            })
    void aJsonValueIsCastAsTheScalarItHolds(String expression, String expected) {
        String actual = sql(expression);

        org.junit.jupiter.api.Assertions.assertTrue(
                actual.contains(expected), () -> expected + " not in: " + actual);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
            delimiter = '|',
            value = {
                "list_size(t.tags)|cardinality(t.tags)",
                "list_contains(t.tags, 'red')|(array_position(t.tags, 'red') > 0)",
                "list_sum(t.scores)|(SELECT sum(_e) FROM UNNEST(t.scores) AS _u(_e))",
                "list_max(t.scores)|(SELECT max(_e) FROM UNNEST(t.scores) AS _u(_e))",
                "list_avg(t.scores)|avg(CAST(_e AS DOUBLE))",
            })
    void aListAnswersAboutItself(String expression, String expected) {
        String actual = sql(expression);

        org.junit.jupiter.api.Assertions.assertTrue(
                actual.contains(expected), () -> expected + " not in: " + actual);
    }
}
