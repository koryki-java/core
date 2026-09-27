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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.IQLVisibilityContext;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.SqlRenderer;
import ai.koryki.iql.functions.FunctionRenderer;
import ai.koryki.iql.query.Query;
import ai.koryki.jdbc.Database;
import ai.koryki.jdbc.ListResult;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.antlr.v4.runtime.RuleContext;
import org.junit.jupiter.api.Test;

/**
 * A generator and an engine that are built once and used by everyone: what a renderer supplier is
 * for.
 *
 * <p>A {@link SqlRenderer} keeps what it renders in fields, so two threads on one instance render
 * one query into the other. The claim held here is that this stops mattering once the generator is
 * given the means to make a renderer instead of a renderer: every operation asks for its own and
 * nobody ever shares one. The tests hold it with a renderer that is <em>not</em> safe to share
 * ({@link StatefulRenderer}) and with the real one, because a renderer that happens to be safe
 * proves nothing about the mechanism that is meant to make it not matter.
 *
 * <p>The instance forms are still there, deprecated, and their tests say what they still mean.
 */
@SuppressWarnings("deprecation")
class SharedGeneratorTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final String DB = "/ai/koryki/databases/northwind";
    private static final String MODEL = DB + "/model";

    private static final String CUSTOMERS = "FIND customers c FETCH c.company_name ASC";
    private static final String PRODUCTS = "FIND products p FETCH p.product_name ASC";
    private static final String JOINED =
            "FIND customers c, c orders o FETCH c.country ASC, sum(o.freight) freight";
    private static final List<String> QUERIES = List.of(CUSTOMERS, PRODUCTS, JOINED);

    private static LinkResolver resolver() {
        return new LinkResolver(
                Locale.ENGLISH,
                CatalogLoader.db(DB),
                CatalogLoader.model(MODEL, Locale.ENGLISH),
                true);
    }

    private static SqlRenderer duckdb() {
        return new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, UTC);
    }

    /**
     * A renderer that is not safe to share, and known to be so without a race to hope for: the
     * query being rendered is kept in a field, and between storing it and using it another thread's
     * render writes its own.
     */
    private static final class StatefulRenderer implements SqlRenderer {

        private final SqlRenderer inner = duckdb();
        private volatile Query rendering;

        @Override
        public Rendered toSql(
                LinkResolver resolver,
                IQLVisibilityContext visibility,
                Query query,
                Map<Object, RuleContext> iqlToContext) {
            rendering = query;
            Thread.yield();
            return inner.toSql(resolver, visibility, rendering, iqlToContext);
        }

        @Override
        public FunctionRenderer getFunctionRenderer() {
            return inner.getFunctionRenderer();
        }

        @Override
        public SqlDialect getDialect() {
            return inner.getDialect();
        }
    }

    /** Says which of its instances was asked for what, in order. */
    private static final class TrackingRenderer implements SqlRenderer {

        private final SqlRenderer inner = duckdb();
        private final String name;
        private final List<String> log;

        TrackingRenderer(String name, List<String> log) {
            this.name = name;
            this.log = log;
        }

        @Override
        public Rendered toSql(
                LinkResolver resolver,
                IQLVisibilityContext visibility,
                Query query,
                Map<Object, RuleContext> iqlToContext) {
            log.add(name + ":toSql");
            return inner.toSql(resolver, visibility, query, iqlToContext);
        }

        @Override
        public FunctionRenderer getFunctionRenderer() {
            log.add(name + ":functions");
            return inner.getFunctionRenderer();
        }

        @Override
        public SqlDialect getDialect() {
            log.add(name + ":dialect");
            return inner.getDialect();
        }
    }

    /**
     * A database that keeps nothing: it hands the statement it was given back as the one row of the
     * result. Whoever reads the row knows exactly which SQL reached the database, and it may be
     * called from any number of threads at once.
     */
    private static final class EchoDatabase implements Database<ListResult<HeaderInfo>> {

        @Override
        public void executeInto(String sql, ListResult<HeaderInfo> processor) {
            processor.append(List.<Object>of(sql));
        }

        @Override
        public boolean allowsConcurrentExecution() {
            return true;
        }

        @Override
        public void close() {}
    }

    private static String echoed(ListResult<HeaderInfo> result) {
        return (String) result.getRows().get(0).get(0);
    }

    /** What each query is on its own, with nothing else going on -- the yardstick. */
    private static List<String> alone() {
        Generator<HeaderInfo> generator =
                new Generator<>(resolver(), SharedGeneratorTest::duckdb, HeaderInfo::new);
        List<String> sql = new ArrayList<>();
        for (String kql : QUERIES) {
            sql.add(generator.toSql(kql));
        }
        // the yardstick has to tell the queries apart, or agreeing with it proves nothing
        assertEquals(QUERIES.size(), sql.stream().distinct().count(), sql.toString());
        return sql;
    }

    /**
     * Runs {@code work} for every round of every thread, all released together, and returns what it
     * reported as wrong.
     */
    private static List<String> hammer(int threads, int rounds, Work work) throws Exception {
        List<String> wrong = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> done = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int offset = t;
                done.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    for (int round = 0; round < rounds; round++) {
                                        int q = (round + offset) % QUERIES.size();
                                        try {
                                            String problem = work.run(q);
                                            if (problem != null) {
                                                wrong.add("query " + q + ": " + problem);
                                            }
                                        } catch (RuntimeException e) {
                                            wrong.add("query " + q + " threw " + e);
                                        }
                                    }
                                    return null;
                                }));
            }
            start.countDown();
            for (Future<?> f : done) {
                f.get(2, TimeUnit.MINUTES);
            }
        } finally {
            pool.shutdownNow();
        }
        return wrong;
    }

    @FunctionalInterface
    private interface Work {
        /** Returns what was wrong, or null. */
        String run(int query);
    }

    private static void assertNoneWrong(List<String> wrong, int total) {
        assertTrue(
                wrong.isEmpty(),
                () ->
                        wrong.size()
                                + " of "
                                + total
                                + " results were not the query's own; first:\n"
                                + wrong.get(0));
    }

    // ---- shared, and correct

    @Test
    void oneGeneratorServesManyThreadsBecauseEachOperationHasARendererOfItsOwn() throws Exception {
        List<String> alone = alone();
        Generator<HeaderInfo> shared =
                new Generator<>(resolver(), StatefulRenderer::new, HeaderInfo::new);

        List<String> wrong =
                hammer(
                        8,
                        150,
                        q -> {
                            String sql = shared.toSql(QUERIES.get(q));
                            return alone.get(q).equals(sql) ? null : "rendered as:\n" + sql;
                        });

        assertNoneWrong(wrong, 8 * 150);
    }

    @Test
    void oneEngineServesManyThreadsAndEachQueryReachesTheDatabaseAsItsOwnSql() throws Exception {
        List<String> alone = alone();
        Engine<HeaderInfo, ListResult<HeaderInfo>> shared =
                new Engine<>(
                        new EchoDatabase(), resolver(), StatefulRenderer::new, HeaderInfo::new);

        List<String> wrong =
                hammer(
                        8,
                        150,
                        q -> {
                            String sql = echoed(shared.executeKQL(QUERIES.get(q), ListResult::new));
                            return alone.get(q).equals(sql)
                                    ? null
                                    : "reached the database as:\n" + sql;
                        });

        assertNoneWrong(wrong, 8 * 150);
    }

    /** The class that is actually shipped -- and about which the measurement was made. */
    @Test
    void theRealRendererWorksTheSameWayWhenTheEngineIsBuiltFromASupplier() throws Exception {
        List<String> alone = alone();
        Engine<HeaderInfo, ListResult<HeaderInfo>> shared =
                EngineBuilder.headers(new EchoDatabase(), resolver(), SharedGeneratorTest::duckdb)
                        .build();

        List<String> wrong =
                hammer(
                        8,
                        150,
                        q -> {
                            String sql = echoed(shared.executeKQL(QUERIES.get(q), ListResult::new));
                            return alone.get(q).equals(sql)
                                    ? null
                                    : "reached the database as:\n" + sql;
                        });

        assertNoneWrong(wrong, 8 * 150);
    }

    /**
     * {@code analyze}, {@code validateKQL} and {@code warningsKQL} do not render, but they read the
     * function catalog and the dialect from a renderer, so they take part too.
     *
     * <p>What this holds is the checking path of a shared generator -- resolver, transpiler and
     * column-info function -- under many threads. It cannot tell a shared renderer from a private
     * one: {@link StatefulRenderer} keeps state only while it renders, and these operations do not.
     * The tests above are the ones that can.
     */
    @Test
    void theOperationsThatOnlyCheckCanBeSharedToo() throws Exception {
        Generator<HeaderInfo> shared =
                new Generator<>(resolver(), StatefulRenderer::new, HeaderInfo::new);
        List<Integer> columns = new ArrayList<>();
        for (String kql : QUERIES) {
            columns.add(shared.analyze(kql).size());
        }

        List<String> wrong =
                hammer(
                        8,
                        150,
                        q -> {
                            String kql = QUERIES.get(q);
                            if (!shared.validateKQL(kql).isEmpty()) {
                                return "invalid: " + shared.validateKQL(kql);
                            }
                            if (!shared.warningsKQL(kql).isEmpty()) {
                                return "warned: " + shared.warningsKQL(kql);
                            }
                            int seen = shared.analyze(kql).size();
                            return seen == columns.get(q) ? null : "analysed " + seen + " columns";
                        });

        assertNoneWrong(wrong, 8 * 150);
    }

    // ---- one renderer for one operation

    @Test
    void theSupplierIsNotAskedWhenTheGeneratorIsBuilt() {
        AtomicInteger asked = new AtomicInteger();
        Supplier<SqlRenderer> counting =
                () -> {
                    asked.incrementAndGet();
                    return duckdb();
                };

        new Generator<>(resolver(), counting, HeaderInfo::new);
        new Engine<>(new EchoDatabase(), resolver(), counting, HeaderInfo::new);
        EngineBuilder.headers(new EchoDatabase(), resolver(), counting).build();

        assertEquals(0, asked.get());
    }

    @Test
    void everyOperationAsksForExactlyOneRenderer() {
        AtomicInteger asked = new AtomicInteger();
        Engine<HeaderInfo, ListResult<HeaderInfo>> engine =
                new Engine<>(
                        new EchoDatabase(),
                        resolver(),
                        () -> {
                            asked.incrementAndGet();
                            return duckdb();
                        },
                        HeaderInfo::new);

        engine.toSql(CUSTOMERS);
        assertEquals(1, asked.get(), "toSql");
        engine.executeKQL(CUSTOMERS, ListResult::new);
        assertEquals(2, asked.get(), "executeKQL renders and reads the catalog: still one");
        engine.validateKQL(CUSTOMERS);
        assertEquals(3, asked.get(), "validateKQL");
        engine.warningsKQL(CUSTOMERS);
        assertEquals(4, asked.get(), "warningsKQL");
        engine.analyze(CUSTOMERS);
        assertEquals(5, asked.get(), "analyze");
        engine.getRenderer();
        assertEquals(6, asked.get(), "getRenderer");

        // no rendering, and no catalog: nothing to ask for
        engine.formatKQL(CUSTOMERS);
        engine.executeSQL("SELECT 1", ListResult::new);
        engine.runSql("SELECT 1");
        assertEquals(6, asked.get(), "formatKQL, executeSQL and runSql need no renderer");
    }

    /**
     * The renderer that supplies the function catalog and the dialect is the one that renders --
     * not a second one made in between, which could differ from the first.
     */
    @Test
    void oneOperationUsesOneRendererForCatalogDialectAndRendering() {
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger made = new AtomicInteger();
        Generator<HeaderInfo> generator =
                new Generator<>(
                        resolver(),
                        () -> new TrackingRenderer("r" + made.incrementAndGet(), log),
                        HeaderInfo::new);

        generator.toSql(CUSTOMERS);

        assertEquals(1, made.get());
        assertTrue(log.contains("r1:functions"), log.toString());
        assertTrue(log.contains("r1:dialect"), log.toString());
        assertTrue(log.contains("r1:toSql"), log.toString());
        assertTrue(log.stream().allMatch(e -> e.startsWith("r1:")), log.toString());

        log.clear();
        generator.toSql(CUSTOMERS);
        assertTrue(
                log.stream().allMatch(e -> e.startsWith("r2:")),
                "the next one is a new renderer: " + log);
    }

    @Test
    void getRendererIsANewRendererEachTimeWhenTheGeneratorMakesThem() {
        Generator<HeaderInfo> generator =
                new Generator<>(resolver(), SharedGeneratorTest::duckdb, HeaderInfo::new);

        assertNotSame(generator.getRenderer(), generator.getRenderer());
    }

    @Test
    void withInfoHandsOnTheMeansAndNotARenderer() {
        AtomicInteger asked = new AtomicInteger();
        Engine<HeaderInfo, ListResult<HeaderInfo>> engine =
                new Engine<>(
                        new EchoDatabase(),
                        resolver(),
                        () -> {
                            asked.incrementAndGet();
                            return duckdb();
                        },
                        HeaderInfo::new);

        Engine<HeaderInfo, ListResult<HeaderInfo>> other =
                engine.withInfo(t -> t.infos(HeaderInfo::new));
        assertEquals(0, asked.get(), "making the copy asks for nothing");

        assertNotSame(other.getRenderer(), other.getRenderer());
        assertEquals(2, asked.get(), "and the copy asks the same supplier");
    }

    // ---- what the instance forms still mean

    @Test
    void anInstanceGivenToTheDeprecatedConstructorsIsTheRendererEveryTime() {
        SqlRenderer given = duckdb();
        Generator<HeaderInfo> generator = new Generator<>(resolver(), given, HeaderInfo::new);
        Engine<HeaderInfo, ListResult<HeaderInfo>> engine =
                new Engine<>(new EchoDatabase(), resolver(), given, HeaderInfo::new);

        assertSame(given, generator.getRenderer());
        assertSame(given, generator.getRenderer());
        assertSame(given, engine.getRenderer());
        assertSame(given, engine.withInfo(t -> t.infos(HeaderInfo::new)).getRenderer());
    }

    @Test
    void everyDeprecatedFormStillBuildsAnEngineThatRuns() {
        List<String> alone = alone();
        SqlRenderer given = duckdb();
        EchoDatabase database = new EchoDatabase();
        List<Engine<HeaderInfo, ListResult<HeaderInfo>>> engines =
                List.of(
                        new Engine<>(database, resolver(), given, HeaderInfo::new),
                        new Engine<>(database, resolver(), given, t -> t.infos(HeaderInfo::new)),
                        new Engine<>(
                                database, resolver(), given, t -> t.infos(HeaderInfo::new), null),
                        new EngineBuilder<HeaderInfo, ListResult<HeaderInfo>>(
                                        database, resolver(), given)
                                .info(HeaderInfo::new)
                                .build(),
                        EngineBuilder.headers(database, resolver(), given).build());

        for (int i = 0; i < engines.size(); i++) {
            int form = i;
            Engine<HeaderInfo, ListResult<HeaderInfo>> engine = engines.get(i);
            assertAll(
                    () ->
                            assertEquals(
                                    alone.get(0),
                                    echoed(engine.executeKQL(CUSTOMERS, ListResult::new)),
                                    "engine form " + form),
                    () -> assertSame(given, engine.getRenderer(), "engine form " + form));
        }
        Generator<HeaderInfo> generator =
                new Generator<>(resolver(), given, t -> t.infos(HeaderInfo::new));
        assertEquals(alone.get(0), generator.toSql(CUSTOMERS));
    }

    @Test
    void theSupplierFormsAndTheInstanceFormsRenderTheSameSql() {
        Generator<HeaderInfo> supplied =
                new Generator<>(resolver(), SharedGeneratorTest::duckdb, HeaderInfo::new);
        Generator<HeaderInfo> instance = new Generator<>(resolver(), duckdb(), HeaderInfo::new);

        for (String kql : QUERIES) {
            assertEquals(instance.toSql(kql), supplied.toSql(kql), kql);
        }
        assertNotEquals(supplied.toSql(CUSTOMERS), supplied.toSql(PRODUCTS));
    }

    // ---- what a wrong supplier says

    @Test
    void aSupplierThatReturnsNothingIsNamedWhereItIsUsed() {
        Generator<HeaderInfo> generator = new Generator<>(resolver(), () -> null, HeaderInfo::new);

        NullPointerException e =
                assertThrows(NullPointerException.class, () -> generator.toSql(CUSTOMERS));

        assertTrue(e.getMessage().contains("renderer supplier returned null"), e.getMessage());
    }

    @Test
    void noSupplierAtAllIsRefusedWhenTheGeneratorIsBuilt() {
        Supplier<SqlRenderer> none = null;

        assertAll(
                () ->
                        assertEquals(
                                "renderers",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new Generator<>(
                                                                resolver(), none, HeaderInfo::new))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "renderers",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new Engine<>(
                                                                new EchoDatabase(),
                                                                resolver(),
                                                                none,
                                                                HeaderInfo::new))
                                        .getMessage()),
                () ->
                        assertEquals(
                                "renderers",
                                assertThrows(
                                                NullPointerException.class,
                                                () ->
                                                        new EngineBuilder<
                                                                HeaderInfo, ListResult<HeaderInfo>>(
                                                                new EchoDatabase(),
                                                                resolver(),
                                                                none))
                                        .getMessage()));
    }
}
