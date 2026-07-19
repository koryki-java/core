package ai.koryki.sqlite.temporal;

import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.databases.cases.TestUtil;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.Engine;
import ai.koryki.sqlite.iql.SqlQueryRenderer;
import ai.koryki.sqlite.northwind.NorthwindSqlite;
import ai.koryki.catalog.CatalogLoader;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Schema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Locale;

public class TemporalDBEngineTest extends BaseEngineTest<HeaderInfo> {

    public static final String DB    = "/ai/koryki/sqlite/databases/temporal";
    public static final String MODEL = "/ai/koryki/sqlite/databases/temporal/model";

    public TemporalDBEngineTest() {
        super("sqlite");
    }

    @Override protected String queriesRoot() { return "src/test/resources/ai/koryki/sqlite/queries/temporal"; }
    @Override protected String expectedCsv() { return "src/test/resources/ai/koryki/sqlite/expected/temporal/csv"; }
    @Override protected String expectedSql() { return "src/test/resources/ai/koryki/sqlite/expected/temporal/sql"; }

    @BeforeAll
    public void readNorthwindDB() throws IOException, SQLException {
        Locale locale = Locale.ENGLISH;
        Schema db = CatalogLoader.db(DB);
        Model schema = CatalogLoader.model(MODEL, locale);
        LinkResolver resolver = new LinkResolver(locale, db, schema, true);
        engine = Engine.builder(NorthwindSqlite.<ListWithSqlResult<HeaderInfo>>northwind(), resolver,
                new SqlQueryRenderer(java.time.ZoneId.of("UTC"))).format(new StableFormat(Locale.ROOT)).build();
    }

    @Test
    public void testSingleFile() throws IOException {
        Path kql = Path.of("src/test/resources/ai/koryki/sqlite/queries/temporal/privatetest/fetch_time_sec_from_midnight.kql");
        TestUtil.test(kql, suffix(), engine, Path.of(queriesRoot()), Path.of(expectedCsv()), Path.of(expectedSql()), "sqlite");
    }
}
