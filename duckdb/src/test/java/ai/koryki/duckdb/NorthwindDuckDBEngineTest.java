package ai.koryki.duckdb;

import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.databases.cases.TestUtil;
import ai.koryki.databases.northwind.duckdb.NorthwindDuckdb;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.kql.EngineBuilder;
import ai.koryki.kql.HeaderInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public class NorthwindDuckDBEngineTest extends BaseEngineTest<HeaderInfo> {

    @Override protected String schema() { return "northwind"; }

    // The parse_* fixtures are dialect-specific here (their own format masks) and therefore live
    // in the module, not in the shared corpus.
    @Override protected Path localQueries() { return Path.of("src/test/resources/ai/koryki/duckdb/queries/northwind"); }
    @Override protected Path localExpectedCsv() { return Path.of("src/test/resources/ai/koryki/duckdb/expected/northwind/csv"); }
    @Override protected Path localExpectedSql() { return Path.of("src/test/resources/ai/koryki/duckdb/expected/northwind/sql"); }


    public NorthwindDuckDBEngineTest() {
        super("duckdb", true);
    }

    @BeforeAll
    public void setup() throws IOException {
        engine = EngineBuilder.headers(NorthwindDuckdb.<ListWithSqlResult<HeaderInfo>>northwind(), NorthwindService.resolver(),
                new SqlQueryRenderer(java.time.ZoneId.of("UTC"))).valueFormat(new StableFormat(Locale.ROOT)).build();
    }

    @Test
    public void testSingleFile() throws IOException {
        Path kql = queriesRoot().resolve("expression/arithmetic.kql");
        TestUtil.test(kql, suffix(), engine, queriesRoot(), expectedCsv(), expectedSql());
    }
}
