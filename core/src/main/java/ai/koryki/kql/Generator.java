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
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Everything a KQL query yields without a database: validate, format, translate to SQL, determine
 * the output columns.
 *
 * <p>The name says what it is for. Whoever <em>writes</em> KQL -- a language model or a human --
 * needs exactly this half and no connection: the loop of writing, checking and correcting runs
 * entirely without a database. {@link Engine} adds execution on top.
 *
 * <p><b>Why this is a class of its own.</b> The five methods here never touched the {@code
 * database} field of {@link Engine}, yet were reachable only through a constructor that demanded a
 * {@code Database}. So anyone who merely wanted to check a query needed an open connection they did
 * not use -- and anyone without one had to fake it. That is the whole reason for the split.
 *
 * <p><b>Final fields.</b> All three fields are final; whoever wants something else builds a second
 * one. That is not cosmetic: until now the engine carried mutable state via {@code setInfo} and
 * {@code setFormat}, so every caller had to rebuild it per call while holding a lock -- a lock that
 * then guarded two entirely different things, the connection and the configuration. A generator has
 * no connection and now no mutable configuration either.
 *
 * <p><b>A renderer for each operation.</b> One of the three fields is not a renderer but the means
 * to make one, a {@code Supplier}, because a {@link SqlRenderer} keeps what it renders in fields
 * and two renders on one instance overwrite each other -- see {@link SqlRenderer}. Every operation
 * asks the supplier for a renderer of its own and drops it when it is done, so a generator built
 * this way <em>can</em> be shared: what it holds is the {@link LinkResolver}, the supplier and the
 * column-info function, none of which changes. That takes a supplier that is itself safe to call
 * from several threads, and a lambda that makes a new renderer is.
 *
 * <p>The constructors that take a renderer instance are deprecated. They still work, but a
 * generator built from one renders every query on that one instance and so is for one caller at a
 * time -- the opposite of "immutable, and therefore shareable", which this class used to say of
 * every generator. Passing a supplier instead costs a lambda.
 */
public class Generator<I extends ColumnInfo> {

    private final LinkResolver resolver;
    private final Supplier<? extends SqlRenderer> renderers;

    private final Function<KQLTranspiler, List<I>> info;

    public static <I extends ColumnInfo> Function<KQLTranspiler, List<I>> getInfo(
            Supplier<I> supplier) {
        return t -> t.infos(supplier);
    }

    /**
     * @param renderers makes the renderer for one operation; asked once for each, never kept. It
     *     has to hand out a renderer that nobody else holds -- {@code () -> new
     *     SqlQueryRenderer(dialect, zone)} -- or the generator is exactly as shareable as the one
     *     it hands out
     */
    public Generator(
            LinkResolver resolver,
            Supplier<? extends SqlRenderer> renderers,
            Supplier<I> supplier) {

        this(resolver, renderers, getInfo(supplier));
    }

    /**
     * @param renderers see {@link #Generator(LinkResolver, Supplier, Supplier)}
     */
    public Generator(
            LinkResolver resolver,
            Supplier<? extends SqlRenderer> renderers,
            Function<KQLTranspiler, List<I>> info) {
        this.resolver = resolver;
        this.renderers = Objects.requireNonNull(renderers, "renderers");
        this.info = info;
    }

    /**
     * @deprecated one renderer for every operation there will ever be, and so a generator for one
     *     caller at a time. Pass a supplier: {@code () -> new SqlQueryRenderer(dialect, zone)}.
     */
    @Deprecated
    public Generator(LinkResolver resolver, SqlRenderer renderer, Supplier<I> supplier) {

        this(resolver, shared(renderer), getInfo(supplier));
    }

    /**
     * @deprecated see {@link #Generator(LinkResolver, SqlRenderer, Supplier)}
     */
    @Deprecated
    public Generator(
            LinkResolver resolver, SqlRenderer renderer, Function<KQLTranspiler, List<I>> info) {
        this(resolver, shared(renderer), info);
    }

