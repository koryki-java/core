package ai.koryki.duckdb;

import java.nio.file.Path;
import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.northwind.duckdb.NorthwindDuckdb;
import ai.koryki.databases.temporal.duckdb.TemporalService;
import ai.koryki.jdbc.WordedLocaleFormat;
import ai.koryki.kql.EngineBuilder;
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

    @Override protected String schema() { return "display"; }

    // A purely module-local suite: this schema does not exist in the shared corpus, so the roots
    // point into the module.
    @Override protected Path queriesRoot() { return Path.of("src/test/resources/ai/koryki/duckdb/queries/display"); }
    @Override protected Path expectedCsv() { return Path.of("src/test/resources/ai/koryki/duckdb/expected/display/csv"); }
    @Override protected Path expectedSql() { return Path.of("src/test/resources/ai/koryki/duckdb/expected/display/sql"); }


    public DurationDisplayEngineTest() {
        super("duckdb");
    }

    @BeforeAll
    public void setup() throws IOException {
        engine = EngineBuilder.headers(NorthwindDuckdb.<ListWithSqlResult<HeaderInfo>>northwind(), TemporalService.resolver(),
                new SqlQueryRenderer(java.time.ZoneId.of("UTC"))).valueFormat(WordedLocaleFormat.wide(Locale.ENGLISH)).build();
    }
}
