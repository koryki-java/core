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
package ai.koryki.databases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.databases.oraview.OraViewService;
import ai.koryki.databases.temporal.duckdb.TemporalService;
import ai.koryki.databases.typecheck.duckdb.TypecheckService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.SqlRenderer;
import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.jdbc.Database;
import ai.koryki.jdbc.ListResult;
import ai.koryki.kql.Engine;
import ai.koryki.kql.HeaderInfo;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * The four ready-made services, and what each of their entry points does with a renderer.
 *
 * <p>Given the means to make one -- a {@code Supplier} -- the service's engine asks for a renderer
 * for every query, so it holds none and can be shared. Given an instance, which is deprecated, it
 * renders every query on that instance. The services are written out four times and no entry point
 * is shared between them, so each one of the twenty is held here on its own: a copy that lost the
 * supplier on the way to the engine would still compile.
 */
@SuppressWarnings("deprecation")
class ServiceRendererTest {

    private static final Database<ListResult<ColumnInfo>> NO_DATABASE = () -> {};

    private static SqlRenderer duckdb() {
        return new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC"));
    }

    /** Every entry point of every service that takes a supplier, as the engine it produces. */
    private static List<Named> viaSupplier(Supplier<SqlRenderer> renderers) {
        Supplier<ColumnInfo> info = HeaderInfo::new;
        Locale en = Locale.ENGLISH;
        List<Named> engines = new ArrayList<>();
        engines.add(
                named(
                        "NorthwindService.build(db, renderers)",
                        NorthwindService.build(NO_DATABASE, renderers).getEngine()));
        engines.add(
                named(
                        "NorthwindService.build(db, renderers, info)",
                        NorthwindService.build(NO_DATABASE, renderers, info).getEngine()));
        engines.add(
                named(
                        "new NorthwindService(db, renderers, info)",
                        new NorthwindService<>(NO_DATABASE, renderers, info).getEngine()));
        engines.add(
                named(
                        "new NorthwindService(db, renderers, locale, info)",
                        new NorthwindService<>(NO_DATABASE, renderers, en, info).getEngine()));
        engines.add(
                named(
                        "new NorthwindService(db, renderers, resolver, info)",
                        new NorthwindService<>(
                                        NO_DATABASE, renderers, NorthwindService.resolver(), info)
                                .getEngine()));

        engines.add(
                named(
                        "TemporalService.build(db, renderers)",
                        TemporalService.build(NO_DATABASE, renderers).getEngine()));
        engines.add(
                named(
                        "TemporalService.build(db, renderers, info)",
                        TemporalService.build(NO_DATABASE, renderers, info).getEngine()));
        engines.add(
                named(
                        "new TemporalService(db, renderers, info)",
                        new TemporalService<>(NO_DATABASE, renderers, info).getEngine()));
        engines.add(
                named(
                        "new TemporalService(db, renderers, locale, info)",
                        new TemporalService<>(NO_DATABASE, renderers, en, info).getEngine()));
        engines.add(
                named(
                        "new TemporalService(db, renderers, resolver, info)",
                        new TemporalService<>(
                                        NO_DATABASE, renderers, TemporalService.resolver(), info)
                                .getEngine()));

        engines.add(
                named(
                        "TypecheckService.build(db, renderers)",
                        TypecheckService.build(NO_DATABASE, renderers).getEngine()));
        engines.add(
                named(
                        "TypecheckService.build(db, renderers, info)",
                        TypecheckService.build(NO_DATABASE, renderers, info).getEngine()));
        engines.add(
                named(
                        "new TypecheckService(db, renderers, info)",
                        new TypecheckService<>(NO_DATABASE, renderers, info).getEngine()));
        engines.add(
                named(
                        "new TypecheckService(db, renderers, locale, info)",
                        new TypecheckService<>(NO_DATABASE, renderers, en, info).getEngine()));
        engines.add(
                named(
                        "new TypecheckService(db, renderers, resolver, info)",
                        new TypecheckService<>(
                                        NO_DATABASE, renderers, TypecheckService.resolver(), info)
                                .getEngine()));

        engines.add(
                named(
                        "OraViewService.build(db, renderers)",
                        OraViewService.build(NO_DATABASE, renderers).getEngine()));
        engines.add(
                named(
                        "OraViewService.build(db, renderers, info)",
                        OraViewService.build(NO_DATABASE, renderers, info).getEngine()));
        engines.add(
                named(
                        "new OraViewService(db, renderers, info)",
                        new OraViewService<>(NO_DATABASE, renderers, info).getEngine()));
        engines.add(
                named(
                        "new OraViewService(db, renderers, locale, info)",
                        new OraViewService<>(NO_DATABASE, renderers, en, info).getEngine()));
        engines.add(
                named(
                        "new OraViewService(db, renderers, resolver, info)",
                        new OraViewService<>(
                                        NO_DATABASE, renderers, OraViewService.resolver(), info)
                                .getEngine()));
        return engines;
    }

