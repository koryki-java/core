package ai.koryki.kql;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The not-equals operator. {@code <>} is the canonical spelling; {@code !=} is a lexer alias
 * that {@code UnaryLogicalExpression.setOp} folds into {@code <>}, so both reach the catalog
 * (which keys operators by surface text) and the IQL serializer as one operator.
 */
public class NotEqualsOperatorTest {

    private static LinkResolver resolver;

    @BeforeAll
    public static void readNorthwindDB() throws IOException {
        resolver = NorthwindService.resolver();
    }

    @Test
    void notEqualsRendersThroughTheCatalogTemplate() {
        assertTrue(sql("FIND orders o FILTER o.ship_city <> 'London' FETCH o.order_id")
                .contains("o.ship_city <> 'London'"));
    }

    /** The alias is surface syntax only: it must not reach the model, the catalog or the SQL. */
    @Test
    void bangEqualsIsAnAliasOfDiamond() {
        assertEquals(sql("FIND orders o FILTER o.ship_city <> 'London' FETCH o.order_id"),
                sql("FIND orders o FILTER o.ship_city != 'London' FETCH o.order_id"));
    }

    /** Not-equals is an equality-class operator, so it composes with NOT like the others. */
    @Test
    void negatedNotEqualsKeepsItsGrouping() {
        assertTrue(sql("FIND orders o FILTER NOT o.ship_city <> 'London' FETCH o.order_id")
                .contains("NOT (o.ship_city <> 'London')"));
    }

    private static String sql(String kql) {
        return KQLTranspiler.builder(kql, resolver).build()
                .getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")));
    }
}
