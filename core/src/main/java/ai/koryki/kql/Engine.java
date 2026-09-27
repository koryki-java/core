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
import ai.koryki.jdbc.ListResult;
import ai.koryki.jdbc.ResultConsumer;
import ai.koryki.jdbc.ValueFormat;
import java.sql.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A {@link Generator} that also executes its queries.
 *
 * <p>The split runs along the one field that tells them apart: {@code database}. Validating,
 * formatting, translating and analyzing live in {@link Generator} and manage without a connection;
 * what needs one lives here.
 *
 * <p><b>Why inheritance and not delegation.</b> The inherited methods have always been part of
 * Engine's public surface, and there are callers for most of them -- they must stay resolvable on
 * an Engine. As a superclass that costs no line; as a held field it costs ten forwarding methods.
 * Above all, only inheritance carries the purpose of the split outward: whoever asks for a {@code
 * Generator} accepts an Engine without unwrapping it.
 *
 * <p>Nothing is overridden, and every Generator operation is correct on an Engine -- this is
 * specialization, not reuse by inheritance.
 *
 * <p><b>Shareable, if built from a supplier.</b> Every query is rendered on a renderer made for it
 * -- see {@link Generator} -- so an engine built from a {@code Supplier<SqlRenderer>} can be used
 * by several threads at once as far as rendering goes. The connection is a separate matter, and the
 * database's to answer: {@code Database.allowsConcurrentExecution}. An engine built from a renderer
 * instance (deprecated) renders every query on that one instance and is for one caller at a time.
 */
public class Engine<I extends ColumnInfo, C extends ResultConsumer<I>> extends Generator<I> {

    private final Database<C> database;

    /**
     * One result-set {@link ValueFormat}, applied to every processor; null keeps the legacy {@code
     * ColumnInfo.toString} path.
     *
     * <p>Stays here and does not move up into {@link Generator}: it is read only by {@link
     * #executeSQL} and {@link #executeKQL}, that is, on the path that does not exist without a
     * database. A generator has no rows it could format -- there this would be a field that is
     * written and never read.
     *
     * <p>Final, and therefore settable only through the constructor: a setter afterwards would mean
     * a caller can forget it, and the result would not be an error but silently the old {@code
     * ColumnInfo.toString} path -- wrongly formatted output without any signal.
     */
    private final ValueFormat valueFormat;

    /**
     * @param renderers makes the renderer for one operation, see {@link Generator}
     */
    public Engine(
            Database<C> database,
            LinkResolver resolver,
            Supplier<? extends SqlRenderer> renderers,
            Supplier<I> supplier) {

        this(database, resolver, renderers, Generator.getInfo(supplier), null);
    }

    /**
     * @param renderers makes the renderer for one operation, see {@link Generator}
     */
    public Engine(
            Database<C> database,
            LinkResolver resolver,
            Supplier<? extends SqlRenderer> renderers,
            Function<KQLTranspiler, List<I>> info) {
        this(database, resolver, renderers, info, null);
    }

    public Engine(
            Database<C> database,
            LinkResolver resolver,
            Supplier<? extends SqlRenderer> renderers,
            Function<KQLTranspiler, List<I>> info,
            ValueFormat valueFormat) {
        this(database, resolver, renderers, info, valueFormat, null);
    }

    public Engine(
            Database<C> database,
            LinkResolver resolver,
            Supplier<? extends SqlRenderer> renderers,
            Function<KQLTranspiler, List<I>> info,
            ValueFormat valueFormat,
            String signature) {
        super(resolver, renderers, info, signature);
        this.database = database;
        this.valueFormat = valueFormat;
    }

    /**
     * @deprecated one renderer for every query this engine will ever render, and so an engine for
     *     one caller at a time. Pass a supplier: {@code () -> new SqlQueryRenderer(dialect, zone)}.
     */
    @Deprecated
    public Engine(
            Database<C> database,
            LinkResolver resolver,
            SqlRenderer renderer,
            Supplier<I> supplier) {

        this(database, resolver, shared(renderer), Generator.getInfo(supplier), null);
    }

    /**
     * @deprecated see {@link #Engine(Database, LinkResolver, SqlRenderer, Supplier)}
     */
    @Deprecated
    public Engine(
            Database<C> database,
            LinkResolver resolver,
            SqlRenderer renderer,
            Function<KQLTranspiler, List<I>> info) {
        this(database, resolver, shared(renderer), info, null);
    }

    /**
     * @deprecated see {@link #Engine(Database, LinkResolver, SqlRenderer, Supplier)}
     * @param valueFormat how values become text, or null for the legacy {@code ColumnInfo.toString}
     *     path
     */
    @Deprecated
    public Engine(
            Database<C> database,
            LinkResolver resolver,
            SqlRenderer renderer,
            Function<KQLTranspiler, List<I>> info,
            ValueFormat valueFormat) {
        this(database, resolver, shared(renderer), info, valueFormat);
    }

