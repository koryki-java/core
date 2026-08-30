package ai.koryki.postgresql.iql;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.kql.KQLTranspiler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The rejecting half of the {@code count_distinct} window split.
 *
 * <p>Same shape as {@code StringAggWindowTest} in the oracle module, and for the same stated reason:
 * the engine harness cannot assert dialect-specific failures, so a shared fixture can cover the
 * engines that accept this (it does — {@code docs/aggregate/count_distinct_window.kql}) but not the
 * ones that must refuse.
 *
 * <p>Measured across the eight engines: DuckDB, Oracle, Trino and Snowflake accept
 * {@code COUNT(DISTINCT x) OVER (…)}; PostgreSQL, MariaDB, SQL Server and SQLite reject it. Those
 * four mark the function {@code windowUnsupported}, so the transpiler refuses rather than emitting
 * SQL the database will throw out. The accepting half is pinned in core's {@code CountDistinctTest}.
 */
public class CountDistinctWindowTest {

    private static LinkResolver resolver;

    @BeforeAll
    public static void setUp() throws IOException {
        resolver = NorthwindService.resolver();
    }

    @Test
    public void windowedCountDistinctIsRejected() {
        assertThrows(RuntimeException.class, () -> KQLTranspiler.builder(
                "FIND customers c FETCH c.customer_id, "
                        + "count_distinct(c.customer_id) OVER (PARTITION c.country) n", resolver)
                .functions(PostgreSqlDialect.INSTANCE.getFunctionRenderer()).build()
                .getSql(new SqlQueryRenderer(PostgreSqlDialect.INSTANCE, ZoneId.of("UTC"))),
                "PostgreSQL cannot window COUNT(DISTINCT ...) — it must not render silently");
    }
}
