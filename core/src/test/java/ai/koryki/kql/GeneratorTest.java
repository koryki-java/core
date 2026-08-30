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

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.validate.Violation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The claim of this class: writing and checking work without a database.
 *
 * <p>There is no {@code Database} here, no connection and no driver -- and still every method
 * answers. That was exactly what used to be impossible: the same methods sat on {@link Engine}, and
 * an Engine was only available to whoever could produce a {@code Database}.
 *
 * <p>Worth noting in passing: before this file, not a single test in {@code :core} exercised the
 * engine level. The 32 dialect suites reach it only through a real database, so what has to work
 * without one was written down nowhere.
 */
public class GeneratorTest {

    private static Generator<HeaderInfo> generator;

    @BeforeAll
    public static void buildWithoutADatabase() {

        generator = new Generator<>(NorthwindService.resolver(),
                new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")),
                HeaderInfo::new);
    }

    @Test
    void aValidQueryHasNoErrors() {
        assertTrue(generator.validateKQL("FIND customers c FETCH c.company_name").isEmpty());
    }

    @Test
    void anUnknownColumnIsAnError() {
        List<Violation> errors = generator.validateKQL("FIND customers c FETCH c.nosuch_column");

        assertAll(
                () -> assertFalse(errors.isEmpty(), "an unknown column must not pass as valid"),
                () -> assertTrue(errors.get(0).isError(), errors.toString()),
                () -> assertTrue(errors.toString().contains("nosuch_column"), errors.toString()));
    }

    /**
     * An unknown function is a warning and not an error.
     *
     * <p>That difference is the reason {@code validateKQL} returns {@code errors()} and not
     * {@code violations()}: callers read an empty list as "valid", and a warning must not feel like
     * a failure.
     */
    @Test
    void anUnknownFunctionOnlyWarns() {
        String kql = "FIND customers c FETCH nonsense(1) x";

        assertAll(
                () -> assertTrue(generator.validateKQL(kql).isEmpty(), "no error"),
                () -> assertFalse(generator.warningsKQL(kql).isEmpty(), "but a warning"));
    }

    @Test
    void theTranslationYieldsSql() {
        String sql = generator.toSql("FIND customers c FETCH c.company_name");

        assertAll(
                () -> assertTrue(sql.toUpperCase().contains("SELECT"), sql),
                () -> assertTrue(sql.contains("company_name"), sql));
    }

    @Test
    void theFormatterReturnsTheQueryReadable() {
        String formatted = generator.formatKQL("FIND customers c FETCH c.company_name");

        assertAll(
                () -> assertTrue(formatted.contains("FIND"), formatted),
                () -> assertTrue(formatted.contains("FETCH"), formatted),
                () -> assertTrue(formatted.contains("company_name"), formatted));
    }

    /**
     * Formatting also works for a query the catalog would reject.
     *
     * <p>That is why {@code formatKQL} builds its transpiler without a function catalog and without
     * a dialect and deliberately does not use {@code transpiler(kql)}: the faulty query is
     * precisely the one you want to see formatted, in order to find the mistake.
     */
    @Test
    void aQueryWithAnUnknownFunctionCanStillBeFormatted() {
        String formatted = generator.formatKQL("FIND customers c FETCH nonsense(1) x");

        assertTrue(formatted.contains("nonsense"), formatted);
    }

    @Test
    void theAnalysisNamesEveryOutputColumn() {
        List<HeaderInfo> infos =
                generator.analyze("FIND customers c FETCH c.company_name, c.contact_name");

        assertEquals(2, infos.size(), String.valueOf(infos));
    }

    /**
     * An Engine is a Generator.
     *
     * <p>That is the purpose of the inheritance and not merely its consequence: whoever only wants
     * to validate asks for a {@code Generator} and is handed an Engine without unwrapping it.
     */
    @Test
    void anEngineIsAGenerator() {
        assertTrue(Generator.class.isAssignableFrom(Engine.class));
    }
}
