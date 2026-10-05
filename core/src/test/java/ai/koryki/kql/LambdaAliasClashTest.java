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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.databases.complex.duckdb.ComplexService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.SqlQueryRenderer;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * A lambda parameter may not be named like an alias in sight: {@code t.price} is a field of the
 * alias and {@code @t@price} a selection from the parameter, and the two must never be taken for
 * each other, whatever the syntax of a parameter is.
 */
class LambdaAliasClashTest {

    private static String sql(String kql) {
        return KQLTranspiler.builder(kql, ComplexService.resolver())
                .functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer())
                .build()
                .getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")));
    }

    private static String refusal(String kql) {
        RuntimeException e = assertThrows(RuntimeException.class, () -> sql(kql));
        return String.valueOf(e.getMessage());
    }

    @Test
    void aParameterNamedLikeTheAliasOfTheSelectIsRefused() {
        String message =
                refusal("FIND check_complex t FETCH t.nr, list_transform(t.scores, t -> @t * 2) d");

        assertTrue(message.contains("the parameter t has the name of the alias t"), message);
    }

    @Test
    void anAliasOfAJoinedSourceCountsToo() {
        String message =
                refusal(
                        "FIND check_complex a, VIA same_check check_complex b"
                                + " FETCH a.nr, list_transform(a.scores, b -> @b) d");

        assertTrue(message.contains("the parameter b has the name of the alias b"), message);
    }

    @Test
    void anAliasOfASelectAroundAnExistsCountsInsideIt() {
        String message =
                refusal(
                        "FIND check_complex t"
                                + " FILTER EXISTS (t VIA same_check check_complex u"
                                + " FILTER list_filter(u.scores, t -> @t > 1)@1 > 0)"
                                + " FETCH t.nr");

        assertTrue(message.contains("the parameter t has the name of the alias t"), message);
    }

    @Test
    void anAliasOfTheExistsItselfCountsToo() {
        String message =
                refusal(
                        "FIND check_complex t"
                                + " FILTER EXISTS (t VIA same_check check_complex u"
                                + " FILTER list_filter(u.scores, u -> @u > 1)@1 > 0)"
                                + " FETCH t.nr");

        assertTrue(message.contains("the parameter u has the name of the alias u"), message);
    }

    @Test
    void aParameterWithAnotherNameIsFine() {
        String sql =
                sql("FIND check_complex t FETCH t.nr, list_transform(t.scores, x -> @x * 2) d");

        assertFalse(sql.isBlank());
    }

    @Test
    void anAliasOfAnotherSelectIsNoClash() {
        // x is an alias in the second select of the set, not where the first lambda stands
        String sql =
                sql(
                        "FIND check_complex t FETCH t.nr, list_transform(t.scores, x -> @x * 2) d"
                                + " UNION FIND check_complex x FETCH x.nr, x.scores d");

        assertFalse(sql.isBlank());
    }
}
