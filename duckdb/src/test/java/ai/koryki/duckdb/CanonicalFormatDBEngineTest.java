package ai.koryki.duckdb;

import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.northwind.duckdb.NorthwindDuckdb;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.jdbc.LocaleFormat;
import ai.koryki.kql.Engine;
import ai.koryki.kql.HeaderInfo;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.util.Locale;

/**
 * Exercises the type-driven read-layer formatter end-to-end: the engine renders
 * results through {@link LocaleFormat} in canonical (ISO) mode, so output is
 * driven by each column's resolved TypeDescriptor — integers without a forced
 * ".0", decimals at their real scale, dates as ISO. Distinct from the
 * StableFormat suites (which keep the formatter inert to isolate SQL goldens).
 */
public class CanonicalFormatDBEngineTest extends BaseEngineTest<HeaderInfo> {

    @Override protected String queriesRoot() { return "src/test/resources/ai/koryki/duckdb/queries/format"; }
    @Override protected String expectedCsv() { return "src/test/resources/ai/koryki/duckdb/expected/format/csv"; }
    @Override protected String expectedSql() { return "src/test/resources/ai/koryki/duckdb/expected/format/sql"; }

    public CanonicalFormatDBEngineTest() {
        super("duckdb");
    }

    @BeforeAll
    public void setup() throws IOException {
        engine = Engine.builder(NorthwindDuckdb.<ListWithSqlResult<HeaderInfo>>northwind(), NorthwindService.resolver(),
                new SqlQueryRenderer(java.time.ZoneId.of("UTC"))).format(new LocaleFormat((Locale)null)).build();
    }
}
