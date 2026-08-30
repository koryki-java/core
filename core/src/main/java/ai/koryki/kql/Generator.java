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
 * <p><b>Why this is a class of its own.</b> The five methods here never touched the
 * {@code database} field of {@link Engine}, yet were reachable only through a constructor that
 * demanded a {@code Database}. So anyone who merely wanted to check a query needed an open
 * connection they did not use -- and anyone without one had to fake it. That is the whole reason
 * for the split.
 *
 * <p><b>Immutable, and therefore shareable.</b> All three fields are final; whoever wants something
 * else builds a second one. That is not cosmetic: until now the engine carried mutable state via
 * {@code setInfo} and {@code setFormat}, so every caller had to rebuild it per call while holding a
 * lock -- a lock that then guarded two entirely different things, the connection and the
 * configuration. A generator has no connection and now no mutable configuration either: it may be
 * created once per language and left standing.
 */
public class Generator<I extends ColumnInfo> {

    private final LinkResolver resolver;
    private final SqlRenderer renderer;

    private final Function<KQLTranspiler, List<I>> info;

    public static <I extends ColumnInfo> Function<KQLTranspiler, List<I>> getInfo(Supplier<I> supplier) {
        return t -> t.infos(supplier);
    }

    public Generator(LinkResolver resolver, SqlRenderer renderer, Supplier<I> supplier) {

        this(resolver, renderer, getInfo(supplier));
    }

    public Generator(LinkResolver resolver, SqlRenderer renderer,
                     Function<KQLTranspiler, List<I>> info) {
        this.resolver = resolver;
        this.renderer = renderer;
        this.info = info;
    }

    /**
     * The transpiler for a query, with the renderer's function catalog and dialect.
     *
     * <p>Stood in the code five times verbatim -- four times here, once in
     * {@code Engine.executeKQL}. One place, so that validation and execution cannot drift apart:
     * what {@link #validateKQL} lets through, {@code executeKQL} must also be able to translate.
     *
     * <p>{@link #formatKQL} deliberately does not take this path.
     */
    protected KQLTranspiler transpiler(String kql) {
        return KQLTranspiler.builder(kql, resolver)
                .functions(renderer.getFunctionRenderer())
                .dialect(renderer.getDialect())
                .build();
    }

    public String toSql(String kql) {
        return transpiler(kql).getSql(renderer);
    }

    public List<I> analyze(String kql) {
        KQLTranspiler transpiler = transpiler(kql);
        return info != null ? info.apply(transpiler) : java.util.Collections.emptyList();
    }

    /**
     * Validates without executing; returns the errors (empty = valid). Parse errors still throw.
     *
     * <p>Deliberately errors only, not {@code violations()}: callers treat an empty list as "valid",
     * so an advisory warning must not read as a failure. Use {@link #warningsKQL} for those.
     */
    public List<ai.koryki.iql.validate.Violation> validateKQL(String kql) {
        return transpiler(kql).errors();
    }

    /** Advisory diagnostics for a query that is otherwise valid — e.g. a function KQL does not know. */
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

        KQLFormatter formatter = new KQLFormatter(transpiler.getCtx(), transpiler.getDescription()).withMaxLineLength(maxlinesize);
        return formatter.format();
    }

    public LinkResolver getResolver() {
        return resolver;
    }

    public SqlRenderer getRenderer() {
        return renderer;
    }

    /** The TypeDescriptor derivation that decorates the result; chosen at construction. */
    public Function<KQLTranspiler, List<I>> getInfo() {
        return info;
    }

}
