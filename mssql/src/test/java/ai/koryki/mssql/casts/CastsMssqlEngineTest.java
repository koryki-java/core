package ai.koryki.mssql.casts;

import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.kql.EngineBuilder;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.mssql.iql.SqlQueryRenderer;
import ai.koryki.mssql.northwind.NorthwindMssql;
import ai.koryki.mssql.MssqlUnavailable;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Locale;

/**
 * The casts corpus on SQL Server. It ran on DuckDB alone, which is how {@code to_integer(4.5)} could
 * answer 4 for so long although the catalog promises a half-way value rounds away from zero: the
 * single dialect checking the rounding was the one getting it wrong. The fixtures query northwind
 * tables, so this is the northwind engine pointed at the casts fixture directory.
 */
@MssqlUnavailable
public class CastsMssqlEngineTest extends BaseEngineTest<HeaderInfo> {

    @Override
    protected String schema() {
        return "casts";
    }

    public CastsMssqlEngineTest() {
        super("mssql", true);
    }

    @BeforeAll
    public void setup() throws IOException, SQLException {
        engine = EngineBuilder.headers(new NorthwindMssql<ListWithSqlResult<HeaderInfo>>(java.time.ZoneId.of("UTC")), NorthwindService.resolver(),
                new SqlQueryRenderer(java.time.ZoneId.of("UTC"))).valueFormat(new StableFormat(Locale.ROOT)).build();
    }
}
