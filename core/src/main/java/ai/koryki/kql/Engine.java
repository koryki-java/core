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

import ai.koryki.antlr.Bag;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlRenderer;
import ai.koryki.iql.IQLVisibilityContext;
import ai.koryki.iql.query.Out;
import ai.koryki.iql.query.Source;

import ai.koryki.iql.rules.Aggregate;
import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.jdbc.ResultConsumer;
import ai.koryki.scaffold.domain.Model;
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
    private Aggregate aggregate;

    private Function<KQLTranspiler, List<I>> info;

    public static <C extends ResultConsumer<HeaderInfo>> Engine<HeaderInfo, C> build(
            Database<C> database, LinkResolver resolver, SqlRenderer renderer) {

        Function<KQLTranspiler, List<HeaderInfo>> info = getInfo(resolver);
        return new Engine<>(database, resolver, renderer, info);
    }

    public static Function<KQLTranspiler, List<HeaderInfo>> getInfo(LinkResolver resolver) {

        return getInfo(resolver, HeaderInfo::new);
    }

    public static <I extends ColumnInfo> Function<KQLTranspiler, List<I>> getInfo(LinkResolver resolver, Supplier<I> supplier) {
        Function<KQLTranspiler, List<I>> info = (t) -> {
            List<I> list = SqlQueryRenderer.collectOut(t.getQuery())
                    .stream().
                    map(o -> {
                        I i = supplier.get();
                        i.setHeader(info(resolver.getModel(), t.visibility().child(SqlQueryRenderer.select(t.getQuery())), o));
                        return i;
                    }).collect(Collectors.toList());
            return list;
        };
        return info;
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

    public C executeKQL(String kql, Supplier< C> processor, Consumer<Statement> stmtConsumer) {

        KQLTranspiler transpiler = new KQLTranspiler();
        if (aggregate != null) {
            transpiler.setAggregate(aggregate);
        }
        transpiler.setKql(kql);
        transpiler.setResolver(resolver);

        String sql = transpiler.getSql(renderer);

        C p = processor.get();

        if (info != null) {
            p.setInfos(info.apply(transpiler));
        }

        execute(sql, p, stmtConsumer);
        return p;
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
            schema.getEntity(t.getName()).flatMap(x -> x.getColumn(out.getExpression().getField().getName())).ifPresent(c -> label.setItem(c.getLabel() != null ? c.getLabel() : c.getName()));
            return label.getItem();
        } else {
            // Just Field-Index
            return Integer.toString(out.getIdx());
        }
    }

    public String toSql(String kql) {
        KQLTranspiler transpiler = new KQLTranspiler(kql, resolver);
        return transpiler.getSql(renderer);
    }

    public String validateKQL(String kql) {
        KQLTranspiler transpiler = new KQLTranspiler(kql, resolver);

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

    public Aggregate getAggregate() {
        return aggregate;
    }

    public void setAggregate(Aggregate aggregate) {
        this.aggregate = aggregate;
    }

}
