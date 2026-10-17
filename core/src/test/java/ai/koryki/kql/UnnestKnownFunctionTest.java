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
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.databases.complex.duckdb.ComplexService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionKind;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.StandardFunctions;
import ai.koryki.iql.validate.Violation;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@code unnest} as a known function: of the kind that makes rows, so that it is written as a
 * source and refused in an expression, typed by its argument, and supported where the dialect says
 * so. What its rows are, and their SQL, come later.
 */
class UnnestKnownFunctionTest {

    private static final String SOURCE = "FIND check_complex t, t unnest(t.items) i FETCH t.nr";

    /** The violations on DuckDB, which supports it. */
    private static List<Violation> duckdb(String kql) {
        return KQLTranspiler.builder(kql, ComplexService.resolver())
                .functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer())
                .build()
                .violations();
    }

    /** The violations in the standard catalog, where lists are not supported. */
    private static List<Violation> standard(String kql) {
        return KQLTranspiler.builder(kql, ComplexService.resolver())
                .functions(StandardFunctions.registry())
                .build()
                .violations();
    }

    private static void assertRefused(List<Violation> found, String category, String message) {
        assertEquals(1, found.size(), found.toString());
        assertEquals(category, found.get(0).getCategory());
        assertTrue(found.get(0).getMessage().contains(message), found.get(0).getMessage());
    }

    @Test
    void itIsRegisteredAsAFunctionThatMakesRows() {
        FunctionRegistry registry = StandardFunctions.registry();

        List<FunctionDefinition> overloads = registry.overloads("unnest");

        assertEquals(1, overloads.size());
        FunctionDefinition unnest = overloads.get(0);
        assertEquals(FunctionKind.SOURCE, unnest.getKind());
        assertEquals(FunctionCategory.LIST, unnest.getCategory());
        assertEquals(1, unnest.getSignature().args().size());
        assertEquals("ARRAY", unnest.getSignature().args().get(0).family().name());
    }

    @Test
    void aListMadeIntoRowsValidatesWhereTheDialectHasLists() {
        assertEquals(List.of(), duckdb(SOURCE));
    }

    @Test
    void theOuterFormValidatesToo() {
        assertEquals(List.of(), duckdb("FIND check_complex t, t + unnest(t.items) i FETCH t.nr"));
    }

    @Test
    void aDialectWithoutListsSaysSoAtTheCall() {
        List<Violation> found = standard(SOURCE);

        assertRefused(found, Violation.UNSUPPORTED, "'unnest' is not supported by this dialect");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "FIND check_complex t FETCH unnest(t.items)|makes a list into rows, so it is written as a source",
                "FIND check_complex t FETCH t.nr, list_size(unnest(t.items))|written as a source",
                "FIND check_complex t FILTER unnest(t.tags) = 'red' FETCH t.nr|written as a source",
            })
    void inAnExpressionItIsRefusedWithTheWayItIsWritten(String kql, String message) {
        List<Violation> found = duckdb(kql);

        assertTrue(!found.isEmpty(), "refused");
        assertTrue(
                found.stream().anyMatch(v -> v.getMessage().contains(message)), found.toString());
    }

    @Test
    void whatItIsGivenMustBeAList() {
        List<Violation> found = duckdb("FIND check_complex t, t unnest(t.nr) i FETCH t.nr");

        assertEquals(1, found.size(), found.toString());
        assertTrue(found.get(0).getMessage().contains("unnest"), found.get(0).getMessage());
    }

    @Test
    void theListMayBeWhatAFunctionMade() {
        assertEquals(
                List.of(),
                duckdb(
                        "FIND check_complex t, t unnest(list_filter(t.scores, x -> x > 1)) i"
                                + " FETCH t.nr"));
    }

    @Test
    void whatIsNotRenderedYetIsSaidSoWithoutAStackTrace() {
        try {
            KQLTranspiler.builder(SOURCE, ComplexService.resolver())
                    .functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer())
                    .build()
                    .getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")));
            throw new AssertionError("not rendered yet");
        } catch (ai.koryki.antlr.KorykiaiException e) {
            assertTrue(e.getMessage().contains("not rendered yet"), e.getMessage());
        }
    }
}
