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

import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlRenderer;
import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.jdbc.Database;
import ai.koryki.jdbc.ResultConsumer;
import ai.koryki.jdbc.ValueFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Fluent assembly of an {@link Engine} from its three required components (database, model
 * resolver, dialect renderer) and two optionals (the per-row column-info supplier and the result
 * {@link ValueFormat}). It replaces the spread of two constructors, a static factory and a
 * post-construction {@code setFormat}, and yields an engine fully configured at build time — the
 * setters it replaced are gone, so this is now the only way to give an engine a {@link
 * ValueFormat}.
 *
 * <p>Use {@link #headers} for the common {@link HeaderInfo} case; the generic constructor here
 * serves a custom {@link ColumnInfo} via {@link #info}.
 *
 * <p>The renderer is the one component that is not handed over but made: a renderer is not safe for
 * concurrent use ({@link SqlRenderer}), so the builder takes the means to make one, a {@code
 * Supplier}, and the engine asks it for a renderer for every query. What such an engine holds --
 * the link resolver, the supplier, the column-info function -- may be shared, and the database as
 * its {@code allowsConcurrentExecution} says; the engine can therefore be built once. The
 * constructor and {@link #headers} that take a renderer instance are deprecated: that instance is
 * then every query's renderer, and the engine is for one caller at a time.
 */
public final class EngineBuilder<I extends ColumnInfo, C extends ResultConsumer<I>> {

    private final Database<C> database;
    private final LinkResolver resolver;
    private final Supplier<? extends SqlRenderer> renderers;
    private Function<KQLTranspiler, List<I>> info;
    private ValueFormat valueFormat;
    private String signature;

    /**
     * @param renderers makes the renderer for one query, see {@link Generator}
     */
    public EngineBuilder(
            Database<C> database,
            LinkResolver resolver,
            Supplier<? extends SqlRenderer> renderers) {
        this.database = database;
        this.resolver = resolver;
        this.renderers = Objects.requireNonNull(renderers, "renderers");
    }

    /**
     * @deprecated one renderer for every query, and so an engine for one caller at a time. Pass a
     *     supplier: {@code () -> new SqlQueryRenderer(dialect, zone)}.
     */
    @Deprecated
    public EngineBuilder(Database<C> database, LinkResolver resolver, SqlRenderer renderer) {
        this(database, resolver, Generator.shared(renderer));
    }

    /**
     * The common {@link HeaderInfo} case: a builder whose info supplier is already {@code
     * HeaderInfo::new}, so that only {@link #valueFormat} is left to choose.
     *
     * <p>Deliberately here and not on {@link Engine}: Engine is generic over its {@link ColumnInfo}
     * throughout -- no field, no parameter and no return type names an implementation. Only the
     * static factories did, and that made them the single place where Engine knew what {@code I}
     * is. Supplying a customary default is the builder's business.
     */
    public static <C extends ResultConsumer<HeaderInfo>> EngineBuilder<HeaderInfo, C> headers(
            Database<C> database,
            LinkResolver resolver,
            Supplier<? extends SqlRenderer> renderers) {

        return new EngineBuilder<HeaderInfo, C>(database, resolver, renderers)
                .info(HeaderInfo::new);
    }

    /**
     * @deprecated see {@link #EngineBuilder(Database, LinkResolver, SqlRenderer)}
     */
    @Deprecated
    public static <C extends ResultConsumer<HeaderInfo>> EngineBuilder<HeaderInfo, C> headers(
            Database<C> database, LinkResolver resolver, SqlRenderer renderer) {

        return headers(database, resolver, Generator.shared(renderer));
    }

    /** Column metadata from a per-row supplier (the usual case). */
    public EngineBuilder<I, C> info(Supplier<I> supplier) {
        this.info = Generator.getInfo(supplier);
        return this;
    }

    /** Column metadata from a transpiler-aware function (advanced). */
    public EngineBuilder<I, C> info(Function<KQLTranspiler, List<I>> info) {
        this.info = info;
        return this;
    }

    /** Result-set formatting applied to every processor; omit to keep the default. */
    public EngineBuilder<I, C> valueFormat(ValueFormat valueFormat) {
        this.valueFormat = valueFormat;
        return this;
    }

    /**
     * Written as a comment right behind the first {@code SELECT} keyword of every SQL statement the
     * built engine produces from KQL; omit, or pass {@code null}, for no signature. See {@link
     * ai.koryki.iql.SqlRenderer#setSignature} and {@code ai.koryki.iql.SqlQueryRenderer}.
     *
     * <p>What lets whoever operates a downstream system the SQL is posted to -- a database, or an
     * external service such as a hosted MCP server -- trace a statement back to whoever produced
     * it.
     */
    public EngineBuilder<I, C> signature(String signature) {
        this.signature = signature;
        return this;
    }

    public Engine<I, C> build() {
        return new Engine<>(database, resolver, renderers, info, valueFormat, signature);
    }
}
