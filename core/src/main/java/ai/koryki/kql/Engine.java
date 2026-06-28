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
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.SqlRenderer;

import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.jdbc.Format;
import ai.koryki.jdbc.ResultConsumer;
import ai.koryki.jdbc.Database;

import java.sql.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Engine<I extends ColumnInfo, C extends ResultConsumer<I>> {

    private final Database<C> database;
    private final LinkResolver resolver;
    private final SqlRenderer renderer;

    private Function<KQLTranspiler, List<I>> info;

    /** One result-set Format, applied to every processor; null keeps the legacy ColumnInfo.toString path. */
    private Format format;

    public static <C extends ResultConsumer<HeaderInfo>> Engine<HeaderInfo, C> build(
            Database<C> database, LinkResolver resolver, SqlRenderer renderer) {

        Function<KQLTranspiler, List<HeaderInfo>> info = getInfo(resolver);
        return new Engine<>(database, resolver, renderer, info);
    }

    /**
     * Fluent assembly for the common {@link HeaderInfo} case: the column-info
     * supplier defaults to {@code HeaderInfo::new}, and {@link Format} is set
     * at build time. See {@link EngineBuilder}.
     */
    public static <C extends ResultConsumer<HeaderInfo>> EngineBuilder<HeaderInfo, C> builder(
            Database<C> database, LinkResolver resolver, SqlRenderer renderer) {

        return new EngineBuilder<HeaderInfo, C>(database, resolver, renderer).info(HeaderInfo::new);
    }

    public static Function<KQLTranspiler, List<HeaderInfo>> getInfo(LinkResolver resolver) {

        return getInfo(resolver, HeaderInfo::new);
    }

    public static <I extends ColumnInfo> Function<KQLTranspiler, List<I>> getInfo(LinkResolver resolver, Supplier<I> supplier) {
        return t -> t.infos(supplier);
    }

    public Engine(Database<C> database, LinkResolver resolver, SqlRenderer renderer,
                  Supplier<I> supplier) {

        this(database, resolver, renderer, getInfo(resolver, supplier));
    }


    public Engine(Database<C> database, LinkResolver resolver, SqlRenderer renderer,
                  Function<KQLTranspiler, List<I>> info) {
        this.database = database;
        this.resolver = resolver;
        this.renderer = renderer;
        this.info = info;
    }

    public <P extends C> P executeSQL(String sql, Supplier<P> processor)  {
        return executeSQL(sql, processor, (statement) -> {});
    }

    public <P extends C> P executeSQL(String sql, Supplier<P> processor, Consumer<Statement> stmtConsumer) {
        try (P p = processor.get()) {

            if (format != null) {
                p.setFormat(format);
            }

            execute(sql, p, stmtConsumer);
            return p;
        }
    }

    private <P extends C> void execute(String sql, P p, Consumer<Statement> stmtConsumer) {
        database.execute(sql, s -> {
            stmtConsumer.accept(s);
            database.execute(s, p);
        });
    }

    public C executeKQL(String kql, Supplier<C> processor) {
        return executeKQL(kql, processor, (c) -> {});
    }

    public C executeKQL(String kql, Supplier<C> processor, Consumer<Statement> stmtConsumer) {

        KQLTranspiler transpiler = KQLTranspiler.builder(kql, resolver).functions(renderer.getFunctionRenderer()).build();

        String sql = transpiler.getSql(renderer);

        C p = processor.get();

        p.setSql(sql);

        if (info != null) {
            p.setInfos(info.apply(transpiler));
        }

        if (format != null) {
            p.setFormat(format);
        }

        execute(sql, p, stmtConsumer);
        return p;
    }

    public String toSql(String kql) {
        KQLTranspiler transpiler = KQLTranspiler.builder(kql, resolver).functions(renderer.getFunctionRenderer()).build();
        return transpiler.getSql(renderer);
    }

    /** Validates without executing; returns the violations (empty = valid). Parse errors still throw. */
    public List<ai.koryki.iql.validate.Violation> validateKQL(String kql) {
        KQLTranspiler transpiler = KQLTranspiler.builder(kql, resolver).functions(renderer.getFunctionRenderer()).build();
        return transpiler.violations();
    }

    /** Pretty-prints the KQL (the former behavior of validateKQL). */
    public String formatKQL(String kql) {
        KQLTranspiler transpiler = KQLTranspiler.builder(kql, resolver).build();

        KQLFormatter formatter = new KQLFormatter(transpiler.getCtx(), transpiler.getDescription());
        return formatter.format();
    }

    public Database<C> getDatabase() {
        return database;
    }

    public LinkResolver getResolver() {
        return resolver;
    }

    public SqlRenderer getRenderer() {
        return renderer;
    }

    public void setInfo(Function<KQLTranspiler, List<I>> info) {
        this.info = info;
    }

    public Format getFormat() {
        return format;
    }

    public Engine<I, C> setFormat(Format format) {
        this.format = format;
        return this;
    }

}
