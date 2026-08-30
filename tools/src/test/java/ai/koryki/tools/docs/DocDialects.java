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
 * The single list of documented dialects and documentation samples, shared by every docs
 * generator so the function pages, the per-dialect support matrices and the test-module SQL
 * goldens can never end up covering different sets.
 */
final class DocDialects {

    /** A dialect as the docs see it: catalog key, menu title, nav order, and the dialect itself. */
    record Doc(String name, String title, int order, SqlDialect dialect) {
        @Override
        public String toString() {
            return name;
        }
    }

    /** One documentation sample: its {@link FunctionDocGenerator#slug slug}, demo database and KQL. */
    record Sample(String slug, String db, String kql) {
    }

    

    /**
     * Sample roots in resolution order. Must match {@code FunctionDocGenerator.SAMPLE_ROOTS}:
     * a slug present in more than one root is taken from the first, so the SQL shown on a page
     * always belongs to the sample shown above it.
     */
    private static final List<String> ROOTS = List.of("northwind", "typecheck", "temporal");

    private static final List<Doc> ALL = List.of(
            new Doc("duckdb", "DuckDB", 21, DuckdbBaseDialect.INSTANCE),
            new Doc("oracle", "Oracle", 22, OracleDialect.INSTANCE),
            new Doc("snowflake", "Snowflake", 23, SnowflakeDialect.INSTANCE),
            new Doc("mssql", "SQL Server", 24, MssqlDialect.INSTANCE),
            new Doc("postgresql", "PostgreSQL", 25, PostgreSqlDialect.INSTANCE),
            new Doc("mariadb", "MariaDB", 26, MariadbDialect.INSTANCE),
            new Doc("sqlite", "SQLite", 27, SqliteDialect.INSTANCE),
            new Doc("trino", "Trino", 28, TrinoDialect.INSTANCE));

    static List<Doc> all() {
        return ALL;
    }

    static List<String> names() {
        return ALL.stream().map(Doc::name).toList();
    }

    static Map<String, SqlDialect> byName() {
        Map<String, SqlDialect> byName = new LinkedHashMap<>();
        for (Doc doc : ALL) {
            byName.put(doc.name(), doc.dialect());
        }
        return byName;
    }

    /** db name (the sample-path prefix: typecheck/temporal/northwind) -> its model resolver. */
    static Map<String, LinkResolver> resolvers() throws IOException {
        return Map.of(
                "typecheck", TypecheckService.resolver(),
                "temporal", TemporalService.resolver(),
                "northwind", NorthwindService.resolver());
    }

    /** Every documentation sample, keyed by slug. Empty when run outside the tools module. */
    static Map<String, Sample> samples() throws IOException {
        Map<String, Sample> samples = new LinkedHashMap<>();
        for (String db : ROOTS) {
            Path root = Fixtures.queries(db).resolve("docs");
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root, FileVisitOption.FOLLOW_LINKS)) {
                for (Path p : (Iterable<Path>) walk.filter(x -> x.toString().endsWith(".kql"))::iterator) {
                    String slug = p.getFileName().toString().replaceFirst("\\.kql$", "");
                    samples.putIfAbsent(slug, new Sample(slug, db, Files.readString(p)));
                }
            }
        }
        return samples;
    }

    private DocDialects() {
    }
}
