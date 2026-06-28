package ai.koryki.snowflake.temporal;

import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.databases.cases.TestUtil;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.Engine;
import ai.koryki.catalog.Util;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Schema;
import ai.koryki.snowflake.SnowflakeUnavailable;
import ai.koryki.snowflake.iql.SqlQueryRenderer;
import ai.koryki.snowflake.northwind.NorthwindSnowflake;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

@SnowflakeUnavailable
public class TemporalDBEngineTest extends BaseEngineTest<HeaderInfo> {

    public static final String DB    = "/ai/koryki/snowflake/databases/temporal";
    public static final String MODEL = "/ai/koryki/snowflake/databases/temporal/model";

    public TemporalDBEngineTest() {
        super("snowflake");
    }

    @Override protected String queriesRoot() { return "src/test/resources/ai/koryki/snowflake/queries/temporal"; }
    @Override protected String expectedCsv() { return "src/test/resources/ai/koryki/snowflake/expected/temporal/csv"; }
    @Override protected String expectedSql() { return "src/test/resources/ai/koryki/snowflake/expected/temporal/sql"; }

    @BeforeAll
    public void readNorthwindDB() throws Exception {
        Locale locale = Locale.ENGLISH;
        Schema db = Util.db(DB);
        Model schema = Util.model(MODEL, locale);
        LinkResolver resolver = new LinkResolver(locale, db, schema, true);
        engine = Engine.builder(new NorthwindSnowflake<ListWithSqlResult<HeaderInfo>>(), resolver,
                new SqlQueryRenderer(java.time.ZoneId.of("UTC"))).format(new StableFormat(Locale.ROOT)).build();
    }

    @Test
    public void testSingleFile() throws IOException {
        Path kql = Path.of("src/test/resources/ai/koryki/snowflake/queries/temporal/privatetest/conformance/distances_rolling.kql");
        TestUtil.test(kql, suffix(), engine, Path.of(queriesRoot()), Path.of(expectedCsv()), Path.of(expectedSql()));
    }
}
