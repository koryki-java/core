package ai.koryki.oracle.northwind;

import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.kql.HeaderInfo;
import java.util.Locale;
import ai.koryki.databases.cases.TestUtil;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.kql.Engine;
import ai.koryki.oracle.OracleUnavailable;
import ai.koryki.oracle.iql.SqlQueryRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;

@OracleUnavailable
public class OracleEngineTest extends BaseEngineTest<HeaderInfo> {

    @Override protected String queriesRoot() { return "src/test/resources/ai/koryki/oracle/queries/northwind"; }
    @Override protected String expectedCsv() { return "src/test/resources/ai/koryki/oracle/expected/northwind/csv"; }
    @Override protected String expectedSql() { return "src/test/resources/ai/koryki/oracle/expected/northwind/sql"; }

    public OracleEngineTest() {
        super("oracle");
    }

    @BeforeAll
    public void readNorthwindDB() throws IOException, SQLException {
        engine = Engine.builder(new NorthwindOracle<ListWithSqlResult<HeaderInfo>>(), NorthwindService.resolver(),
                new SqlQueryRenderer(java.time.ZoneId.of("UTC"))).format(new StableFormat(Locale.ROOT)).build();
    }

    @Test
    public void testSingleFile() throws IOException {
        Path kql = Path.of("src/test/resources/ai/koryki/oracle/queries/northwind/parse/parse_date.kql");
        TestUtil.test(kql, suffix(), engine, Path.of(queriesRoot()), Path.of(expectedCsv()), Path.of(expectedSql()));
    }
}
