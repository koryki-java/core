package ai.koryki.duckdb;

import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.databases.cases.TestUtil;
import ai.koryki.databases.northwind.duckdb.NorthwindDuckdb;
import ai.koryki.databases.temporal.duckdb.TemporalService;
import ai.koryki.kql.EngineBuilder;
import ai.koryki.kql.HeaderInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public class TemporalDBEngineTest extends BaseEngineTest<HeaderInfo> {

    @Override protected String schema() { return "temporal"; }


    public TemporalDBEngineTest() {
        super("duckdb", true);
    }


    @BeforeAll
    public void setup() throws IOException {
        engine = EngineBuilder.headers(NorthwindDuckdb.<ListWithSqlResult<HeaderInfo>>northwind(), TemporalService.resolver(),
                new SqlQueryRenderer(java.time.ZoneId.of("UTC"))).valueFormat(new StableFormat(Locale.ROOT)).build();
    }

    @Test
    public void testSingleFile() throws IOException {
        //Path kql = queriesRoot().resolve("fetch_time_sec_from_midnight.kql");
        Path kql = queriesRoot().resolve("duration_literal.kql");
        TestUtil.<HeaderInfo>test(kql, suffix(), engine, queriesRoot(), expectedCsv(), expectedSql());
    }
}
