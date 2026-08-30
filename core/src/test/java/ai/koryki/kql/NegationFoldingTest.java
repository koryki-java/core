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
 * Negation folded into the operator rather than wrapped around it.
 *
 * <p>The two forms are equivalent in three-valued logic — {@code NOT (x IN s)} and {@code x NOT IN s}
 * agree on NULL as well — so this is about the SQL reading the way it would be written by hand, and
 * about optimisers that detect anti-joins from {@code NOT EXISTS} / {@code NOT IN} specifically.
 * Semantics are pinned by the fixture corpus, whose result CSVs are unchanged by the folding and are
 * compared against every live database.
 */
public class NegationFoldingTest {

    private static LinkResolver resolver;

    @BeforeAll
    static void setUp() throws IOException {
        resolver = NorthwindService.resolver();
    }

    @Test
    void isNullFoldsToIsNotNull() {
        String sql = sql("FIND orders o FILTER NOT o.shipped_date ISNULL FETCH o.order_id");
        assertTrue(sql.contains("o.shipped_date IS NOT NULL"), sql);
        assertFalse(sql.contains("NOT ("), sql);
    }

    @Test
    void inFoldsToNotIn() {
        String sql = sql("FIND orders o FILTER NOT o.ship_city IN ('London', 'Paris') FETCH o.order_id");
        assertTrue(sql.contains("o.ship_city NOT IN ('London', 'Paris')"), sql);
    }

    @Test
    void likeFoldsToNotLike() {
        String sql = sql("FIND customers c FILTER NOT c.company_name LIKE 'A%' FETCH c.customer_id");
        assertTrue(sql.contains("c.company_name NOT LIKE 'A%'"), sql);
    }

    @Test
    void betweenFoldsToNotBetween() {
        String sql = sql("FIND orders o FILTER NOT o.freight BETWEEN 10 AND 50 FETCH o.order_id");
        assertTrue(sql.contains("o.freight NOT BETWEEN 10 AND 50"), sql);
    }

    /** The anti-join form, and without the parenthesis pair the wrapped version needed. */
    @Test
    void existsFoldsToNotExists() {
        String sql = sql("FIND customers c FILTER NOT EXISTS (c orders o) FETCH c.customer_id");
        assertTrue(sql.contains("NOT EXISTS ("), sql);
        assertFalse(sql.contains("NOT (\n"), sql);
    }

    /** A subquery operand brings its own parentheses; IN must not add a second pair. */
    @Test
    void subqueryInDoesNotDoubleParenthesize() {
        String sql = sql("FIND customers c FILTER NOT c.customer_id IN ("
                + "FIND orders o FETCH o.customer_id) FETCH c.customer_id");
        assertTrue(sql.contains("c.customer_id NOT IN ("), sql);
        assertFalse(sql.contains("IN (("), sql);
    }

    /**
     * Symbol comparisons stay structural on purpose. {@code NOT (a > b)} could render as
     * {@code a <= b}, but that reads as a different query rather than this one negated, and the
     * wrapped form is already clear.
     */
    @Test
    void symbolComparisonsAreNotRewritten() {
        assertTrue(sql("FIND orders o FILTER NOT o.freight > 100 FETCH o.order_id")
                .contains("NOT (o.freight > 100)"));
    }

    private static String sql(String kql) {
        return KQLTranspiler.builder(kql, resolver).build()
                .getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")));
    }
}
