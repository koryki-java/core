package ai.koryki.tools.docs;

import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.functions.StandardFunctions;
import ai.koryki.mssql.iql.MssqlDialect;
import ai.koryki.oracle.iql.OracleDialect;
import ai.koryki.postgresql.iql.PostgreSqlDialect;
import ai.koryki.snowflake.iql.SnowflakeDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Golden-file docs: generated from the function catalog into the repo's docs/
 * tree. Missing files are written; existing files fail the build on drift
 * (delete and re-run to regenerate).
 */
class FunctionDocsTest {

    record Dialect(String name, String title, int order, SqlDialect dialect) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Dialect> dialects() {
        return Stream.of(
                new Dialect("duckdb",     "DuckDB",     21, DuckdbBaseDialect.INSTANCE),
                new Dialect("oracle",     "Oracle",     22, OracleDialect.INSTANCE),
                new Dialect("snowflake",  "Snowflake",  23, SnowflakeDialect.INSTANCE),
                new Dialect("mssql",      "SQL Server", 24, MssqlDialect.INSTANCE),
                new Dialect("postgresql", "PostgreSQL", 25, PostgreSqlDialect.INSTANCE));
    }

    @Test
    void categoryPagesAreUpToDate() throws IOException {
        Map<String, String> pages = new FunctionDocGenerator().categoryPages(StandardFunctions.registry());
        for (Map.Entry<String, String> e : pages.entrySet()) {
            FunctionDocGenerator.sync(Path.of("../docs/functions/" + e.getKey()), e.getValue());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void dialectPageIsUpToDate(Dialect d) throws IOException {
        String md = new FunctionDocGenerator().dialectPage(d.name(), d.title(), d.order(),
                StandardFunctions.registry(), d.dialect().getFunctionRenderer());
        FunctionDocGenerator.sync(Path.of("../docs/dialects/" + d.name() + ".md"), md);
    }
}
