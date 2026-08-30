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
 * A boolean expression standing alone as a predicate.
 *
 * <p>Before this, {@code unary_logical_expression} always demanded {@code expression operator …},
 * so the catalog's three BOOLEAN-returning functions could not be used for the one thing they exist
 * to do — their doc samples projected a true/false column instead of filtering — and a BOOLEAN
 * column could not be filtered at all.
 */
public class BarePredicateTest {

    private static LinkResolver resolver;

    @BeforeAll
    static void setUp() throws IOException {
        resolver = NorthwindService.resolver();
    }

    @Test
    void booleanFunctionIsAPredicate() {
        assertTrue(sql("FIND customers c FILTER starts_with(c.company_name, 'A') FETCH c.customer_id")
                .contains("starts_with(c.company_name, 'A')"));
    }

    @Test
    void negatedBooleanFunctionIsAPredicate() {
        assertTrue(sql("FIND customers c FILTER NOT starts_with(c.company_name, 'A') FETCH c.customer_id")
                .contains("NOT ("));
    }

    /** It must still be a *boolean* expression — the grammar cannot tell, only the type can. */
    @Test
    void nonBooleanBarePredicateIsAnError() {
        List<Violation> v = violations("FIND customers c FILTER c.company_name FETCH c.customer_id");
        assertFalse(v.isEmpty(), "a TEXT column is not a filter condition");
        assertTrue(v.stream().anyMatch(Violation::isError), v.toString());
        assertTrue(v.get(0).getMessage().contains("yes/no test"), v.get(0).getMessage());
    }

    /** The operator forms must be untouched by the new alternative. */
    @Test
    void ordinaryComparisonsStillParse() {
        assertTrue(sql("FIND orders o FILTER o.freight > 100 AND o.ship_city = 'London' FETCH o.order_id")
                .contains("o.freight > 100"));
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
