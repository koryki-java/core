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
import ai.koryki.antlr.Interval;
import ai.koryki.antlr.KorykiaiException;
import ai.koryki.antlr.PanicException;
import ai.koryki.iql.query.Block;
import ai.koryki.iql.query.Out;
import ai.koryki.iql.query.Query;
import ai.koryki.iql.query.Source;
import ai.koryki.iql.rules.Aggregate;
import ai.koryki.iql.validate.ValidateException;
import ai.koryki.iql.validate.Validator;
import ai.koryki.iql.validate.Violation;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IQLTranspiler {

    private Aggregate aggregat;
    private LinkResolver resolver;
    private IQLReader reader;
    private List<Interval> panic;
    private String iql;
    private String description;
    private IQLParser.QueryContext ctx;
    private Query query;
    private IQLQueryMapper iql2Bean;
    private List<Violation> violations = new ArrayList<>();
    private IQLVisibilityContext visibilityContext;


    public IQLTranspiler(Aggregate aggregat, InputStream iql, LinkResolver resolver) throws IOException {
        this(aggregat, AbstractReader.readStream(iql), resolver);
    }

    public IQLTranspiler(Aggregate aggregat, String iql, LinkResolver resolver) {
        this.aggregat = aggregat;
        this.iql= iql;
        this.resolver = resolver;
    }

    public IQLReader getReader() {
        if (reader == null) {
            try {
                IQLReader r = new IQLReader(iql, false);
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

    public IQLParser.QueryContext getCtx() {
        panic(panic);
        if (ctx == null) {
            IQLReader r = getReader();
            ctx = r.getQuery();
            panic(r.getPanic());
        }
        return ctx;
    }

    private IQLQueryMapper getIql2Bean() {
        if (iql2Bean == null) {
            iql2Bean = new IQLQueryMapper(getCtx(), getDescription());
        }
        return iql2Bean;
    }

    public String getSql(SqlRenderer renderer) {

        return renderer.toSql(resolver, visibility(), getQuery(), getIql2Bean().getIqlToContext());
    }

    private IQLVisibilityContext visibility() {
        if (visibilityContext == null) {
            violations(violations);
            Query q = getQuery();
            List<Violation> v = new ArrayList<>();

            Map<String, Source> blockIdToLeadingTableMap = Walker.apply(q, new BlockLeadingSourceCollector());
            Map<String, Block> blockIdToBlockMap =  Walker.apply(q, new BlockRegistryCollector());
                    // must not execute rules here, IQL has to be valid in text form!

            IQLQueryMapper l = getIql2Bean();
            Validator validator = new Validator(aggregat, q, resolver, blockIdToLeadingTableMap, l.getIqlToContext());
            v.addAll(validator.validate());
            SelectScopeCollector select2Aliases = new SelectScopeCollector(l.getIqlToContext());
            Map<Object, Map<String, Source>> s2a = Walker.apply(q, select2Aliases);
            v.addAll(select2Aliases.violations());
            violations(v);

            visibilityContext = new IQLVisibilityContext(blockIdToBlockMap, blockIdToLeadingTableMap, s2a);
        }
        return visibilityContext;
    }

    private void violations(List<Violation> v) {
        this.violations = v;
        if (v != null && !v.isEmpty()) {
            throw new ValidateException(v);
        }
    }

    public Query getQuery() {
        if (query == null) {
            query = getIql2Bean().toQuery(getCtx());
            query.setDescription(getDescription());
        }
        return query;
    }

    public List<Out> getOut() {

        return SqlQueryRenderer.collectOut(getQuery());
    }

    public String getIql() {

        return new IQLSerializer(getQuery()).toString();
    }
}
