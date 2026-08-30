package ai.koryki.kql;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The null-safe comparison operator {@code DISTINCT}.
 *
 * <p>The one comparison in KQL that is two-valued: it answers true or false even when an operand is
 * NULL, where every other operator answers unknown and the row is then dropped by {@code FILTER}.
 * That is the whole point of it — {@code c.region <> o.ship_region} silently excludes every row where
 * both regions are absent, and there was previously no way to say "and count two blanks as equal"
 * short of {@code a = b OR (a ISNULL AND b ISNULL)}.
 *
 * <p>The per-dialect renderings (MariaDB {@code <=>}, Oracle {@code DECODE}) are exercised for real
 * by {@code queries/kql/northwind/docs/comparison/distinct.kql}, which runs against every live database.
 */
public class NullSafeComparisonTest {

    private static LinkResolver resolver;

    @BeforeAll
    static void setUp() throws IOException {
        resolver = NorthwindService.resolver();
    }

    @Test
    void rendersAsAnsiDistinctness() {
        assertTrue(sql("FIND customers c, orders o FILTER c.region DISTINCT o.ship_region FETCH c.customer_id")
                .contains("c.region IS DISTINCT FROM o.ship_region"));
    }

    /** Negation folds into the operator's own negative form rather than wrapping it. */
    @Test
    void negationFoldsToIsNotDistinctFrom() {
        String sql = sql("FIND customers c, orders o FILTER NOT c.region DISTINCT o.ship_region FETCH c.customer_id");
        assertTrue(sql.contains("c.region IS NOT DISTINCT FROM o.ship_region"), sql);
        assertFalse(sql.contains("NOT ("), sql);
    }

    /**
     * DISTINCT keeps its {@code FETCH DISTINCT} role. The word now appears in two grammar positions —
     * after FETCH, and between two expressions — which is only safe because they can never overlap.
     * This pins that: the row-deduplicating sense still parses and renders.
     */
    @Test
    void fetchDistinctStillDeduplicates() {
        assertTrue(sql("FIND orders o FETCH DISTINCT o.ship_city").contains("DISTINCT o.ship_city"));
    }

    /** Both senses of the word in one query — the case that would expose a grammar ambiguity. */
    @Test
    void bothSensesCoexistInOneQuery() {
        String sql = sql("FIND customers c, orders o FILTER c.region DISTINCT o.ship_region "
                + "FETCH DISTINCT c.customer_id");
        assertTrue(sql.contains("DISTINCT c.customer_id"), sql);
        assertTrue(sql.contains("c.region IS DISTINCT FROM o.ship_region"), sql);
    }

    private static String sql(String kql) {
        return KQLTranspiler.builder(kql, resolver).build()
                .getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")));
    }
}
