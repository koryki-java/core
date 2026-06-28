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
package ai.koryki.databases.typecheck.duckdb;

import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlRenderer;
import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.jdbc.Database;
import ai.koryki.jdbc.ResultProcessor;
import ai.koryki.kql.Engine;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.catalog.Util;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Schema;

import java.util.function.Supplier;

public class TypecheckService<I extends ColumnInfo, P extends ResultProcessor<I>> {
    public static final String DB = "/ai/koryki/databases/duckdb/typecheck";
    public static final String ROOT = "/ai/koryki/databases/duckdb/typecheck/model";

    private final Engine<I, P> engine;

    public static <I extends ColumnInfo, P extends ResultProcessor<I>> TypecheckService<I, P> build(Database<P> database, SqlRenderer renderer, Supplier<I> supplier) {
        return new TypecheckService<>(database, renderer, supplier);
    }

    public static <P extends ResultProcessor<ColumnInfo>> TypecheckService<ColumnInfo, P> build(Database<P> database, SqlRenderer renderer) {

        return new TypecheckService<>(database, renderer, HeaderInfo::new);
    }

    /**
     * Create Service with Locale.ENGLISH, not system default Locale!
     */
    public TypecheckService(Database<P> database, SqlRenderer renderer, Supplier<I> supplier) {
        this(database, renderer, java.util.Locale.ENGLISH, supplier);
    }

    public TypecheckService(Database<P> database, SqlRenderer renderer, java.util.Locale locale, Supplier<I> supplier) {

        this(database, renderer, resolver(locale), supplier);
    }

    public TypecheckService(Database<P> database, SqlRenderer renderer, LinkResolver resolver, Supplier<I> supplier) {
        engine = new Engine<I, P>(database, resolver, renderer, Engine.getInfo(resolver, supplier));
    }

    public static LinkResolver resolver() {
        return resolver(java.util.Locale.ENGLISH);
    }

    public static LinkResolver resolver(java.util.Locale locale) {

        Schema db = Util.db(DB);
        Model schema = Util.model(ROOT, locale);
        LinkResolver resolver = new LinkResolver(locale, db, schema, true);
        return resolver;
    }

    public Engine<I, P> getEngine() {
        return engine;
    }
}
