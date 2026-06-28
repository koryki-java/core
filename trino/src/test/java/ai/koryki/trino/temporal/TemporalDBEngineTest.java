package ai.koryki.trino.temporal;

import ai.koryki.catalog.Util;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.databases.cases.TestUtil;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.Engine;
import ai.koryki.trino.TrinoUnavailable;
import ai.koryki.trino.iql.SqlQueryRenderer;
import ai.koryki.trino.northwind.NorthwindTrino;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Locale;

@TrinoUnavailable
public class TemporalDBEngineTest extends BaseEngineTest<HeaderInfo> {

    public static final String DB    = "/ai/koryki/trino/databases/temporal";
    public static final String MODEL = "/ai/koryki/trino/databases/temporal/model";

    public TemporalDBEngineTest() {
        super("trino");
    }

    @Override protected String queriesRoot() { return "src/test/resources/ai/koryki/trino/queries/temporal"; }
    @Override protected String expectedCsv() { return "src/test/resources/ai/koryki/trino/expected/temporal/csv"; }
    @Override protected String expectedSql() { return "src/test/resources/ai/koryki/trino/expected/temporal/sql"; }

    @BeforeAll
    public void readNorthwindDB() throws IOException, SQLException {
        Locale locale = Locale.ENGLISH;
        Schema db = Util.db(DB);
        Model schema = Util.model(MODEL, locale);
        LinkResolver resolver = new LinkResolver(locale, db, schema, true);
        engine = Engine.builder(new NorthwindTrino<ListWithSqlResult<HeaderInfo>>(), resolver,
                new SqlQueryRenderer(java.time.ZoneId.of("UTC"))).format(new StableFormat(Locale.ROOT)).build();
    }

    @Test
    public void testSingleFile() throws IOException {
        Path kql = Path.of("src/test/resources/ai/koryki/trino/queries/temporal/privatetest/fetch_time_sec_from_midnight.kql");
        TestUtil.test(kql, suffix(), engine, Path.of(queriesRoot()), Path.of(expectedCsv()), Path.of(expectedSql()), "trino");
    }
}
