package ai.koryki.kql;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.SqlQueryRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code count_distinct(x)} — the function form of SQL's {@code COUNT(DISTINCT x)}.
 *
 * <p>A function rather than a grammar change: the grammar's call rule has no DISTINCT slot, and the
 * function spelling is what BI tools use anyway (Tableau COUNTD, Power BI DISTINCTCOUNT).
 *
 * <p>The windowed form is covered where it belongs: the four engines that accept it run
 * {@code docs/aggregate/count_distinct_window.kql} for real, and the four that must refuse are
 * pinned by {@code CountDistinctWindowTest} in the postgresql module — core cannot see the dialect
 * modules, and the engine harness cannot assert a failure.
 */
public class CountDistinctTest {

    private static LinkResolver resolver;

    @BeforeAll
    static void setUp() throws IOException {
        resolver = NorthwindService.resolver();
    }

    @Test
    void rendersAsCountDistinct() {
        assertTrue(sql(DuckdbBaseDialect.INSTANCE,
                "FIND customers c FETCH c.country, count_distinct(c.customer_id) n")
                .contains("COUNT(DISTINCT c.customer_id)"));
    }

    /** It is an aggregate, so the non-aggregated column must be grouped exactly as plain count does. */
    @Test
    void drivesGroupByInference() {
        assertTrue(sql(DuckdbBaseDialect.INSTANCE,
                "FIND customers c FETCH c.country, count_distinct(c.customer_id) n")
                .contains("GROUP BY"));
    }

    private static String sql(SqlDialect dialect, String kql) {
        return KQLTranspiler.builder(kql, resolver).functions(dialect.getFunctionRenderer())
                .build().getSql(new SqlQueryRenderer(dialect, ZoneId.of("UTC")));
    }
}
