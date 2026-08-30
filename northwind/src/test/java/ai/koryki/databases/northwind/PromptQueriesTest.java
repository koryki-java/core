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
package ai.koryki.databases.northwind;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.validate.Violation;
import ai.koryki.kql.Generator;
import ai.koryki.kql.HeaderInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The queries inside {@code prompt.md} are queries, and they were never treated as any.
 *
 * <p>That file is the KQL guide handed to a language model, and it teaches mostly by example. Three
 * of its examples did not parse or did not resolve, in both languages, and had not since the file
 * was written -- a link named {@code report_to} where the model says {@code reports_to}; a nested
 * query correlating on an alias of the outer query, which is not supported; and a query-block with
 * {@code FILTER} where {@code FETCH} belongs. Nothing caught them, because prose in a jar is never
 * parsed. A model reading the guide copies the shape it is shown, so a broken example is worse than
 * a missing one: it costs a round trip and teaches the wrong thing on the way.
 *
 * <p>Both languages, each against its own model: identifiers are translated, so the German guide
 * says {@code bestellungen} and {@code berichtet_an}, and checking it against the English model
 * would fail it for being German.
 *
 * <p><b>Why formatting is part of the check.</b> {@link Generator#validateKQL} accepted the
 * correlated example -- the aliases are resolved in the formatter, and only there. A test that
 * validated and stopped would have called that query good.
 *
 * <p><b>And why the test lives here.</b> It guards a file this module ships, so it runs before the
 * jar is published rather than in whatever consumes it afterwards. It was first written one
 * repository downstream, where it did catch these three -- but there it could only fail after a
 * broken guide had already been released, and it failed for anyone whose local artifact was older
 * than the fix. A guard on a file belongs with the file.
 */
class PromptQueriesTest {

    /** Northwind's own dialect, the same one every consumer of this catalog renders with. */
    private static Generator<HeaderInfo> generator(String language) {
        return new Generator<>(NorthwindService.resolver(Locale.forLanguageTag(language)),
                new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")),
                HeaderInfo::new);
    }

    static List<Arguments> queriesInTheGuide() {
        List<Arguments> out = new ArrayList<>();
        for (String language : List.of("en", "de")) {
            for (String query : indentedQueries(guide(language))) {
                out.add(Arguments.of(language, withoutChartClause(query)));
            }
        }
        return out;
    }

    /**
     * The query without its chart clause, because this module cannot parse one.
     *
     * <p>Two grammars carry the name {@code ai.koryki.kql.KQLParser}: {@code koryki-kqlcore}, which
     * this module resolves, and {@code koryki-kqlvisualise}, which adds {@code VISUALISE} and lives
     * one layer out -- it is built on top of core, so putting it on this test's classpath would
     * point core at an artifact of its own consumer. That is not a trade worth making for a test.
     *
     * <p>So the chart examples are checked from {@code FIND} down to the last {@code FETCH} and no
     * further. That is where the entity names, the links and the columns are, and every fault this
     * test was written for sat in exactly that half. What goes unchecked here is the chart clause
     * itself -- channels, marks, scales -- and that needs a consumer with the other grammar.
     */
    private static String withoutChartClause(String query) {
        int chart = query.indexOf("\nVISUALISE");
        return chart < 0 ? query : query.substring(0, chart).strip();
    }

    /**
     * From the classpath and not from {@code src/main/resources}: what ships is what is read, and a
     * resource that failed to be packaged should fail this test rather than pass it from disk.
     */
    private static String guide(String language) {
        String path = "/ai/koryki/prompts/northwind/" + language + "/prompt.md";
        try (InputStream in = PromptQueriesTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("no such resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("could not read " + path, e);
        }
    }

    /**
     * The guide indents its queries by four spaces instead of fencing them.
     *
     * <p>Only blocks beginning with {@code FIND} or {@code WITH} are taken. The file also shows
     * fragments -- a single link, a logical expression, a VISUALISE clause with no query above it --
     * and those are rightly invalid on their own.
     */
    private static List<String> indentedQueries(String md) {
        List<String> out = new ArrayList<>();
        StringBuilder block = new StringBuilder();
        // A trailing non-indented line, so the last block is closed like every other one.
        for (String line : (md + "\nEND").split("\n", -1)) {
            if (line.startsWith("    ") && !line.isBlank()) {
                block.append(line, 4, line.length()).append('\n');
            } else if (line.isBlank() && block.length() > 0) {
                block.append('\n');
            } else {
                String query = block.toString().strip();
                if (query.startsWith("FIND") || query.startsWith("WITH")) {
                    out.add(query);
                }
                block.setLength(0);
            }
        }
        return out;
    }

    /** One test per example, so a failure names the example and not the file. */
    @ParameterizedTest(name = "[{0}] {1}")
    @MethodSource("queriesInTheGuide")
    void everyQueryInTheGuideIsValid(String language, String query) {
        Generator<HeaderInfo> generator = generator(language);

        List<Violation> errors = generator.validateKQL(query);
        assertTrue(errors.isEmpty(), () -> query + "\n-- " + errors);

        // See the class comment: the aliases are resolved here, and the correlated example got
        // exactly this far before it threw.
        assertDoesNotThrow(() -> generator.formatKQL(query), query);
    }

    /** There are examples at all -- an extractor that silently found none would pass forever. */
    @Test
    void theGuideContainsExamples() {
        assertFalse(queriesInTheGuide().isEmpty(), "no queries extracted from prompt.md");
        assertTrue(indentedQueries(guide("en")).size() >= 10,
                "suspiciously few English examples: " + indentedQueries(guide("en")).size());
    }
}
