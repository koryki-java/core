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
package ai.koryki.databases.complex.duckdb;

import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlRenderer;
import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.jdbc.Database;
import ai.koryki.jdbc.ResultProcessor;
import ai.koryki.kql.Engine;
import ai.koryki.kql.EngineBuilder;
import ai.koryki.kql.HeaderInfo;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * The test bed for complex types: the table {@code check_complex} of the Northwind DuckDB database,
 * one column per shape of ARRAY, STRUCT, MAP and JSON, and nestings of them. DuckDB only, because
 * the other dialects have no counterpart for most of it.
 *
 * <p>Like the other beds, it needs the catalog from {@code koryki-northwind-catalog} and the
 * database from {@code koryki-northwind-duckdb} on the class path. The renderers are suppliers: the
 * engine asks for one for every query, so it holds none and can be shared.
 */
public class ComplexService<I extends ColumnInfo, P extends ResultProcessor<I>> {
    public static final String DB = "/ai/koryki/duckdb/databases/complex";
    public static final String ROOT = "/ai/koryki/duckdb/databases/complex/model";

    private final Engine<I, P> engine;

    public static <I extends ColumnInfo, P extends ResultProcessor<I>> ComplexService<I, P> build(
            Database<P> database, Supplier<? extends SqlRenderer> renderers, Supplier<I> supplier) {
        return new ComplexService<>(database, renderers, supplier);
    }

    public static <P extends ResultProcessor<ColumnInfo>> ComplexService<ColumnInfo, P> build(
            Database<P> database, Supplier<? extends SqlRenderer> renderers) {
        return new ComplexService<>(database, renderers, HeaderInfo::new);
    }

    /** Create the service with Locale.ENGLISH, not the system default locale. */
    public ComplexService(
            Database<P> database, Supplier<? extends SqlRenderer> renderers, Supplier<I> supplier) {
        this(database, renderers, Locale.ENGLISH, supplier);
    }

    public ComplexService(
            Database<P> database,
            Supplier<? extends SqlRenderer> renderers,
            Locale locale,
            Supplier<I> supplier) {
        this(database, renderers, resolver(locale), supplier);
    }

    public ComplexService(
            Database<P> database,
            Supplier<? extends SqlRenderer> renderers,
            LinkResolver resolver,
            Supplier<I> supplier) {
        engine = new EngineBuilder<I, P>(database, resolver, renderers).info(supplier).build();
    }

    public static LinkResolver resolver() {
        return resolver(Locale.ENGLISH);
    }

    public static LinkResolver resolver(Locale locale) {
        Schema db = CatalogLoader.db(DB);
        Model model = CatalogLoader.model(ROOT, locale);
        return new LinkResolver(locale, db, model, true);
    }

    public Engine<I, P> getEngine() {
        return engine;
    }
}
