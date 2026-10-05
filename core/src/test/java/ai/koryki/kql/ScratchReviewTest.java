package ai.koryki.kql;

import ai.koryki.databases.complex.duckdb.ComplexService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.SqlQueryRenderer;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ScratchReviewTest {
    static void show(String kql) {
        String one = kql.replace("\n", " ");
        try {
            String sql =
                    KQLTranspiler.builder(kql, ComplexService.resolver())
                            .functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer())
                            .build()
                            .getSql(
                                    new SqlQueryRenderer(
                                            DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")));
            System.out.println(
                    "OK   " + one + "\n     => " + sql.replace("\n", " ").replaceAll("\\s+", " "));
        } catch (Throwable e) {
            System.out.println(
                    "FAIL "
                            + one
                            + "\n     => "
                            + String.valueOf(e.getMessage()).replace("\n", " "));
        }
    }

    @Test
    void probe() {
        show("FIND check_complex t FILTER 'red' IN t.tags FETCH t.nr");
        show("FIND check_complex t FILTER t.nr IN (1, 2) FETCH t.nr");
        show("FIND check_complex t FILTER t.tags@1 IN ('red', 'x') FETCH t.nr");
        show("FIND check_complex t FILTER 'red' NOT IN t.tags FETCH t.nr");
        show("FIND check_complex t FETCH t.nr, length(t.tags) n");
    }
}