    /**
     * The supplier behind the deprecated constructors: the same instance every time, which is what
     * they always meant. Not checked for null here -- a null renderer has always been accepted and
     * has failed on first use.
     *
     * @deprecated exists only for the deprecated constructors that take a renderer instance, and
     *     goes with them. Nothing new should call it: a renderer that is handed out again and again
     *     is what a supplier is there to avoid.
     */
    @Deprecated
    static Supplier<SqlRenderer> shared(SqlRenderer renderer) {
        return () -> renderer;
    }

    /** What {@code withInfo} and the like hand on: the means, not a renderer made from them. */
    Supplier<? extends SqlRenderer> renderers() {
        return renderers;
    }

    /**
     * The transpiler for a query, with the function catalog and dialect of a renderer made for it.
     *
     * <p>Stood in the code five times verbatim -- four times here, once in {@code
     * Engine.executeKQL}. One place, so that validation and execution cannot drift apart: what
     * {@link #validateKQL} lets through, {@code executeKQL} must also be able to translate.
     *
     * <p>{@link #formatKQL} deliberately does not take this path.
     */
    protected KQLTranspiler transpiler(String kql) {
        return transpiler(kql, getRenderer());
    }

    /**
     * The same, with the renderer the caller has: an operation that also renders takes one renderer
     * and uses it for both, so that the function catalog the query is checked against is the one it
     * is rendered with.
     */
    protected KQLTranspiler transpiler(String kql, SqlRenderer renderer) {
        return KQLTranspiler.builder(kql, resolver)
                .functions(renderer.getFunctionRenderer())
                .dialect(renderer.getDialect())
                .build();
    }

    public String toSql(String kql) {
        SqlRenderer renderer = getRenderer();
        return transpiler(kql, renderer).getSql(renderer);
    }

    public List<I> analyze(String kql) {
        KQLTranspiler transpiler = transpiler(kql);
        return info != null ? info.apply(transpiler) : java.util.Collections.emptyList();
    }

    /**
     * Validates without executing; returns the errors (empty = valid). Parse errors still throw.
     *
     * <p>Deliberately errors only, not {@code violations()}: callers treat an empty list as
     * "valid", so an advisory warning must not read as a failure. Use {@link #warningsKQL} for
     * those.
     */
    public List<ai.koryki.iql.validate.Violation> validateKQL(String kql) {
        return transpiler(kql).errors();
    }

    /**
     * Advisory diagnostics for a query that is otherwise valid — e.g. a function KQL does not know.
     */
    public List<ai.koryki.iql.validate.Violation> warningsKQL(String kql) {
        return transpiler(kql).warnings();
    }

    /** Pretty-prints the KQL (the former behavior of validateKQL). */
    public String formatKQL(String kql) {
        return formatKQL(kql, 0);
    }

    public String formatKQL(String kql, int maxlinesize) {
        // Without a function catalog and without a dialect, and therefore not via transpiler(kql):
        // formatting needs only the parse tree. Whoever hands in a query with an unknown function
        // should get it back readable rather than fail validation -- that is precisely the query
        // one wants to see formatted, in order to find the mistake.
        KQLTranspiler transpiler = KQLTranspiler.builder(kql, resolver).build();

        KQLFormatter formatter =
                new KQLFormatter(transpiler.getCtx(), transpiler.getDescription())
                        .withMaxLineLength(maxlinesize);
        return formatter.format();
    }

    public LinkResolver getResolver() {
        return resolver;
    }

    /**
     * A renderer for one use. Made by the supplier the generator was built with, so a new one on
     * every call; a generator built from an instance (deprecated) returns that instance every time.
     * Do not keep it, and do not give it to another thread.
     */
    public SqlRenderer getRenderer() {
        return Objects.requireNonNull(renderers.get(), "the renderer supplier returned null");
    }

    /** The TypeDescriptor derivation that decorates the result; chosen at construction. */
    public Function<KQLTranspiler, List<I>> getInfo() {
        return info;
    }
}
