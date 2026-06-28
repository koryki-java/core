package ai.koryki.duckdb;

import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.northwind.duckdb.NorthwindDuckdb;
import ai.koryki.databases.temporal.duckdb.TemporalService;
import ai.koryki.jdbc.WordedLocaleFormat;
import ai.koryki.kql.Engine;
import ai.koryki.kql.HeaderInfo;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.util.Locale;

/**
 * Demonstrates the business-facing duration rendering: same engine/data as the temporal tests, but
 * formatted with {@link WordedLocaleFormat} (WIDE) under {@code Locale.ENGLISH} instead of the
 * canonical {@code StableFormat}. The golden therefore shows the worded / HH:MM:SS form.
 */
public class DurationDisplayEngineTest extends BaseEngineTest<HeaderInfo> {

    @Override protected String queriesRoot() { return "src/test/resources/ai/koryki/duckdb/queries/display"; }
    @Override protected String expectedCsv() { return "src/test/resources/ai/koryki/duckdb/expected/display/csv"; }
    @Override protected String expectedSql() { return "src/test/resources/ai/koryki/duckdb/expected/display/sql"; }

    public DurationDisplayEngineTest() {
        super("duckdb");
    }

    @BeforeAll
    public void setup() throws IOException {
        engine = Engine.builder(NorthwindDuckdb.<ListWithSqlResult<HeaderInfo>>northwind(), TemporalService.resolver(),
                new SqlQueryRenderer(java.time.ZoneId.of("UTC"))).format(WordedLocaleFormat.wide(Locale.ENGLISH)).build();
    }
}