    /** The same twenty, with a renderer instance. */
    private static List<Named> viaInstance(SqlRenderer renderer) {
        Supplier<ColumnInfo> info = HeaderInfo::new;
        Locale en = Locale.ENGLISH;
        List<Named> engines = new ArrayList<>();
        engines.add(
                named(
                        "NorthwindService.build(db, renderer)",
                        NorthwindService.build(NO_DATABASE, renderer).getEngine()));
        engines.add(
                named(
                        "NorthwindService.build(db, renderer, info)",
                        NorthwindService.build(NO_DATABASE, renderer, info).getEngine()));
        engines.add(
                named(
                        "new NorthwindService(db, renderer, info)",
                        new NorthwindService<>(NO_DATABASE, renderer, info).getEngine()));
        engines.add(
                named(
                        "new NorthwindService(db, renderer, locale, info)",
                        new NorthwindService<>(NO_DATABASE, renderer, en, info).getEngine()));
        engines.add(
                named(
                        "new NorthwindService(db, renderer, resolver, info)",
                        new NorthwindService<>(
                                        NO_DATABASE, renderer, NorthwindService.resolver(), info)
                                .getEngine()));

        engines.add(
                named(
                        "TemporalService.build(db, renderer)",
                        TemporalService.build(NO_DATABASE, renderer).getEngine()));
        engines.add(
                named(
                        "TemporalService.build(db, renderer, info)",
                        TemporalService.build(NO_DATABASE, renderer, info).getEngine()));
        engines.add(
                named(
                        "new TemporalService(db, renderer, info)",
                        new TemporalService<>(NO_DATABASE, renderer, info).getEngine()));
        engines.add(
                named(
                        "new TemporalService(db, renderer, locale, info)",
                        new TemporalService<>(NO_DATABASE, renderer, en, info).getEngine()));
        engines.add(
                named(
                        "new TemporalService(db, renderer, resolver, info)",
                        new TemporalService<>(
                                        NO_DATABASE, renderer, TemporalService.resolver(), info)
                                .getEngine()));

        engines.add(
                named(
                        "TypecheckService.build(db, renderer)",
                        TypecheckService.build(NO_DATABASE, renderer).getEngine()));
        engines.add(
                named(
                        "TypecheckService.build(db, renderer, info)",
                        TypecheckService.build(NO_DATABASE, renderer, info).getEngine()));
        engines.add(
                named(
                        "new TypecheckService(db, renderer, info)",
                        new TypecheckService<>(NO_DATABASE, renderer, info).getEngine()));
        engines.add(
                named(
                        "new TypecheckService(db, renderer, locale, info)",
                        new TypecheckService<>(NO_DATABASE, renderer, en, info).getEngine()));
        engines.add(
                named(
                        "new TypecheckService(db, renderer, resolver, info)",
                        new TypecheckService<>(
                                        NO_DATABASE, renderer, TypecheckService.resolver(), info)
                                .getEngine()));

        engines.add(
                named(
                        "OraViewService.build(db, renderer)",
                        OraViewService.build(NO_DATABASE, renderer).getEngine()));
        engines.add(
                named(
                        "OraViewService.build(db, renderer, info)",
                        OraViewService.build(NO_DATABASE, renderer, info).getEngine()));
        engines.add(
                named(
                        "new OraViewService(db, renderer, info)",
                        new OraViewService<>(NO_DATABASE, renderer, info).getEngine()));
        engines.add(
                named(
                        "new OraViewService(db, renderer, locale, info)",
                        new OraViewService<>(NO_DATABASE, renderer, en, info).getEngine()));
        engines.add(
                named(
                        "new OraViewService(db, renderer, resolver, info)",
                        new OraViewService<>(NO_DATABASE, renderer, OraViewService.resolver(), info)
                                .getEngine()));
        return engines;
    }

    private record Named(String entryPoint, Engine<?, ?> engine) {}

    private static Named named(String entryPoint, Engine<?, ?> engine) {
        return new Named(entryPoint, engine);
    }

    @Test
    void everyEntryPointThatTakesASupplierBuildsAnEngineThatMakesARendererForEveryQuery() {
        AtomicInteger made = new AtomicInteger();
        List<Named> engines =
                viaSupplier(
                        () -> {
                            made.incrementAndGet();
                            return duckdb();
                        });

        assertEquals(20, engines.size());
        assertEquals(0, made.get(), "building a service asks for no renderer");

        int asked = 0;
        for (Named entry : engines) {
            assertNotNull(entry.engine(), entry.entryPoint());
            SqlRenderer first = entry.engine().getRenderer();
            SqlRenderer second = entry.engine().getRenderer();
            asked += 2;
            assertNotSame(first, second, entry.entryPoint() + ": a new renderer each time");
            assertEquals(asked, made.get(), entry.entryPoint() + ": asked once for each");
        }
    }

    @Test
    void everyDeprecatedEntryPointStillHandsItsInstanceToTheEngine() {
        SqlRenderer given = duckdb();
        List<Named> engines = viaInstance(given);

        assertEquals(20, engines.size());
        for (Named entry : engines) {
            assertSame(given, entry.engine().getRenderer(), entry.entryPoint());
        }
    }
}
