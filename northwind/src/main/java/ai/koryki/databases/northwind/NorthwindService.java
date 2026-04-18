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
package ai.koryki.databases.northwind;

import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlRenderer;
import ai.koryki.jdbc.ResultProcessor;
import ai.koryki.kql.Engine;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.scaffold.Util;
import ai.koryki.scaffold.domain.Model;
import ai.koryki.scaffold.schema.Schema;
import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.jdbc.Database;

import java.util.function.Supplier;

public class NorthwindService<I extends ColumnInfo, P extends ResultProcessor<I>> {

    public static final String ROOT = "/ai/koryki/databases/northwind/model";

    private final Engine<I, P> engine;

    public static <I extends ColumnInfo, P extends ResultProcessor<I>> NorthwindService<I, P> build(Database<P> database, SqlRenderer renderer, Supplier<I> supplier) {
        return new NorthwindService<>(database, renderer, supplier);
    }

    public static <P extends ResultProcessor<ColumnInfo>> NorthwindService<ColumnInfo, P> build(Database<P> database, SqlRenderer renderer) {

        return new NorthwindService<>(database, renderer, HeaderInfo::new);
    }

    /**
     * Create Service with Locale.ENGLISH, not system default Locale!
     */
    public NorthwindService(Database<P> database, SqlRenderer renderer, Supplier<I> supplier) {
        this(database, renderer, java.util.Locale.ENGLISH, supplier);
    }

    public NorthwindService(Database<P> database, SqlRenderer renderer, java.util.Locale locale, Supplier<I> supplier) {

        this(database, renderer, resolver(locale), supplier);
    }

    public NorthwindService(Database<P> database, SqlRenderer renderer, LinkResolver resolver, Supplier<I> supplier) {
        engine = new Engine<I, P>(database, resolver, renderer, Engine.getInfo(resolver, supplier));
    }

    public static LinkResolver resolver() {
        return resolver(java.util.Locale.ENGLISH);
    }

    public static LinkResolver resolver(java.util.Locale locale) {

        Schema db = Util.db(ROOT);
        Model schema = Util.model(ROOT, locale);
        LinkResolver resolver = new LinkResolver(locale, db, schema);
        resolver.setStrict(true);
        return resolver;
    }

    public Engine<I, P> getEngine() {
        return engine;
    }
}
