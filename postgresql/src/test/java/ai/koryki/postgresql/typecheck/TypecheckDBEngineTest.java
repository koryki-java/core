package ai.koryki.postgresql.typecheck;

import ai.koryki.databases.cases.BaseEngineTest;
import ai.koryki.databases.cases.ListWithSqlResult;
import ai.koryki.databases.cases.StableFormat;
import ai.koryki.kql.HeaderInfo;
import ai.koryki.databases.cases.TestUtil;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.Engine;
import ai.koryki.postgresql.PostgreSQLUnavailable;
import ai.koryki.postgresql.iql.SqlQueryRenderer;
import ai.koryki.postgresql.northwind.NorthwindPostgresql;
import ai.koryki.catalog.Util;
import ai.koryki.catalog.domain.Model;
import ai.koryki.catalog.schema.Schema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Locale;

@PostgreSQLUnavailable
public class TypecheckDBEngineTest extends BaseEngineTest<HeaderInfo> {

    public static final String DB    = "/ai/koryki/postgresql/databases/typecheck";
    public static final String MODEL = "/ai/koryki/postgresql/databases/typecheck/model";

    public TypecheckDBEngineTest() {
        super("postgresql");
    }

    @Override protected String queriesRoot() { return "src/test/resources/ai/koryki/postgresql/queries/typecheck"; }
    @Override protected String expectedCsv() { return "src/test/resources/ai/koryki/postgresql/expected/typecheck/csv"; }
    @Override protected String expectedSql() { return "src/test/resources/ai/koryki/postgresql/expected/typecheck/sql"; }

    @BeforeAll
    public void readNorthwindDB() throws IOException, SQLException {
        Locale locale = Locale.ENGLISH;
        Schema db = Util.db(DB);
        Model schema = Util.model(MODEL, locale);
        LinkResolver resolver = new LinkResolver(locale, db, schema, true);
        engine = Engine.builder(new NorthwindPostgresql<ListWithSqlResult<HeaderInfo>>(), resolver,
                new SqlQueryRenderer(java.time.ZoneId.of("UTC"))).format(new StableFormat(Locale.ROOT)).build();
    }

    @Test
    public void testSingleFile() throws IOException {
        Path kql = Path.of("src/test/resources/ai/koryki/postgresql/queries/typecheck/privatetest/smallint_tointeger.kql");
        TestUtil.test(kql, suffix(), engine, Path.of(queriesRoot()), Path.of(expectedCsv()), Path.of(expectedSql()));
    }
}
