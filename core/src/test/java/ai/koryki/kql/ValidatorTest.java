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
import ai.koryki.iql.BlockLeadingSourceCollector;
import ai.koryki.iql.BlockRegistryCollector;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.IQLVisibilityContext;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SelectScopeCollector;
import ai.koryki.iql.Walker;
import ai.koryki.iql.query.Block;
import ai.koryki.iql.query.Query;
import ai.koryki.iql.query.Source;
import ai.koryki.iql.validate.FunctionValidator;
import ai.koryki.iql.validate.Violation;
import org.antlr.v4.runtime.RuleContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FunctionValidator} over the {@code validator/} fixtures.
 *
 * <p>Every case runs the validator <em>fully wired</em> — catalog, resolver and visibility context,
 * as {@code Validator} builds it in production. The one-argument constructor these tests used to
 * call leaves all three null, which silently disables `validateCall`, `checkKnownOperator` and
 * every check behind `typeChecks()`; an `isEmpty()` assertion made under it would have held for a
 * query with an unknown function, a wrong-arity call or a cross-family comparison. Constructing the
 * validator the way production does is what makes "no violations" mean something.
 */
public class ValidatorTest {

    private static LinkResolver resolver;

    @BeforeAll
    public static void readNorthwindDB() {

        resolver = NorthwindService.resolver();
    }

    /**
     * Parses a fixture and runs {@link FunctionValidator} against it with the same inputs
     * {@code KQLTranspiler.analyze()} assembles: the dialect's catalog, the schema resolver, and a
     * visibility context derived from the pre-rules tree.
     */
    private static List<Violation> validate(String fixture) throws IOException {
        return validate(ValidatorTest.class.getResourceAsStream("/ai/koryki/core/validator/" + fixture));
    }

    /** As {@link #validate(String)}, for KQL written inline rather than held as a fixture. */
    private static List<Violation> validateSource(String kql) throws IOException {
        return validate(new ByteArrayInputStream(kql.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<Violation> validate(InputStream kql) throws IOException {

        KQLReader r = new KQLReader(kql);
        KQLParser.QueryContext script = r.getCtx();

        KQLQueryMapper l = new KQLQueryMapper(resolver, script, r.getDescription());
        Query query = l.toBean();
        Map<Object, RuleContext> iqlToContext = l.getIqlToContext();

        Map<Object, Map<String, Source>> scopes = Walker.apply(query, new SelectScopeCollector(iqlToContext));
        Map<String, Source> leading = Walker.apply(query, new BlockLeadingSourceCollector());
        Map<String, Block> blocks = Walker.apply(query, new BlockRegistryCollector());
        IQLVisibilityContext visibility = new IQLVisibilityContext(blocks, leading, scopes);

        FunctionValidator v = new FunctionValidator(iqlToContext,
                DuckdbBaseDialect.INSTANCE.getFunctionRenderer(), resolver, visibility);
        new Walker().walk(query, v);
        return v.collect();
    }

    /** Errors only — a warning (an unknown function passed through to SQL) does not make it invalid. */
    private static List<Violation> errors(String fixture) throws IOException {
        return validate(fixture).stream().filter(Violation::isError).toList();
    }

    /**
     * Guards the harness itself. Both of these validated clean under the one-argument constructor —
     * the catalog and the scope context were null, so the checks that would catch them never ran.
     * If this test ever passes vacuously again, the {@code isEmpty()} assertions below have stopped
     * meaning anything.
     */
    @Test
    public void harnessHasTheCatalogAndScopesWired() throws IOException {
        List<Violation> compared = validateSource(
                "FIND orders o FILTER o.order_date = 'not a date' FETCH o.order_id");
        assertTrue(compared.stream().anyMatch(Violation::isError),
                "comparing a DATE against TEXT must be an error — needs resolver + visibility: " + compared);

        List<Violation> unknown = validateSource("FIND orders o FETCH nosuchfunction(o.order_id)");
        assertFalse(unknown.isEmpty(), "an unknown function must be reported — needs the catalog");
    }

    @Test
    public void cycle1() throws IOException {
        assertTrue(errors("cycle_1.kql").isEmpty(), () -> validateMessage("cycle_1.kql"));
    }

    @Test
    public void expression1() throws IOException {
        assertFalse(errors("invalid_expression_1.kql").isEmpty());
    }

    @Test
    public void expression2() throws IOException {
        assertFalse(errors("invalid_expression_2.kql").isEmpty());
    }

    @Test
    public void function1() throws IOException {
        assertFalse(errors("invalid_function_1.kql").isEmpty());
    }

    @Test
    public void function2() throws IOException {
        assertTrue(errors("function_2.kql").isEmpty(), () -> validateMessage("function_2.kql"));
    }

    @Test
    public void function3() throws IOException {
        assertTrue(errors("function_3.kql").isEmpty(), () -> validateMessage("function_3.kql"));
    }

    @Test
    public void function4() throws IOException {
        assertTrue(errors("function_4.kql").isEmpty(), () -> validateMessage("function_4.kql"));
    }

    @Test
    public void function5() throws IOException {
        assertTrue(errors("function_5.kql").isEmpty(), () -> validateMessage("function_5.kql"));
    }

    @Test
    public void function6() throws IOException {
        assertTrue(errors("function_6.kql").isEmpty(), () -> validateMessage("function_6.kql"));
    }

    @Test
    public void function7() throws IOException {
        assertTrue(errors("function_7.kql").isEmpty(), () -> validateMessage("function_7.kql"));
    }

    @Test
    public void function8() throws IOException {
        assertTrue(errors("function_8.kql").isEmpty(), () -> validateMessage("function_8.kql"));
    }

    /** Failure text: which violations actually came back, rather than just "expected true". */
    private static String validateMessage(String fixture) {
        try {
            return fixture + " -> " + validate(fixture);
        } catch (IOException e) {
            return fixture + " -> " + e;
        }
    }
}
