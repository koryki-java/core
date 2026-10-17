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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.IQLQueryMapper;
import ai.koryki.iql.IQLReader;
import ai.koryki.iql.IQLSerializer;
import ai.koryki.iql.IQLTranspiler;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.query.Join;
import ai.koryki.iql.query.Query;
import ai.koryki.iql.query.Source;
import ai.koryki.iql.validate.Violation;
import java.io.IOException;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A source that is a call, {@code o unnest(o.items) i}: mapped into the bean, through IQL and back,
 * written out as KQL again, and refused by {@code SourceCallValidator} where it is not unnest of a
 * list after the source it comes from. What the list is, its element and its SQL come later, so the
 * tests use a plausible column name and neither type nor render.
 */
class UnnestSourceTest {

    private static final String UNNEST =
            "FIND orders o, o unnest(o.items) i FETCH o.order_id, i.sku";

    private static LinkResolver resolver;
    private static Generator<HeaderInfo> generator;

    @BeforeAll
    static void readNorthwind() {
        resolver = NorthwindService.resolver();
        generator =
                new Generator<>(
                        resolver,
                        new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")),
                        HeaderInfo::new);
    }

    private static Query bean(String kql) throws IOException {
        KQLReader reader = new KQLReader(kql, true);
        return new KQLQueryMapper(resolver, reader.getCtx(), reader.getDescription()).toBean();
    }

    private static List<Violation> violations(String kql) {
        return generator.validateKQL(kql);
    }

    // ---- the bean -----------------------------------------------------------------------------

    @Test
    void theCallIsTheSourceAndHasNoName() throws IOException {
        Join join = bean(UNNEST).getSet().getSelect().getJoin().get(0);
        Source source = join.getSource();

        assertNull(source.getName());
        assertEquals("i", source.getAlias());
        assertNotNull(source.getCall());
        assertEquals("unnest", source.getCall().getFunc());
        assertEquals(1, source.getCall().getArguments().size());
        assertTrue(join.getCrit() == null && join.getColumns() == null, "no link is looked up");
    }

    @Test
    void anOuterJoinKeepsItsPlus() throws IOException {
        Join join =
                bean("FIND orders o, o + unnest(o.items) i FETCH o.order_id, i.sku")
                        .getSet()
                        .getSelect()
                        .getJoin()
                        .get(0);

        assertTrue(join.isOptional());
        assertEquals("unnest", join.getSource().getCall().getFunc());
    }

    // ---- IQL and KQL, written out and read back -----------------------------------------------

    @Test
    void theIqlTextSaysTheCallAndReadsBackTheSame() throws IOException {
        String iql = new IQLSerializer(bean(UNNEST)).toString();
        assertTrue(iql.contains("unnest(o.items) i"), iql);

        IQLReader reader = new IQLReader(iql, true);
        Query back = new IQLQueryMapper(reader.getCtx(), reader.getDescription()).toScript();

        assertEquals(iql, new IQLSerializer(back).toString());
        Source source = back.getSet().getSelect().getJoin().get(0).getSource();
        assertNull(source.getName());
        assertEquals("unnest", source.getCall().getFunc());
    }

    @Test
    void theFormatterWritesTheSourceAsItWasTyped() {
        String formatted = generator.formatKQL(UNNEST);

        assertTrue(formatted.contains("o unnest(o.items) i"), formatted);
        assertEquals(formatted, generator.formatKQL(formatted), "stable under formatting");
    }

    @Test
    void theOuterFormIsKeptByTheFormatter() {
        String formatted =
                generator.formatKQL("FIND orders o, o + unnest(o.items) i FETCH o.order_id, i.sku");

        assertTrue(formatted.contains("o + unnest(o.items) i"), formatted);
    }

    @Test
    void anIqlJoinToAnEntityStillNeedsACriterion() throws IOException {
        // the grammar lets the criterion go for the sake of a call; the validator keeps it for
        // everything else
        String iql =
                new IQLSerializer(bean("FIND orders o, o customers c FETCH o.order_id")).toString();
        // JOIN <criterion> <entity> <alias>: drop the criterion
        String withoutCriterion = iql.replaceFirst("JOIN (\\w+) customers c", "JOIN customers c");
        assertTrue(!iql.equals(withoutCriterion), iql);

        IQLTranspiler transpiler = new IQLTranspiler(withoutCriterion, resolver);
        List<Violation> found = transpiler.violations();

        assertEquals(1, found.size(), found.toString());
        assertTrue(found.get(0).getMessage().contains("names no criterion"), found.toString());
    }

    @Test
    void anEntityIsNotLinkedFromTheRowsOfAList() {
        KorykiaiException e =
                assertThrows(
                        KorykiaiException.class,
                        () ->
                                bean(
                                        "FIND orders o, o unnest(o.items) i, i customers c"
                                                + " FETCH o.order_id"));

        assertTrue(e.getMessage().contains("has no link to follow"), e.getMessage());
    }

    @Test
    void theListMustComeFromASourceThatIsThere() {
        KorykiaiException e =
                assertThrows(
                        KorykiaiException.class,
                        () -> bean("FIND orders o, x unnest(o.items) i FETCH o.order_id"));

        assertTrue(e.getMessage().contains("unknown alias 'x'"), e.getMessage());
    }

    // ---- what the validator refuses -----------------------------------------------------------

    @ParameterizedTest
    @CsvSource(
            quoteCharacter = '"',
            delimiter = '|',
            value = {
                "FIND orders o, o explode(o.items) i FETCH o.order_id|'explode' cannot be a source",
                "FIND orders o, o unnest(o.items, o.tags) i FETCH o.order_id|unnest takes one list",
                "FIND orders o, o unnest() i FETCH o.order_id|unnest takes one list",
                "FIND orders o, o unnest(x -> x) i FETCH o.order_id|not a lambda",
                "FIND orders o, o unnest(o) i FETCH o.order_id|not the alias 'o'",
                "FIND orders o, o unnest(o.a > 1) i FETCH o.order_id|not a condition",
                "FIND unnest(o.items) i FETCH i.sku|write it after that source",
                "FIND orders o, o VIA x unnest(o.items) i FETCH o.order_id|takes no VIA",
                "FIND orders o, o [order_id] unnest(o.items) i FETCH o.order_id|takes no VIA",
                "FIND orders o FILTER EXISTS (o unnest(o.items) i) FETCH o.order_id|cannot start an EXISTS",
            })
    void aSourceThatIsNotUnnestOfAListAfterItsSourceIsRefused(String kql, String reason) {
        List<Violation> found = violations(kql);

        assertEquals(1, found.size(), found.toString());
        assertEquals("source", found.get(0).getCategory());
        assertTrue(found.get(0).getMessage().contains(reason), found.get(0).getMessage());
        assertNotNull(found.get(0).getRange(), "the call is pointed at");
    }
}
