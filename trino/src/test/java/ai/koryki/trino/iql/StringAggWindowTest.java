package ai.koryki.trino.iql;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.kql.KQLTranspiler;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trino renders string_agg as array_join(array_agg(..)) — an OVER clause cannot
 * attach to the outer array_join call, so a windowed string_agg must be rejected
 * at transpile time instead of rendering invalid SQL. Transpile-only, no DB needed
 * (the engine harness cannot assert dialect-specific failures).
 */
public class StringAggWindowTest {

    @Test
    public void stringAggWithOverIsRejected() throws IOException {
        KQLTranspiler transpiler = KQLTranspiler.builder(new ByteArrayInputStream("""
                FIND orders o
                FETCH o.order_id, string_agg(o.ship_city, ',') OVER (PARTITION o.customer_id) cities
                """.getBytes(StandardCharsets.UTF_8)), NorthwindService.resolver()).build();
        UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class,
                () -> transpiler.getSql(new SqlQueryRenderer(ZoneId.of("UTC"))));
        assertTrue(e.getMessage().contains("OVER"), e.getMessage());
    }
}
