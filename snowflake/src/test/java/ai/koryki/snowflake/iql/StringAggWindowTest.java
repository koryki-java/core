package ai.koryki.snowflake.iql;

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
 * Snowflake LISTAGG allows OVER () with PARTITION BY only — no window ORDER or frame —
 * so a windowed string_agg is rejected at transpile time (conservative full rejection;
 * partition-only support can be added later). Transpile-only, no DB or Snowflake
 * account needed (the engine harness cannot assert dialect-specific failures).
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
