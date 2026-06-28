package ai.koryki.duckdb;

import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.databases.cases.TestUtil;
import ai.koryki.databases.northwind.duckdb.NorthwindDuckdb;
import ai.koryki.databases.temporal.duckdb.TemporalService;
import ai.koryki.kql.Engine;
import ai.koryki.kql.HeaderInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public class TemporalDBEngineTest extends BaseEngineTest<HeaderInfo> {

    @Override protected String queriesRoot() { return "src/test/resources/ai/koryki/duckdb/queries/temporal"; }
    @Override protected String expectedCsv() { return "src/test/resources/ai/koryki/duckdb/expected/temporal/csv"; }
    @Override protected String expectedSql() { return "src/test/resources/ai/koryki/duckdb/expected/temporal/sql"; }

    public TemporalDBEngineTest() {
        super("duckdb", true);
    }


    @BeforeAll
    public void setup() throws IOException {
        engine = Engine.builder(NorthwindDuckdb.<ListWithSqlResult<HeaderInfo>>northwind(), TemporalService.resolver(),
                new SqlQueryRenderer(java.time.ZoneId.of("UTC"))).format(new StableFormat(Locale.ROOT)).build();
    }

    @Test
    public void testSingleFile() throws IOException {
        //Path kql = Path.of("src/test/resources/ai/koryki/duckdb/queries/temporal/privatetest/fetch_time_sec_from_midnight.kql");
        Path kql = Path.of("src/test/resources/ai/koryki/duckdb/queries/temporal/privatetest/duration_literal.kql");
        TestUtil.<HeaderInfo>test(kql, suffix(), engine, Path.of(queriesRoot()), Path.of(expectedCsv()), Path.of(expectedSql()));
    }
}