    /**
     * The same engine with a different column description -- connection, model, dialect and
     * formatting are kept.
     *
     * <p>The replacement for {@code setInfo}: whoever used to reconfigure someone else's engine now
     * builds a second one. That is the difference that matters -- the one passed in stays
     * untouched, and nobody has to know any more whether someone before them already changed it.
     */
    public Engine<I, C> withInfo(Function<KQLTranspiler, List<I>> info) {
        return new Engine<>(
                database, getResolver(), renderers(), info, valueFormat, getSignature());
    }

    /**
     * The same engine with a different signature -- connection, model, formatting and column
     * description are kept. See {@link Generator#getRenderer()} for what it does with it.
     */
    public Engine<I, C> withSignature(String signature) {
        return new Engine<>(
                database, getResolver(), renderers(), getInfo(), valueFormat, signature);
    }

    /** Runs {@code sql}; works on any {@link Database}, JDBC or not. */
    public <P extends C> P executeSQL(String sql, Supplier<P> processor) {
        return executeSQL(sql, processor, null);
    }

    /**
     * Like {@link #executeSQL(String, Supplier)}, with a hook to configure the JDBC statement
     * (fetch size, timeout) before it runs.
     *
     * @param stmtConsumer applied to the prepared statement; {@code null} for none. It exists only
     *     on a JDBC database -- any other refuses with an {@link UnsupportedOperationException},
     *     because there is no statement for it to see.
     */
    public <P extends C> P executeSQL(
            String sql, Supplier<P> processor, Consumer<Statement> stmtConsumer) {
        try (P p = processor.get()) {

            if (valueFormat != null) {
                p.setValueFormat(valueFormat);
            }

            execute(sql, p, stmtConsumer);
            return p;
        }
    }

    /**
     * The one place that reaches the database. Without a statement hook the JDBC-free entry point
     * is enough -- {@link Database#executeInto} -- and that is what keeps a database that has no
     * statements usable here. With one, the JDBC route is the only one that has a statement to
     * offer.
     */
    private <P extends C> void execute(String sql, P p, Consumer<Statement> stmtConsumer) {
        if (stmtConsumer == null) {
            database.executeInto(sql, p);
            return;
        }
        database.execute(
                sql,
                s -> {
                    stmtConsumer.accept(s);
                    database.execute(s, p);
                });
    }

    /** Transpiles and runs {@code kql}; works on any {@link Database}, JDBC or not. */
    public C executeKQL(String kql, Supplier<C> processor) {
        return executeKQL(kql, processor, null);
    }

    /**
     * Like {@link #executeKQL(String, Supplier)}, with a hook to configure the JDBC statement.
     *
     * @param stmtConsumer applied to the prepared statement; {@code null} for none. JDBC only, see
     *     {@link #executeSQL(String, Supplier, Consumer)}.
     */
    public C executeKQL(String kql, Supplier<C> processor, Consumer<Statement> stmtConsumer) {

        SqlRenderer renderer = getRenderer();
        KQLTranspiler transpiler = transpiler(kql, renderer);

        String sql = transpiler.getSql(renderer);

        C p = processor.get();

        p.setSql(sql);

        if (getInfo() != null) {
            p.setInfos(getInfo().apply(transpiler));
        }

        if (valueFormat != null) {
            p.setValueFormat(valueFormat);
        }

        execute(sql, p, stmtConsumer);
        return p;
    }

    /**
     * Runs SQL and collects the rows. Public on purpose: "execute SQL, return rows" is a general
     * operation an add-on such as {@code VisualiseEngine} needs, without Engine having to know its
     * types for it.
     */
    public ListResult<I> runSql(String sql) {
        ListResult<I> r = new ListResult<>();
        runInto(sql, r);
        return r;
    }

    /**
     * Like {@link #runSql}, but into a result prepared by the caller — the only way to set the
     * TypeDescriptors and the format <em>before</em> decoding. Kept neutral: Engine needs to know
     * nothing about the caller for it.
     */
    public void runInto(String sql, ListResult<I> target) {
        execute(sql, collector(target), null);
    }

    @SuppressWarnings("unchecked")
    private C collector(ListResult<I> result) {
        // A Database invokes only ResultConsumer interface methods
        // (getInfos/metadata/append) on the processor, so a row-collecting
        // ListResult<I> runs safely regardless of the engine's concrete C.
        return (C) result;
    }

    public Database<C> getDatabase() {
        return database;
    }

    public ValueFormat getValueFormat() {
        return valueFormat;
    }
}
