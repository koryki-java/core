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

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.SqlRenderer;
import ai.koryki.iql.query.viz.Layer;
import ai.koryki.iql.query.viz.Mapping;
import ai.koryki.iql.query.viz.Visualise;

import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.jdbc.Format;
import ai.koryki.jdbc.ListResult;
import ai.koryki.jdbc.ResultConsumer;
import ai.koryki.jdbc.Database;
import ai.koryki.viz.RenderResult;
import ai.koryki.viz.VegaLite;
import ai.koryki.viz.VizColumn;
import ai.koryki.viz.stat.SqlRunner;
import ai.koryki.viz.stat.StatResult;
import ai.koryki.viz.stat.StatTransform;
import ai.koryki.viz.stat.StatTransforms;
import ai.koryki.result.Finding;
import ai.koryki.result.Investigator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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

    /**
     * Runs a KQL query carrying a {@code VISUALISE} clause and returns the
     * result rendered as a Vega-Lite JSON spec (data embedded inline).
     *
     * @throws KorykiaiException if the query has no VISUALISE clause
     */
    public String executeVegaLite(String kql) {
        KQLTranspiler transpiler = KQLTranspiler.builder(kql, resolver).functions(renderer.getFunctionRenderer()).build();
        Visualise viz = transpiler.getQuery().getVisualise();
        if (viz == null) {
            throw new KorykiaiException("query has no VISUALISE clause");
        }
        String baseSql = stripTrailingSemicolon(transpiler.getSql(renderer));
        List<VizColumn> baseColumns = enrichedColumns(transpiler);

        // statistical geoms aggregate in the DB (their own data); ordinary layers draw the raw result
        Map<Layer, StatResult> stats = new HashMap<>();
        SqlRunner runner = sql -> runSql(sql).getRows();
        boolean needsBase = viz.getLayers().isEmpty();
        for (Layer layer : viz.getLayers()) {
            if (layer.isPlace()) {
                continue;
            }
            StatTransform stat = StatTransforms.forGeom(layer.getMark());
            if (stat == null && layer.getSettings().containsKey("aggregate")) {
                stat = StatTransforms.aggregate(); // opt-in aggregation on an ordinary geom
            }
            if (stat == null) {
                needsBase = true;
                continue;
            }
            stats.put(layer, stat.compute(runner, baseSql, layer, channelColumns(viz, layer)));
        }

        // only pull the raw result when a layer actually draws it (a pure histogram never does)
        ListResult<I> base = new ListResult<>();
        if (needsBase) {
            if (info != null) {
                base.setInfos(info.apply(transpiler)); // TypeDescriptors drive both decoding and channel types
            }
            if (format != null) {
                base.setFormat(format);
            }
            execute(baseSql, collector(base), s -> {});
        }

        return new VegaLite().render(viz, baseColumns, base.getRows(), stats);
    }

    /**
     * Validates a KQL query and, if it carries a VISUALISE clause, renders it to a
     * Vega-Lite spec — <b>without executing any SQL</b> (the spec is built structurally
     * from the query's resolved columns with empty data). The deterministic check for a
     * generate → validate → fix loop: returns a chart, a deliberate no-chart (valid query,
     * no VISUALISE), or the precise error message to feed back to the generator for repair.
     */
    public RenderResult validateAndRender(String kql) {
        KQLTranspiler transpiler;
        try {
            transpiler = KQLTranspiler.builder(kql, resolver).functions(renderer.getFunctionRenderer()).build();
            transpiler.getSql(renderer); // force parse + column resolution now (DB-free) so syntax and
                                         // resolution errors surface here rather than escaping later
        } catch (KorykiaiException e) {
            return RenderResult.error(e.getMessage()); // parse or resolution failure
        } catch (RuntimeException e) {
            return RenderResult.error("invalid query: " + describe(e));
        }
        List<ai.koryki.iql.validate.Violation> violations = transpiler.violations();
        if (!violations.isEmpty()) {
            return RenderResult.error(violations.stream()
                    .map(ai.koryki.iql.validate.Violation::getMessage)
                    .collect(Collectors.joining("; ")));
        }
        Visualise viz = transpiler.getQuery().getVisualise();
        if (viz == null) {
            return RenderResult.noChart(); // valid query, no chart requested — that is fine
        }
        try {
            String spec = new VegaLite().render(viz, VegaLite.columns(transpiler), java.util.List.of());
            return RenderResult.chart(spec);
        } catch (KorykiaiException e) {
            return RenderResult.error(e.getMessage()); // VISUALISE render failure (bad coord, missing geometry, …)
        } catch (RuntimeException e) {
            return RenderResult.error("could not render VISUALISE: " + describe(e));
        }
    }

    private static String describe(RuntimeException e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    /**
     * Per-column analysis for a query — the {@code info} provider's per-column objects
     * (e.g. {@code Finding}s from an {@code Investigator}), derived statically with
     * <b>no SQL executed</b>. Empty when no info provider is configured. Feeds the
     * two-stage "generate query, then add VISUALISE" flow.
     */
    public List<I> analyze(String kql) {
        KQLTranspiler transpiler = KQLTranspiler.builder(kql, resolver).functions(renderer.getFunctionRenderer()).build();
        return info != null ? info.apply(transpiler) : java.util.Collections.emptyList();
    }

    private ListResult<I> runSql(String sql) {
        ListResult<I> r = new ListResult<>();
        execute(sql, collector(r), s -> {});
        return r;
    }

    /**
     * Base columns (name + type) enriched with query semantics — concluded metric
     * name, physical unit, temporal grain and ORDER BY — derived statically by the
     * {@link Investigator}. Drives encoding polish (titles/formats/sort). Best-effort:
     * on any failure the plain columns are returned so rendering never breaks.
     */
    private List<VizColumn> enrichedColumns(KQLTranspiler transpiler) {
        List<VizColumn> base = VegaLite.columns(transpiler);
        try {
            Locale locale = resolver.getLocale() != null ? resolver.getLocale() : Locale.ENGLISH;
            List<Finding> findings = new Investigator(locale).asInfoProvider().apply(transpiler);
            Map<String, Finding> byAlias = new HashMap<>();
            for (Finding f : findings) {
                if (f.getOut() != null && f.getOut().getHeader() != null) {
                    byAlias.put(f.getOut().getHeader(), f);
                }
            }
            List<VizColumn> enriched = new ArrayList<>(base.size());
            for (VizColumn c : base) {
                Finding f = byAlias.get(c.getName());
                if (f == null) {
                    enriched.add(c);
                    continue;
                }
                var q = f.getQuantity();
                String qdim = q != null && q.known() && q.dim() != null ? q.dim().toString() : null;
                enriched.add(new VizColumn(c.getName(), c.getType(), f.getMetric(), f.getUnit(),
                        f.getGrain(), f.getOrderPriority(), f.getOrderDirection(), qdim));
            }
            return enriched;
        } catch (RuntimeException e) {
            return base; // enrichment is best-effort; never break rendering
        }
    }

    /** Merged channel → output-column bindings (layer overrides global; literals ignored). */
    private static Map<String, String> channelColumns(Visualise viz, Layer layer) {
        Map<String, String> mapping = new HashMap<>();
        putColumns(mapping, viz.getGlobal());
        putColumns(mapping, layer.getMapping());
        return mapping;
    }

    private static void putColumns(Map<String, String> mapping, List<Mapping> mappings) {
        for (Mapping m : mappings) {
            if (m.getChannel() != null && m.getColumn() != null) {
                mapping.put(m.getChannel(), m.getColumn());
            }
        }
    }

    private static String stripTrailingSemicolon(String sql) {
        String s = sql.trim();
        while (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    private C collector(ListResult<I> result) {
        // JdbcDatabase.execute() invokes only ResultConsumer interface methods
        // (getInfos/metadata/append) on the processor, so a row-collecting
        // ListResult<I> runs safely regardless of the engine's concrete C.
        return (C) result;
    }

    /** Validates without executing; returns the violations (empty = valid). Parse errors still throw. */
    public List<ai.koryki.iql.validate.Violation> validateKQL(String kql) {
        KQLTranspiler transpiler = KQLTranspiler.builder(kql, resolver).functions(renderer.getFunctionRenderer()).build();
        return transpiler.violations();
    }

    /** Pretty-prints the KQL (the former behavior of validateKQL). */
    public String formatKQL(String kql) {
        return formatKQL(kql, 0);
    }

    public String formatKQL(String kql, int maxlinesize) {
        KQLTranspiler transpiler = KQLTranspiler.builder(kql, resolver).build();

        KQLFormatter formatter = new KQLFormatter(transpiler.getCtx(), transpiler.getDescription()).withMaxLineLength(maxlinesize);
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
