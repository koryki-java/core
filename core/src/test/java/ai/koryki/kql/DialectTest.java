package ai.koryki.kql;

import ai.koryki.databases.FileAsserter;
import ai.koryki.iql.Identifier;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.scaffold.Util;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DialectTest {
    public static final String SUFFIX = ".kql";

    private static LinkResolver resolver;

    @BeforeAll
    public static void readNorthwindDB() {

        resolver = OraViewService.resolver();
    }


    @Test
    public void dialect_1() throws IOException {

        Path p = Paths.get("src/test/resources/ai/koryki/kql/dialect/dialect_1.kql");

        InputStream kql = new FileInputStream(p.toFile());

        //QLReader r = new KQLReader(kql);

        //KQLParser.QueryContext script = r.getCtx();

        //KQLQueryMapper l = new KQLQueryMapper(resolver, script, r.getDescription());
        //Query query = l.toBean();

        //new SchemaRule(resolver.getModel()).apply(query);

        KQLTranspiler transpiler = new KQLTranspiler(kql, resolver);

        String sql = transpiler.getSql(new SqlQueryRenderer(Identifier.quoted ));

        Path expectedSql = FileAsserter.getSibling(p, SUFFIX, ".sql");

        Util.text(sql, expectedSql.toFile());

    }

}
