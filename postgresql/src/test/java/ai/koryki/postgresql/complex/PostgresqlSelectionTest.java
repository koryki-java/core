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
package ai.koryki.postgresql.complex;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.CatalogLoader;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.KQLTranspiler;
import ai.koryki.postgresql.iql.PostgreSqlDialect;
import ai.koryki.postgresql.iql.SqlQueryRenderer;
import java.time.ZoneId;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The SQL of selectors and lambdas on PostgreSQL, from KQL through the whole transpiler on its
 * catalog of the complex bed. No connection is needed; what the SQL returns is the business of
 * {@link ComplexPostgreSQLEngineTest}.
 */
class PostgresqlSelectionTest {

    private static final LinkResolver RESOLVER =
            new LinkResolver(
                    Locale.ENGLISH,
                    CatalogLoader.db("/ai/koryki/postgresql/databases/complex"),
                    CatalogLoader.model(
                            "/ai/koryki/postgresql/databases/complex/model", Locale.ENGLISH),
                    true);

    private static String sql(String expression) {
        String sql =
                KQLTranspiler.builder("FIND check_complex t FETCH " + expression, RESOLVER)
                        .functions(PostgreSqlDialect.INSTANCE.getFunctionRenderer())
                        .build()
                        .getSql(new SqlQueryRenderer(ZoneId.of("UTC")));
        // between the SELECT and the FROM of the query itself, which stands alone on its line
        return sql.substring(sql.indexOf("SELECT") + "SELECT".length(), sql.lastIndexOf("\nFROM"))
                .strip();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                // arrays: 1-based like KQL, from the end through the length
                "t.tags@1|t.tags[1]",
                "t.tags@-1|t.tags[array_length(t.tags, 1)]",
                "t.tags@-2|t.tags[array_length(t.tags, 1) - 1]",
                "t.tags@1:2|t.tags[1:2]",
                "t.tags@2:|t.tags[2:]",
                "t.tags@:2|t.tags[:2]",
                "t.tags@-2:-1|t.tags[array_length(t.tags, 1) - 1:array_length(t.tags, 1)]",
                // an array of arrays is one two-dimensional array: every dimension in one subscript
                "t.matrix@2@1|t.matrix[2][1]",
                "t.matrix@-1@-1|t.matrix[array_length(t.matrix, 1)][array_length(t.matrix, 2)]",
                // composites: a name in parentheses, a position by the name the type gives it
                "t.dims@w|(t.dims).w",
                "t.dims@1|(t.dims).w",
                "t.dims@-1|(t.dims).h",
                "t.address@geo@lat|((t.address).geo).lat",
                "t.address@phones@1|((t.address).phones)[1]",
                // the field is written as the type declares it, whatever the query says
                "t.odd@'ODD NAME'|(t.odd).\"Odd Name\"",
                "t.items@1@sku|(t.items[1]).sku",
                "t.items@1@opts@2|((t.items[1]).opts)[2]",
                // jsonb: a path of arrows, 1-based in KQL and 0-based in jsonb
                "t.attrs@color|(t.attrs -> 'color')",
                "t.doc@x@y@1|(t.doc -> 'x' -> 'y' -> 0)",
                "t.doc@-1|(t.doc -> -1)",
            })
    void theShapeOfTheSelection(String expression, String expected) {
        String actual = sql(expression);

        assertTrue(actual.contains(expected), () -> expected + " not in: " + actual);
    }

    @Test
    void aQuoteInAKeyStaysInsideItsLiteral() {
        String actual = sql("t.attrs@'it\\'s'");

        assertTrue(actual.contains("t.attrs -> 'it''s'"), actual);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                // a row of a two-dimensional array is not a row here, a slice has no meaning
                "t.matrix@1|multi-dimensional",
                "t.matrix@1:2|multi-dimensional",
                "t.matrix@1@1:2|multi-dimensional",
            })
    void aMatrixNeedsAPositionInEveryDimension(String expression, String message) {
        KorykiaiException e = assertThrows(KorykiaiException.class, () -> sql(expression));

        assertTrue(e.getMessage().contains(message), e.getMessage());
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "list_transform(t.tags, x -> upper(x))|ARRAY(SELECT upper(_l0_x) FROM (SELECT t.tags[_i] AS _l0_x,"
                        + " _i AS _pos__l0_x FROM generate_subscripts(t.tags, 1) AS _i) AS _u"
                        + " ORDER BY _pos__l0_x)",
                "list_transform(t.scores, (x, i) -> x * i)|SELECT _l0_x * _l0_i FROM (SELECT t.scores[_i] AS _l0_x, _i AS _l0_i",
                "list_filter(t.scores, x -> x > 10)|SELECT _l0_x FROM (SELECT t.scores[_i] AS _l0_x",
                "list_filter(t.scores, x -> x > 10)|WHERE _l0_x > 10",
                // a NULL list stays NULL; an empty subquery would make it an empty array
                "list_transform(t.tags, x -> x)|CASE WHEN t.tags IS NULL THEN NULL ELSE ARRAY(",
                // a composite element arrives whole, and a step on it is a field of the composite
                "list_transform(t.items, it -> it@price)|(_l0_it).price",
                // the result is an array like any other
                "list_transform(t.tags, x -> x)@1|[1]",
            })
    void theShapeOfTheLambda(String expression, String expected) {
        String actual = sql(expression);

        assertTrue(actual.contains(expected), () -> expected + " not in: " + actual);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                // a list that is an expression is evaluated once, in a derived table of one row
                "list_filter(t.scores, x -> x > 5)@-1|FROM (SELECT CASE WHEN t.scores IS NULL",
                "list_filter(t.scores, x -> x > 5)@-1|BO_1.L[array_length(BO_1.L, 1)]",
                "coalesce(t.tags, t.tags)@-2:|array_length(BO_1.L, 1)",
                // a column is written as it is
                "t.tags@-1|t.tags[array_length(t.tags, 1)]",
            })
    void anExpressionIsEvaluatedOnceWhereItIsNamedRepeatedly(String expression, String expected) {
        String actual = sql(expression);

        assertTrue(actual.contains(expected), () -> expected + " not in: " + actual);
    }

    @Test
    void aListOfListsIsRefusedBecauseArraysDoNotNest() {
        KorykiaiException e =
                assertThrows(
                        KorykiaiException.class,
                        () -> sql("list_transform(t.items, it -> it@opts)"));

        assertTrue(e.getMessage().contains("do not nest"), e.getMessage());
    }

    @Test
    void reduceIsRejectedWithWhatToDoInstead() {
        RuntimeException e =
                assertThrows(
                        RuntimeException.class,
                        () -> sql("list_reduce(t.scores, (a, b) -> a + b)"));

        assertTrue(String.valueOf(e.getMessage()).contains("fold over an array"), e.getMessage());
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
                "to_integer(t.doc@n)|CAST((t.doc ->> 'n') AS INTEGER)",
                "to_text(t.doc@a@b@1)|(t.doc -> 'a' -> 'b' ->> 0)",
                "to_text(t.doc)|(t.doc #>> '{}')",
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
                "list_contains(t.tags, 'red')|(t.tags @> ARRAY['red'])",
                "list_sum(t.scores)|(SELECT sum(_e) FROM unnest(t.scores) AS _u(_e))",
                "list_min(t.scores)|(SELECT min(_e) FROM unnest(t.scores) AS _u(_e))",
                "list_avg(t.scores)|avg(_e::double precision)",
            })
    void aListAnswersAboutItself(String expression, String expected) {
        String actual = sql(expression);

        org.junit.jupiter.api.Assertions.assertTrue(
                actual.contains(expected), () -> expected + " not in: " + actual);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(
            delimiter = '|',
            value = {
                "list_sort(t.tags)|ARRAY(SELECT _e FROM unnest(t.tags) AS _u(_e) ORDER BY _e NULLS LAST)",
                "list_sort(t.tags, 'desc')|ORDER BY _e DESC NULLS LAST",
                "list_distinct(t.tags)|SELECT DISTINCT _e FROM unnest(t.tags) AS _u(_e) WHERE _e IS NOT NULL ORDER BY _e",
                "list_concat(t.scores, t.scores)|array_cat(t.scores, t.scores)",
                "list_any(t.scores, x -> x > 5)|cardinality(CASE WHEN t.scores IS NULL",
                "list(1, 2)|ARRAY[1, 2]",
                "list_agg(t.nr)|array_agg(t.nr) FILTER (WHERE t.nr IS NOT NULL)",
            })
    void aListIsShapedAndMade(String expression, String expected) {
        String actual = sql(expression);

        org.junit.jupiter.api.Assertions.assertTrue(
                actual.contains(expected), () -> expected + " not in: " + actual);
    }
}
