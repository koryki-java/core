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

import ai.koryki.antlr.AbstractReader;
import ai.koryki.antlr.AbstractTranspiler;
import ai.koryki.antlr.Bag;
import ai.koryki.antlr.Lazy;
import ai.koryki.iql.BlockLeadingSourceCollector;
import ai.koryki.iql.BlockRegistryCollector;
import ai.koryki.iql.IQLVisibilityContext;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SelectScopeCollector;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.SqlRenderer;
import ai.koryki.iql.OutputColumn;
import ai.koryki.iql.functions.FunctionCatalog;
import ai.koryki.iql.typing.ExpressionTypeResolver;
import ai.koryki.iql.Walker;
import ai.koryki.iql.query.Block;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Out;
import ai.koryki.iql.query.Query;
import ai.koryki.iql.rewrite.DateBetweenRewriter;
import ai.koryki.iql.query.Source;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.iql.rules.Rules;
import ai.koryki.iql.validate.ValidateException;
import ai.koryki.iql.validate.Validator;
import ai.koryki.iql.validate.Violation;
import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.catalog.domain.Model;
import org.antlr.v4.runtime.RuleContext;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Lazy pipeline of memoized pure stages: reader → ctx → analysis.
 * The analysis stage maps the tree, derives the visibility maps from the
 * pre-rules tree, applies the rewrite rules, and validates — all in one atomic
 * step, so the tree is never observable in its pre-rules state. Every accessor
 * is idempotent.
 */
public class KQLTranspiler extends AbstractTranspiler<KQLReader, KQLParser.QueryContext> {

    private final String kql;
    private final LinkResolver resolver;
    private final FunctionCatalog functions;

    private final Lazy<Analysis> analysis = Lazy.of(this::analyze);

    /**
     * Output of the single analysis stage. The rules mutate the query tree
     * inside this stage only; {@code iqlToContext} is keyed by node identity,
     * which is why the tree is rewritten in place rather than copied.
     */
    private record Analysis(Query query,
                            IQLVisibilityContext visibility,
                            Map<Object, RuleContext> iqlToContext,
                            List<Violation> violations) {}

    public KQLTranspiler(InputStream kql, LinkResolver resolver) throws IOException {
        this(AbstractReader.readStream(kql), resolver);
    }

    public KQLTranspiler(String kql, LinkResolver resolver) {
        this(kql, resolver, null);
    }

    public KQLTranspiler(InputStream kql, LinkResolver resolver, FunctionCatalog functions) throws IOException {
        this(AbstractReader.readStream(kql), resolver, functions);
    }

    /** @param functions dialect function catalog for arity/unsupported validation; null = skip those checks */
    public KQLTranspiler(String kql, LinkResolver resolver, FunctionCatalog functions) {

        this.kql = kql;
        this.resolver = resolver;
        this.functions = functions;
    }

    /** Fluent assembly; see {@link KQLTranspilerBuilder}. */
    public static KQLTranspilerBuilder builder(String kql, LinkResolver resolver) {
        return new KQLTranspilerBuilder(kql, resolver);
    }

    /** Fluent assembly from a stream; the source is read eagerly. */
    public static KQLTranspilerBuilder builder(InputStream kql, LinkResolver resolver) throws IOException {
        return new KQLTranspilerBuilder(AbstractReader.readStream(kql), resolver);
    }

    @Override
    protected KQLReader newReader() throws IOException {
        return new KQLReader(kql, false);
    }

    private Analysis analyze() {
        KQLQueryMapper mapper = new KQLQueryMapper(resolver, getCtx(), getDescription());
        Query q = mapper.toBean();
        q.setDescription(getDescription());
        Map<Object, RuleContext> iqlToContext = mapper.getIqlToContext();

        // scope and visibility maps are derived from the pre-rules tree
        SelectScopeCollector select2Aliases = new SelectScopeCollector(iqlToContext);
        Map<Object, Map<String, Source>> s2a = Walker.apply(q, select2Aliases);
        Map<String, Source> blockIdToLeadingSourceMap = Walker.apply(q, new BlockLeadingSourceCollector());
        Map<String, Block> blockIdToBlockMap = Walker.apply(q, new BlockRegistryCollector());
        IQLVisibilityContext visibility = new IQLVisibilityContext(blockIdToBlockMap, blockIdToLeadingSourceMap, s2a);

        new Rules(resolver, blockIdToLeadingSourceMap, q, iqlToContext).apply();

        List<Violation> v = new ArrayList<>();
        v.addAll(new Validator(q, resolver, blockIdToLeadingSourceMap, iqlToContext, functions, visibility).validate());
        v.addAll(select2Aliases.violations());

        return new Analysis(q, visibility, iqlToContext, List.copyOf(v));
    }

