package ai.koryki.sqlite.casts;

import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.kql.EngineBuilder;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.sqlite.iql.SqlQueryRenderer;
import ai.koryki.sqlite.northwind.NorthwindSqlite;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Locale;

/**
 * The casts corpus on SQLite. It ran on DuckDB alone, which is how {@code to_integer(4.5)} could
 * answer 4 for so long although the catalog promises a half-way value rounds away from zero: the
 * single dialect checking the rounding was the one getting it wrong. The fixtures query northwind
 * tables, so this is the northwind engine pointed at the casts fixture directory.
 */
public class CastsSqliteEngineTest extends BaseEngineTest<HeaderInfo> {

    @Override
    protected String schema() {
        return "casts";
    }

    public CastsSqliteEngineTest() {
        super("sqlite", true);
    }

    @BeforeAll
    public void setup() throws IOException, SQLException {
        engine = EngineBuilder.headers(NorthwindSqlite.<ListWithSqlResult<HeaderInfo>>northwind(), NorthwindService.resolver(),
                new SqlQueryRenderer(java.time.ZoneId.of("UTC"))).valueFormat(new StableFormat(Locale.ROOT)).build();
    }
}
