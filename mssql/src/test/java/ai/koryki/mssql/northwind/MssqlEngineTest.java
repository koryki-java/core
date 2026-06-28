package ai.koryki.mssql.northwind;

import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.kql.HeaderInfo;

import java.time.ZoneId;
import java.util.Locale;
import ai.koryki.databases.cases.TestUtil;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.kql.Engine;
import ai.koryki.mssql.MssqlUnavailable;
import ai.koryki.mssql.iql.SqlQueryRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;

@MssqlUnavailable
public class MssqlEngineTest extends BaseEngineTest<HeaderInfo> {

    @Override protected String queriesRoot() { return "src/test/resources/ai/koryki/mssql/queries/northwind"; }
    @Override protected String expectedCsv() { return "src/test/resources/ai/koryki/mssql/expected/northwind/csv"; }
    @Override protected String expectedSql() { return "src/test/resources/ai/koryki/mssql/expected/northwind/sql"; }

    public MssqlEngineTest() {
        super("mssql");
    }


    @BeforeAll
    public void readNorthwindDB() throws IOException, SQLException {
        engine = Engine.builder(new NorthwindMssql<ListWithSqlResult<HeaderInfo>>(ZoneId.of("UTC")), NorthwindService.resolver(),
                new SqlQueryRenderer(java.time.ZoneId.of("UTC"))).format(new StableFormat(Locale.ROOT)).build();
    }

    @Test
    public void testSingleFile() throws IOException {
        Path kql = Path.of("src/test/resources/ai/koryki/mssql/queries/northwind/privatetest/expression/str_trim.kql");
        TestUtil.test(kql, suffix(), engine, Path.of(queriesRoot()), Path.of(expectedCsv()), Path.of(expectedSql()));
    }
}


