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
package ai.koryki.iql;

import ai.koryki.antlr.AbstractReader;
import ai.koryki.antlr.AbstractTranspiler;
import ai.koryki.antlr.Lazy;
import ai.koryki.iql.functions.FunctionRenderer;
import ai.koryki.iql.query.Block;
import ai.koryki.iql.query.Out;
import ai.koryki.iql.query.Query;
import ai.koryki.iql.query.Source;
import ai.koryki.iql.rewrite.DateBetweenRewriter;
import ai.koryki.iql.validate.ValidateException;
import ai.koryki.iql.validate.Validator;
import ai.koryki.iql.validate.Violation;
import org.antlr.v4.runtime.RuleContext;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lazy pipeline of memoized pure stages:
 * reader → ctx → mapped (query tree) → analysis (scope maps + validation).
 * Every accessor is idempotent; no stage mutates the output of another.
 */
public class IQLTranspiler extends AbstractTranspiler<IQLReader, IQLParser.QueryContext> {

    private final String iql;
    private final LinkResolver resolver;
    private final FunctionRenderer functions;

    private final Lazy<Mapped> mapped = Lazy.of(this::map);
    private final Lazy<Analysis> analysis = Lazy.of(this::analyze);

    /** Output of the mapping stage: the query tree and its source-context index. */
    private record Mapped(Query query, Map<Object, RuleContext> iqlToContext) {}

    /** Everything derived from the mapped tree: visibility maps and validation results. */
    private record Analysis(IQLVisibilityContext visibility, List<Violation> violations) {}

    public IQLTranspiler(InputStream iql, LinkResolver resolver) throws IOException {
        this(AbstractReader.readStream(iql), resolver);
    }

    public IQLTranspiler(String iql, LinkResolver resolver) {
        this(iql, resolver, null);
    }

    public IQLTranspiler(InputStream iql, LinkResolver resolver, FunctionRenderer functions) throws IOException {
        this(AbstractReader.readStream(iql), resolver, functions);
    }

    /** @param functions dialect function catalog for arity/unsupported validation; null = skip those checks */
    public IQLTranspiler(String iql, LinkResolver resolver, FunctionRenderer functions) {

        this.iql = iql;
        this.resolver = resolver;
        this.functions = functions;
    }

    /** Fluent assembly; see {@link IQLTranspilerBuilder}. */
    public static IQLTranspilerBuilder builder(String iql, LinkResolver resolver) {
        return new IQLTranspilerBuilder(iql, resolver);
    }

    /** Fluent assembly from a stream; the source is read eagerly. */
    public static IQLTranspilerBuilder builder(InputStream iql, LinkResolver resolver) throws IOException {
        return new IQLTranspilerBuilder(AbstractReader.readStream(iql), resolver);
    }

    @Override
    protected IQLReader newReader() throws IOException {
        return new IQLReader(iql, false);
    }

    private Mapped map() {
        IQLQueryMapper mapper = new IQLQueryMapper(getCtx(), getDescription());
        Query q = mapper.toQuery(getCtx());
        q.setDescription(getDescription());
        return new Mapped(q, mapper.getIqlToContext());
    }

    private Analysis analyze() {
        Mapped m = mapped.get();
        Query q = m.query();

        Map<String, Source> blockIdToLeadingTableMap = Walker.apply(q, new BlockLeadingSourceCollector());
        Map<String, Block> blockIdToBlockMap = Walker.apply(q, new BlockRegistryCollector());
        // must not execute rules here, IQL has to be valid in text form!

        SelectScopeCollector select2Aliases = new SelectScopeCollector(m.iqlToContext());
        Map<Object, Map<String, Source>> s2a = Walker.apply(q, select2Aliases);
        IQLVisibilityContext visibility = new IQLVisibilityContext(blockIdToBlockMap, blockIdToLeadingTableMap, s2a);

        List<Violation> v = new ArrayList<>();
        v.addAll(new Validator(q, resolver, blockIdToLeadingTableMap, m.iqlToContext(), functions, visibility).validate());
        v.addAll(select2Aliases.violations());

        return new Analysis(visibility, List.copyOf(v));
    }

    public String getSql(SqlRenderer renderer) {

        Analysis a = validAnalysis();
        Query q = getQuery();
        Runnable restore = DateBetweenRewriter.rewrite(q);
        try {
            return renderer.toSql(resolver, a.visibility(), q, mapped.get().iqlToContext()).sql();
        } finally {
            restore.run();
        }
    }

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
        return mapped.get().query();
    }

    public List<Out> getOut() {

        return SqlQueryRenderer.collectOut(getQuery());
    }

    public String getIql() {

        return new IQLSerializer(getQuery()).toString();
    }
}
