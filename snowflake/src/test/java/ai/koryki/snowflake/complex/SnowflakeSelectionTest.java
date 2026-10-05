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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.CatalogLoader;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.KQLTranspiler;
import ai.koryki.snowflake.iql.SnowflakeDialect;
import ai.koryki.snowflake.iql.SqlQueryRenderer;
import java.time.ZoneId;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The SQL of selectors and lambdas on Snowflake, from KQL through the whole transpiler on its
 * catalog of the complex bed. No connection is needed; what the SQL returns is the business of
 * {@link ComplexSnowflakeEngineTest}.
 */
class SnowflakeSelectionTest {

    private static final LinkResolver RESOLVER =
            new LinkResolver(
                    Locale.ENGLISH,
                    CatalogLoader.db(ComplexCatalogTest.DB),
                    CatalogLoader.model(ComplexCatalogTest.MODEL, Locale.ENGLISH),
                    true);

    private static String sql(String expression) {
        String sql =
                KQLTranspiler.builder("FIND check_complex t FETCH " + expression, RESOLVER)
                        .functions(SnowflakeDialect.INSTANCE.getFunctionRenderer())
                        .build()
                        .getSql(new SqlQueryRenderer(ZoneId.of("UTC")));
        return sql.substring(sql.indexOf("SELECT") + "SELECT".length(), sql.lastIndexOf("\nFROM"))
                .strip();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                // lists count from 0: KQL's 1 is GET's 0
                "t.tags@1|GET(t.tags, 0)",
                "t.tags@3|GET(t.tags, 2)",
                // from the end: computed, and NULL when it falls off the front
                "t.tags@-1|CASE WHEN ARRAY_SIZE(t.tags) >= 1 THEN GET(t.tags, ARRAY_SIZE(t.tags) - 1) END",
                "t.matrix@2@1|GET(GET(t.matrix, 1), 0)",
                // ranges: ARRAY_SLICE(list, from, to), 0-based with an exclusive end
                "t.tags@1:2|ARRAY_SLICE(t.tags, 0, 2)",
                "t.tags@2:|ARRAY_SLICE(t.tags, 1, ARRAY_SIZE(t.tags))",
                "t.tags@:2|ARRAY_SLICE(t.tags, 0, 2)",
                "t.tags@-2:|ARRAY_SLICE(t.tags, GREATEST(ARRAY_SIZE(t.tags) - 2, 0), ARRAY_SIZE(t.tags))",
                "t.tags@:-1|ARRAY_SLICE(t.tags, 0, GREATEST(ARRAY_SIZE(t.tags) - 1 + 1, 0))",
                // structs: a field by name, a position becomes the declared name
                "t.dims@w|GET(t.dims, 'w')",
                "t.dims@1|GET(t.dims, 'w')",
                "t.dims@-1|GET(t.dims, 'h')",
                "t.address@geo@lat|GET(GET(t.address, 'geo'), 'lat')",
                "t.odd@'ODD NAME'|GET(t.odd, 'Odd Name')",
                // maps: a text key and an integer key
                "t.attrs@color|GET(t.attrs, 'color')",
                "t.lookup@1|GET(t.lookup, 1)",
                // chains through every kind
                "t.deep@1@m@b@1@1|GET(GET(GET(GET(GET(t.deep, 0), 'm'), 'b'), 1), 0)",
                "t.items@1@opts@2|GET(GET(GET(t.items, 0), 'opts'), 1)",
                // JSON: one path, counted from 0
                "t.doc@a@b@1|GET_PATH(t.doc, 'a.b[0]')",
                "t.doc@'a b'|GET_PATH(t.doc, '\"a b\"')",
            })
    void theShapeOfTheSelection(String expression, String expected) {
        String actual = sql(expression);

        assertTrue(actual.contains(expected), () -> expected + " not in: " + actual);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                // a list that is an expression is evaluated once, in a derived table of one row,
                // where its size is asked for as well as its elements
                "list_filter(t.scores, x -> @x > 5)@-1|FROM (SELECT FILTER(t.scores, _l0_x -> _l0_x > 5) AS L) BO_1",
                "list_filter(t.scores, x -> @x > 5)@-1|GET(BO_1.L, ARRAY_SIZE(BO_1.L) - 1)",
                "coalesce(t.tags, t.tags)@2:|ARRAY_SLICE(BO_1.L, 1, ARRAY_SIZE(BO_1.L))",
                // the position names the list twice, the fold three times
                "list_transform(list_filter(t.scores, x -> @x > 5), (x, i) -> @x * @i)|ARRAY_SIZE(BO_1.L)",
                "list_reduce(list_filter(t.scores, x -> @x > 5), (a, b) -> @a + @b)|REDUCE(ARRAY_SLICE(BO_1.L, 1, ARRAY_SIZE(BO_1.L)), GET(BO_1.L, 0)",
                // a column, and a list read once, are written as they are
                "t.tags@-1|CASE WHEN ARRAY_SIZE(t.tags) >= 1",
                "coalesce(t.tags, t.tags)@2|GET(coalesce(t.tags, t.tags), 1)",
            })
    void anExpressionIsEvaluatedOnceWhereItIsNamedRepeatedly(String expression, String expected) {
        String actual = sql(expression);

        assertTrue(actual.contains(expected), () -> expected + " not in: " + actual);
    }

    @Test
    void aQuoteInAKeyStaysInsideItsLiteral() {
        String actual = sql("t.attrs@'it\\'s'");

        assertTrue(actual.contains("GET(t.attrs, 'it''s')"), actual);
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
                "list_transform(t.tags, x -> upper(@x))|TRANSFORM(t.tags, _l0_x -> upper(_l0_x))",
                "list_filter(t.scores, x -> @x > 10)|FILTER(t.scores, _l0_x -> _l0_x > 10)",
                // the position: the 1-based indexes of the list, the element read back with GET
                "list_transform(t.scores, (x, i) -> @x * @i)|TRANSFORM(ARRAY_GENERATE_RANGE(1, ARRAY_SIZE(t.scores) + 1), _l0_i -> GET(t.scores, _l0_i - 1) * _l0_i)",
                "list_filter(t.scores, (x, i) -> @x > @i)|TRANSFORM(FILTER(ARRAY_GENERATE_RANGE(1, ARRAY_SIZE(t.scores) + 1), _l0_i -> GET(t.scores, _l0_i - 1) > _l0_i), _l0_i -> GET(t.scores, _l0_i - 1))",
                // reduce starts with the first element, as in DuckDB
                "list_reduce(t.scores, (a, b) -> @a + @b)|REDUCE(ARRAY_SLICE(t.scores, 1, ARRAY_SIZE(t.scores)), GET(t.scores, 0), (_l0_a, _l0_b) -> _l0_a + _l0_b)",
                // a step on the parameter: an OBJECT field
                "list_transform(t.items, it -> @it@price)|TRANSFORM(t.items, _l0_it -> GET(_l0_it, 'price'))",
                // a nested lambda that uses only its own parameter
                "list_transform(t.scores, x -> list_transform(t.scores, y -> @y + 1))|TRANSFORM(t.scores, _l0_x -> TRANSFORM(t.scores, _l1_y -> _l1_y + 1))",
            })
    void theShapeOfTheLambda(String expression, String expected) {
        String actual = sql(expression);

        assertTrue(actual.contains(expected), () -> expected + " not in: " + actual);
    }

    @Test
    void aNestedLambdaCannotUseTheParameterAround() {
        KorykiaiException e =
                assertThrows(
                        KorykiaiException.class,
                        () ->
                                sql(
                                        "list_transform(t.scores, x -> list_transform(t.scores, y -> @x + @y))"));

        assertTrue(e.getMessage().contains("parameter of the lambda around it"), e.getMessage());
    }
}
