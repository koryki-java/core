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
package ai.koryki.tools.docs;

import ai.koryki.databases.cases.Fixtures;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.databases.temporal.duckdb.TemporalService;
import ai.koryki.databases.typecheck.duckdb.TypecheckService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlDialect;
import ai.koryki.mariadb.iql.MariadbDialect;
import ai.koryki.mssql.iql.MssqlDialect;
import ai.koryki.oracle.iql.OracleDialect;
import ai.koryki.postgresql.iql.PostgreSqlDialect;
import ai.koryki.snowflake.iql.SnowflakeDialect;
import ai.koryki.sqlite.iql.SqliteDialect;
import ai.koryki.trino.iql.TrinoDialect;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The single list of documented dialects and documentation samples, shared by every docs generator
 * so the function pages, the per-dialect support matrices and the test-module SQL goldens can never
 * end up covering different sets.
 */
public final class DocDialects {

    /** A dialect as the docs see it: catalog key, menu title, nav order, and the dialect itself. */
    public record Doc(String name, String title, int order, SqlDialect dialect) {
        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * One documentation sample: its slug (as incubator's {@code FunctionDocGenerator} keys pages
     * by), demo database and KQL.
     */
    public record Sample(String slug, String db, String kql) {}

    /**
     * Sample roots in resolution order. Must match {@code FunctionDocGenerator.SAMPLE_ROOTS}: a
     * slug present in more than one root is taken from the first, so the SQL shown on a page always
     * belongs to the sample shown above it.
     */
    private static final List<String> ROOTS = List.of("northwind", "typecheck", "temporal");

    private static final List<Doc> ALL =
            List.of(
                    new Doc("duckdb", "DuckDB", 21, DuckdbBaseDialect.INSTANCE),
                    new Doc("oracle", "Oracle", 22, OracleDialect.INSTANCE),
                    new Doc("snowflake", "Snowflake", 23, SnowflakeDialect.INSTANCE),
                    new Doc("mssql", "SQL Server", 24, MssqlDialect.INSTANCE),
                    new Doc("postgresql", "PostgreSQL", 25, PostgreSqlDialect.INSTANCE),
                    new Doc("mariadb", "MariaDB", 26, MariadbDialect.INSTANCE),
                    new Doc("sqlite", "SQLite", 27, SqliteDialect.INSTANCE),
                    new Doc("trino", "Trino", 28, TrinoDialect.INSTANCE));

    public static List<Doc> all() {
        return ALL;
    }

    public static List<String> names() {
        return ALL.stream().map(Doc::name).toList();
    }

    public static Map<String, SqlDialect> byName() {
        Map<String, SqlDialect> byName = new LinkedHashMap<>();
        for (Doc doc : ALL) {
            byName.put(doc.name(), doc.dialect());
        }
        return byName;
    }

    /** db name (the sample-path prefix: typecheck/temporal/northwind) -> its model resolver. */
    public static Map<String, LinkResolver> resolvers() throws IOException {
        return Map.of(
                "typecheck", TypecheckService.resolver(),
                "temporal", TemporalService.resolver(),
                "northwind", NorthwindService.resolver());
    }

    /** Every documentation sample, keyed by slug. Empty when run outside the tools module. */
    public static Map<String, Sample> samples() throws IOException {
        Map<String, Sample> samples = new LinkedHashMap<>();
        for (String db : ROOTS) {
            Path root = Fixtures.queries(db).resolve("docs");
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root, FileVisitOption.FOLLOW_LINKS)) {
                for (Path p :
                        (Iterable<Path>)
                                walk.filter(x -> x.toString().endsWith(".kql"))::iterator) {
                    String slug = p.getFileName().toString().replaceFirst("\\.kql$", "");
                    samples.putIfAbsent(slug, new Sample(slug, db, Files.readString(p)));
                }
            }
        }
        return samples;
    }

    private DocDialects() {}
}
