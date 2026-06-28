package ai.koryki.duckdb;

import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.databases.cases.TestUtil;
import ai.koryki.databases.northwind.duckdb.NorthwindDuckdb;
import ai.koryki.databases.typecheck.duckdb.TypecheckService;
import ai.koryki.kql.Engine;
import ai.koryki.kql.HeaderInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public class TypecheckDBEngineTest extends BaseEngineTest<HeaderInfo> {

    @Override protected String queriesRoot() { return "src/test/resources/ai/koryki/duckdb/queries/typecheck"; }
    @Override protected String expectedCsv() { return "src/test/resources/ai/koryki/duckdb/expected/typecheck/csv"; }
    @Override protected String expectedSql() { return "src/test/resources/ai/koryki/duckdb/expected/typecheck/sql"; }

    public TypecheckDBEngineTest() {
        super("duckdb");
    }

    @BeforeAll
    public void setup() throws IOException {
        engine = Engine.builder(NorthwindDuckdb.<ListWithSqlResult<HeaderInfo>>northwind(), TypecheckService.resolver(),
                new SqlQueryRenderer(java.time.ZoneId.of("UTC"))).format(new StableFormat(Locale.ROOT)).build();
    }

    @Test
    public void testSingleFile() throws IOException {
        Path kql = Path.of("src/test/resources/ai/koryki/duckdb/queries/typecheck/privatetest/to_text/other.kql");
        TestUtil.test(kql, suffix(), engine, Path.of(queriesRoot()), Path.of(expectedCsv()), Path.of(expectedSql()));
    }
}
