package ai.koryki.snowflake.northwind;

import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.kql.HeaderInfo;
import java.util.Locale;
import ai.koryki.databases.cases.TestUtil;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.kql.EngineBuilder;
import ai.koryki.snowflake.SnowflakeUnavailable;
import ai.koryki.snowflake.iql.SqlQueryRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

@SnowflakeUnavailable
public class SnowflakeEngineTest extends BaseEngineTest<HeaderInfo> {

    @Override protected String schema() { return "northwind"; }

    // The parse_* fixtures are dialect-specific here (their own format masks) and therefore live
    // in the module, not in the shared corpus.
    @Override protected Path localQueries() { return Path.of("src/test/resources/ai/koryki/snowflake/queries/northwind"); }
    @Override protected Path localExpectedCsv() { return Path.of("src/test/resources/ai/koryki/snowflake/expected/northwind/csv"); }
    @Override protected Path localExpectedSql() { return Path.of("src/test/resources/ai/koryki/snowflake/expected/northwind/sql"); }


    public SnowflakeEngineTest() {
        super("snowflake", true);
    }

    @BeforeAll
    public void readNorthwindDB() throws Exception {
        engine = EngineBuilder.headers(new NorthwindSnowflake<ListWithSqlResult<HeaderInfo>>(), NorthwindService.resolver(),
                new SqlQueryRenderer(java.time.ZoneId.of("UTC"))).valueFormat(new StableFormat(Locale.ROOT)).build();
    }

    @Test
    public void testSingleFile() throws IOException {
        Path kql = queriesRoot().resolve("expression/str_trim.kql");
        TestUtil.test(kql, suffix(), engine, queriesRoot(), expectedCsv(), expectedSql());
    }
}