    public String getSql(SqlRenderer renderer) {

        Analysis a = validAnalysis();
        Query q = a.query();
        Runnable restore = DateBetweenRewriter.rewrite(q);
        try {
            SqlRenderer.Rendered rendered = renderer.toSql(resolver, a.visibility(), q, a.iqlToContext());
            // capture the schema the renderer resolved, so infos() reuses it instead of recomputing
            this.renderedSchema = rendered.outputs();
            return rendered.sql();
        } finally {
            restore.run();
        }
    }

    private List<OutputColumn> renderedSchema;

    /** Validation results as a value; never throws. */
    public List<Violation> violations() {
        return analysis.get().violations();
    }

    private Analysis validAnalysis() {
        Analysis a = analysis.get();
        if (!a.violations().isEmpty()) {
            throw new ValidateException(a.violations());
        }
        return a;
    }

    public Query getQuery() {
        return validAnalysis().query();
    }

    public IQLVisibilityContext visibility() {
        return validAnalysis().visibility();
    }

    public List<Out> getOut() {

        return SqlQueryRenderer.collectOut(getQuery());
    }

    public String getKql() {

        return new KQLFormatter(getCtx(), getDescription()).format();
    }

    public LinkResolver getResolver() {
        return resolver;
    }

    public <C extends ColumnInfo> List<C> infos(Supplier<C> infoSupplier) {
        Query query = getQuery();
        IQLVisibilityContext visibility = visibility().child(SqlQueryRenderer.select(query));

        // reuse the schema the renderer resolved (single source of truth); else resolve self
        List<OutputColumn> schema = renderedSchema != null ? renderedSchema : resolveOutputsSelf(visibility);

        List<C> infos = schema.stream().map(col -> {
            C i = infoSupplier.get();
            i.setHeader(info(getResolver().getModel(), visibility, col.out()));
            i.setTypeDescriptor(col.type());
            return i;
        }).collect(Collectors.toList());
        return infos;
    }

    /** Fallback when infos() is called without a prior getSql(): resolve with this transpiler's catalog. */
    private List<OutputColumn> resolveOutputsSelf(IQLVisibilityContext visibility) {
        ExpressionTypeResolver types = new ExpressionTypeResolver(getResolver(), visibility, functions);
        return getOut().stream()
                .map(o -> new OutputColumn(o, resolveOutType(types, o.getExpression())))
                .collect(Collectors.toList());
    }

    /** Resolved output type, or {@code null} if it can't be determined (e.g. no dialect catalog). */
    private static TypeDescriptor resolveOutType(ExpressionTypeResolver types, Expression expression) {
        try {
            return types.resolve(expression);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String info(Model schema, IQLVisibilityContext visibility, Out out) {
        String l = out.getLabel() != null ? out.getLabel() : out.getHeader() != null ? out.getHeader() : defaultLabel(schema, visibility, out);
        return l;
    }

    private static String defaultLabel(Model schema, IQLVisibilityContext visibility, Out out) {

        if (out.getExpression().getField() != null) {

            Bag<String> label = new Bag<>("Col: " + Integer.toString(out.getIdx()));

            Source t = visibility.getSource(out.getExpression().getField().getAlias());
            if (t == null) {
                throw new ai.koryki.antlr.KorykiaiException("unknown source alias for output field "
                        + out.getExpression().getField().getAlias() + "." + out.getExpression().getField().getName());
            }
            schema.getEntity(t.getName()).ifPresent(x -> x.getAttribute(out.getExpression().getField().getName()).ifPresent(c -> label.setItem(c.getLabel() != null ? c.getLabel() : c.getName())));
            return label.getItem();
        } else {
            // Just Field-Index
            return Integer.toString(out.getIdx());
        }
    }
}
