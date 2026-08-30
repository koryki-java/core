package ai.koryki.kql;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.FunctionRenderer;
import ai.koryki.iql.functions.StandardFunctions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: a function rendered through a dialect template (or custom renderBody)
 * silently dropped the call's OVER clause — only the registry's default rendering
 * appended it. Every dialect module overrides aggregates this way (MariaDB
 * GROUP_CONCAT, Snowflake LISTAGG, Trino array_join), turning window calls into
 * plain aggregates. The OVER clause is appended in FunctionDefinition.render now.
 */
public class WindowOverTemplateTest {

    private static final class TemplatedDialect extends DuckdbBaseDialect {
        private final FunctionRenderer functions;

        private TemplatedDialect() {
            FunctionRegistry registry = StandardFunctions.registry();
            registry.override("string_agg", 2, "group_concat({0}, {1})");
            functions = registry;
        }

        @Override
        public FunctionRenderer getFunctionRenderer() {
            return functions;
        }
    }

    @Test
    public void overClauseSurvivesTemplatedOverride() throws IOException {
        String kql = """
                FIND orders o
                FETCH o.order_id, string_agg(o.ship_city, ',') OVER (PARTITION o.customer_id) cities
                """;
        KQLTranspiler transpiler = KQLTranspiler.builder(
                new ByteArrayInputStream(kql.getBytes(StandardCharsets.UTF_8)), NorthwindService.resolver()).build();
        String sql = transpiler.getSql(new SqlQueryRenderer(new TemplatedDialect(), ZoneId.of("UTC")));

        assertTrue(sql.contains("group_concat(o.ship_city, ',')"), "template not applied:\n" + sql);
        assertTrue(sql.contains("OVER (PARTITION BY o.customer_id"), "OVER clause dropped:\n" + sql);
    }
}
