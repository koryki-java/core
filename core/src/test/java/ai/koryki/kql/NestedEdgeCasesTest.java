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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import ai.koryki.databases.complex.duckdb.ComplexService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.validate.ValidateException;
import ai.koryki.iql.validate.Violation;
import java.time.ZoneId;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The corners of selectors and lambdas that the larger suites do not name: what a literal can be
 * selected from, nested lambdas and captures, a position that is no position, a step into a scalar,
 * a key of the wrong kind. Each line is one expression and either the SQL fragment it renders to or
 * the part of the message it is refused with.
 */
class NestedEdgeCasesTest {

    private static final String QUERY = "FIND check_complex t FETCH ";

    private static String sql(String expression) {
        return KQLTranspiler.builder(QUERY + expression, ComplexService.resolver())
                .functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer())
                .dialect(DuckdbBaseDialect.INSTANCE)
                .build()
                .getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            quoteCharacter = '"',
            value = {
                // a list made in the query is selected from like a column
                "list(1, 2, 3)@2|list_value(1, 2, 3)[2]",
                "list_size(NULL)|len(NULL)",
                // lambdas inside lambdas, and what they capture
                "list_transform(t.items, it -> list_size(it@opts))|len(_l0_it['opts'])",
                "list_transform(t.matrix, r -> list_transform(r, x -> x * 2))|_l1_x * 2",
                "list_transform(t.scores, x -> x + t.nr)|_l0_x + t.nr",
                "list_transform(t.scores, x -> t.tags@1)|_l0_x -> t.tags[1]",
                // selections are values: arithmetic, aggregates, ordering
                "t.dims@w * t.dims@h area|t.dims['w'] * t.dims['h']",
                "count(t.tags@1)|count(t.tags[1])",
                "t.doc@a@b@1 + t.doc@n|CAST((t.doc ->> '$.a.b[0]') AS INTEGER) + CAST((t.doc ->> '$.n') AS INTEGER)",
                // what the catalog declares about JSON survives coalesce, as long as the branches
                // agree
                "coalesce(t.doc, t.doc)@n|CAST((coalesce(t.doc, t.doc) ->> '$.n') AS INTEGER)",
                "coalesce(t.doc@a, t.doc@a)@b@1|CAST((coalesce((t.doc -> '$.a'), (t.doc -> '$.a')) ->> '$.b[0]') AS INTEGER)",
                // branches that declare different things say nothing together: plain JSON again
                "coalesce(t.doc, t.doc@a)@n|(coalesce(t.doc, (t.doc -> '$.a')) -> '$.n')",
                // the same elements in a fixed size and in a list are a list
                "coalesce(t.vec, list_sort(t.vec))|coalesce(t.vec, ",
                // a struct field is found without regard to case when it is written as a name
                "t.dims@'W'|t.dims['W']",
            })
    void anExpressionThatIsFineRendersWhatItShould(String expression, String fragment) {
        String sql = sql(expression);

        assertTrue(sql.contains(fragment), () -> fragment + " not in: " + sql);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            quoteCharacter = '"',
            value = {
                // nothing to select from
                "NULL@1|without a type",
                "'abc'@1|cannot select @1 from TEXT",
                "t.nr@1|cannot select @1 from INTEGER",
                "t.doc@n@x|cannot select @x from INTEGER",
                "t.items@1@opts@1@x|cannot select @x from TEXT",
                // there is no position 0, in a position or in a range
                "t.dims@0|there is no position 0",
                "t.scores@0|there is no position 0",
                "t.scores@1:0|there is no position 0",
                // a key of the other kind
                "t.attrs@1|selects an integer key",
                "t.lookup@name|selects a text key",
                // not a list where a list is wanted
                "list_size(t.doc)|no overload of 'list_size'",
                "list_size(t.doc)|the catalog declares the elements of the array",
                "upper(t.doc)|until it is cast",
                "upper(t.doc@x)|until it is cast",
                "list_transform(t.nr, x -> x)|no overload of 'list_transform'",
                // lists of different elements are not mixed
                "coalesce(t.scores, t.vec)|different structures",
            })
    void anExpressionThatIsWrongIsRefusedAtThePlaceItIsWritten(String expression, String message) {
        try {
            String sql = sql(expression);
            fail("expected a refusal containing '" + message + "', got: " + sql);
        } catch (ValidateException e) {
            String all =
                    e.getViolations().stream()
                            .map(Violation::getMessage)
                            .collect(Collectors.joining(" | "));
            assertTrue(all.contains(message), () -> message + " not in: " + all);
            assertTrue(
                    e.getViolations().stream().allMatch(v -> v.getRange() != null),
                    "every refusal has a position");
        }
    }
}
