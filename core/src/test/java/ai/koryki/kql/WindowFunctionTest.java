package ai.koryki.kql;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.validate.Violation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Window-only functions — the ones that answer "where does this row sit among those rows".
 *
 * <p>The grammar already allowed {@code OVER (…)} on any call and the renderer already emitted it;
 * what was missing was the functions themselves and a kind that says they cannot be used without it.
 */
public class WindowFunctionTest {

    private static LinkResolver resolver;

    @BeforeAll
    static void setUp() throws IOException {
        resolver = NorthwindService.resolver();
    }

    @Test
    void rankRendersWithItsWindow() {
        String sql = sql("FIND orders o FETCH o.order_id, rank() OVER (ORDER o.freight DESC) r");
        // note the space after "(" — the renderer emits it when there is no PARTITION; pre-existing
        assertTrue(sql.contains("rank() OVER ( ORDER BY o.freight DESC)"), sql);
    }

    @Test
    void lagTakesTheValueFromAnEarlierRow() {
        assertTrue(sql("FIND orders o FETCH o.order_id, lag(o.freight) OVER (ORDER o.order_date) prev")
                .contains("lag(o.freight) OVER ( ORDER BY o.order_date)"));
    }

    @Test
    void partitionAndOrderBothRender() {
        String sql = sql("FIND orders o FETCH o.order_id, "
                + "row_number() OVER (PARTITION o.ship_country ORDER o.freight) n");
        assertTrue(sql.contains("PARTITION BY o.ship_country"), sql);
        assertTrue(sql.contains("ORDER BY o.freight"), sql);
    }

    /** Without OVER the question is incomplete, and the message should say so in those terms. */
    @Test
    void windowFunctionWithoutOverIsAnError() {
        List<Violation> v = violations("FIND orders o FETCH o.order_id, rank() r");
        assertTrue(v.stream().anyMatch(Violation::isError), v.toString());
        assertTrue(v.get(0).getMessage().contains("OVER"), v.get(0).getMessage());
    }

    /** A rank with nothing to rank by is arbitrary; row_number is deliberately exempt. */
    @Test
    void rankWithoutOrderIsAnError() {
        assertTrue(violations("FIND orders o FETCH o.order_id, rank() OVER (PARTITION o.ship_country) r")
                .stream().anyMatch(Violation::isError));
        assertTrue(violations("FIND orders o FETCH o.order_id, row_number() OVER (PARTITION o.ship_country) n")
                .isEmpty(), "numbering an unordered partition is legitimate");
    }

    /**
     * A window function computes per row and must not collapse them, so it must never pull GROUP BY
     * inference in the way an aggregate does.
     */
    @Test
    void windowFunctionDoesNotTriggerGroupBy() {
        assertFalse(sql("FIND orders o FETCH o.order_id, rank() OVER (ORDER o.freight) r")
                .contains("GROUP BY"));
    }

    private static String sql(String kql) {
        return KQLTranspiler.builder(kql, resolver).functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer())
                .build().getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")));
    }

    private static List<Violation> violations(String kql) {
        return KQLTranspiler.builder(kql, resolver)
                .functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer()).build().violations();
    }
}
