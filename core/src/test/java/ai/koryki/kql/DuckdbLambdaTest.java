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

import ai.koryki.databases.complex.duckdb.ComplexService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.functions.StandardFunctions;
import ai.koryki.iql.validate.ValidateException;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The SQL of lambdas on DuckDB, from KQL through the whole transpiler on the complex test bed. The
 * values are for the engine tests ({@code queries/kql/complex/lambda_*.kql}); this pins the shape,
 * and above all that a parameter never has the name its author gave it.
 */
class DuckdbLambdaTest {

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
                "list_transform(t.tags, x -> upper(@x))|list_transform(t.tags, _l0_x -> upper(_l0_x))",
                // two parameters are parenthesised
                "list_transform(t.scores, (x, i) -> @x * @i)|list_transform(t.scores, (_l0_x, _l0_i) -> _l0_x * _l0_i)",
                "list_reduce(t.scores, (a, b) -> @a + @b)|list_reduce(t.scores, (_l0_a, _l0_b) -> _l0_a + _l0_b)",
                // a predicate body
                "list_filter(t.scores, x -> @x > 2 AND @x < 9)|list_filter(t.scores, _l0_x -> (_l0_x > 2 AND _l0_x < 9))",
                // a step on a parameter: bracket form, never the dot that a table alias could take
                "list_transform(t.items, it -> @it@price)|list_transform(t.items, _l0_it -> _l0_it['price'])",
                // nesting numbers the parameters by depth, so an inner one never hides an outer one
                "list_transform(t.scores, x -> list_transform(t.scores, y -> @x + @y))|list_transform(t.scores, _l0_x -> list_transform(t.scores, _l1_y -> _l0_x + _l1_y))",
                // a column of the query inside the body is itself
                "list_transform(t.scores, x -> @x + t.nr)|_l0_x + t.nr",
                // a selector on the result
                "list_transform(t.tags, x -> @x)@1|list_transform(t.tags, _l0_x -> _l0_x)[1]",
            })
    void theShapeOfTheSql(String expression, String expected) {
        String actual = sql(expression);

        assertTrue(actual.contains(expected), () -> expected + " not in: " + actual);
    }

    @Test
    void aParameterNamedLikeAColumnDoesNotTakeTheColumn() {
        // nr is a column of the table and also the name of the parameter: a bare nr in the SQL of
        // the lambda would be the column, so the parameter is written under a name of its own
        String actual = sql("list_transform(t.items, nr -> @nr@price * t.nr)");

        assertTrue(actual.contains("_l0_nr['price'] * t.nr"), actual);
    }

    @Test
    void aParameterNamedLikeAnAliasIsRefusedBeforeItIsRendered() {
        ValidateException e =
                assertThrows(
                        ValidateException.class,
                        () -> sql("list_transform(t.items, t -> @t@price)"));

        assertTrue(e.getMessage().contains("has the name of the alias t"), e.getMessage());
    }

    @Test
    void aDialectWithoutLambdasRejectsTheCall() {
        ValidateException e =
                assertThrows(
                        ValidateException.class,
                        () ->
                                KQLTranspiler.builder(
                                                "FIND check_complex t FETCH list_transform(t.tags, x -> @x)",
                                                ComplexService.resolver())
                                        .functions(StandardFunctions.registry())
                                        .build()
                                        .getSql(
                                                new SqlQueryRenderer(
                                                        DuckdbBaseDialect.INSTANCE,
                                                        ZoneId.of("UTC"))));

        assertTrue(String.valueOf(e.getMessage()).contains("list_transform"), e.getMessage());
    }
}
