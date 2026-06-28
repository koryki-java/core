package ai.koryki.snowflake.northwind;

import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.kql.HeaderInfo;
import java.util.Locale;
import ai.koryki.databases.cases.TestUtil;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.kql.Engine;
import ai.koryki.snowflake.SnowflakeUnavailable;
import ai.koryki.snowflake.iql.SqlQueryRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

@SnowflakeUnavailable
public class SnowflakeEngineTest extends BaseEngineTest<HeaderInfo> {

    @Override protected String queriesRoot() { return "src/test/resources/ai/koryki/snowflake/queries/northwind"; }
    @Override protected String expectedCsv() { return "src/test/resources/ai/koryki/snowflake/expected/northwind/csv"; }
    @Override protected String expectedSql() { return "src/test/resources/ai/koryki/snowflake/expected/northwind/sql"; }

    public SnowflakeEngineTest() {
        super("snowflake");
    }

    @BeforeAll
    public void readNorthwindDB() throws Exception {
        engine = Engine.builder(new NorthwindSnowflake<ListWithSqlResult<HeaderInfo>>(), NorthwindService.resolver(),
                new SqlQueryRenderer(java.time.ZoneId.of("UTC"))).format(new StableFormat(Locale.ROOT)).build();
    }

    @Test
    public void testSingleFile() throws IOException {
        Path kql = Path.of("src/test/resources/ai/koryki/snowflake/queries/northwind/privatetest/expression/str_trim.kql");
        TestUtil.test(kql, suffix(), engine, Path.of(queriesRoot()), Path.of(expectedCsv()), Path.of(expectedSql()));
    }
}
