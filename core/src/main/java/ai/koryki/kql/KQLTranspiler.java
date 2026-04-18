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

import ai.koryki.antlr.*;
import ai.koryki.iql.*;
import ai.koryki.iql.BlockLeadingSourceCollector;
import ai.koryki.iql.IQLVisibilityContext;
import ai.koryki.iql.query.Block;
import ai.koryki.iql.query.Source;
import ai.koryki.iql.rules.Aggregate;
import ai.koryki.iql.query.Out;
import ai.koryki.iql.query.Query;
import ai.koryki.iql.rules.Rules;
import ai.koryki.iql.validate.ValidateException;
import ai.koryki.iql.validate.Validator;
import ai.koryki.iql.validate.Violation;
import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.scaffold.domain.Model;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class KQLTranspiler {

    private Aggregate aggregate;
    private LinkResolver resolver;
    private KQLReader reader;
    private List<Interval> panic;
    private String kql;
    private String description;
    private KQLParser.QueryContext ctx;
    private Query query;
    private KQLQueryMapper kql2Bean;
    private List<Violation> violations;
    private RuleContext ruleContext;

    public KQLTranspiler() {
        this.aggregate = new Aggregate() {
        };
    }


    public KQLTranspiler(InputStream kql, LinkResolver resolver) throws IOException {
        this(kql, resolver, new Aggregate() {
        });
    }

    public KQLTranspiler(InputStream kql, LinkResolver resolver, Aggregate aggregate) throws IOException {
        this(AbstractReader.readStream(kql), resolver, aggregate);
    }

    public KQLTranspiler(String kql, LinkResolver resolver) {
        this(kql, resolver, new Aggregate() {
        });
    }

    public KQLTranspiler(String kql, LinkResolver resolver, Aggregate aggregate) {

        this.aggregate = aggregate;
        this.kql = kql;
        this.resolver = resolver;
    }

    public KQLReader getReader() {
        if (reader == null) {
            try {
                KQLReader r = new KQLReader(kql, false);
                panic(r.getPanic());
                reader = r;
            } catch (IOException e) {
                throw new KorykiaiException(e);
            }
        }
        return reader;
    }

    private void panic(List<Interval> p) {
        this.panic = p;
        if (p != null && !p.isEmpty()) {
            throw new PanicException(p);
        }
    }

    public String getDescription() {
        if (description == null) {
            description = getReader().getDescription();
        }
        return description;
    }

    public KQLParser.QueryContext getCtx() {
        panic(panic);
        if (ctx == null) {
            KQLReader r = getReader();
            KQLParser.QueryContext c = r.getQuery();
            panic(r.getPanic());
//            KQLReader reader = null;
//            try {
//                reader = new KQLReader(Translator.translateToSchema(c, r.getDescription(), resolver));
//            } catch (IOException e) {
//                throw new KorykiaiException(e);
//            }
//            c = reader.getCtx();
            ctx = c;
        }
        return ctx;
    }

    private KQLQueryMapper getKql2Bean() {
        if (kql2Bean == null) {
            kql2Bean = new KQLQueryMapper(resolver, getCtx(), getDescription());
        }
        return kql2Bean;
    }

    public String getSql(SqlRenderer renderer) {

        Query q = getQuery();
        RuleContext ruleContext = ruleContext(q);
        q = rules(q);
        return renderer.toSql(resolver, ruleContext.visibilityContext, q, getKql2Bean().getIqlToContext());
    }

    private RuleContext ruleContext(Query q) {
        if (ruleContext == null) {

            RuleContext rc = new RuleContext();
            KQLQueryMapper l = getKql2Bean();
            rc.iqlToContext = l.getIqlToContext();

            SelectScopeCollector select2Aliases = new SelectScopeCollector(rc.iqlToContext);
            Map<Object, Map<String, Source>> s2a = Walker.apply(q, select2Aliases);
            rc.select2AliasesViolation = select2Aliases.violations();
            rc.blockIdToLeadingSourceMap = Walker.apply(q, new BlockLeadingSourceCollector());
            rc.blockIdToBlockMap = Walker.apply(q, new BlockRegistryCollector());
            rc.visibilityContext = new IQLVisibilityContext(rc.blockIdToBlockMap, rc.blockIdToLeadingSourceMap, s2a);
            ruleContext = rc;
        }
        return ruleContext;
    }

    public IQLVisibilityContext visibility() {
        Query q = getQuery();
        return ruleContext(q).visibilityContext;
    }

    private class RuleContext {
        private List<Violation> select2AliasesViolation;
        private Map<String, Source> blockIdToLeadingSourceMap;
        private Map<String, Block> blockIdToBlockMap;
        private Map<Object, org.antlr.v4.runtime.RuleContext> iqlToContext;
        private IQLVisibilityContext visibilityContext;
    }

    public Query rules() {
        return rules(getQuery());
    }

    private Query rules(Query q) {
        if (violations == null) {

            List<Violation> v = new ArrayList<>();

            RuleContext context = ruleContext(q);
            new Rules(aggregate, resolver, context.blockIdToLeadingSourceMap, q, getKql2Bean().getIqlToContext()).apply();
            Validator validator = new Validator(aggregate, q, resolver, context.blockIdToLeadingSourceMap, context.iqlToContext);
            v.addAll(validator.validate());
            v.addAll(context.select2AliasesViolation);

            violations = v;

            if (!violations.isEmpty()) {
                throw new ValidateException(violations);
            }
        }
        return q;
    }

    public Query getQuery() {
        if (query == null) {
            Query q = getKql2Bean().toBean();
            q.setDescription(getDescription());
            query = rules(q);
        }
        return query;
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

        List<Out> out = getOut();
        List<C> infos = out.stream().map(o -> {
            C i = infoSupplier.get();
            i.setHeader(info(getResolver().getModel(), visibility, o));
            return i;
        }).collect(Collectors.toList());
        return infos;
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
                throw new RuntimeException(out.getExpression().getField().getAlias() + "." + out.getExpression().getField().getName());
            }
            schema.getEntity(t.getName()).ifPresent(x -> x.getColumn(out.getExpression().getField().getName()).ifPresent(c -> label.setItem(c.getLabel() != null ? c.getLabel() : c.getName())));
            return label.getItem();
        } else {
            // Just Field-Index
            return Integer.toString(out.getIdx());
        }
    }

    public Aggregate getAggregate() {
        return aggregate;
    }

    public void setAggregate(Aggregate aggregate) {
        this.aggregate = aggregate;
    }

    public void setResolver(LinkResolver resolver) {
        this.resolver = resolver;
    }

    public void setKql(String kql) {
        this.kql = kql;
    }


}
