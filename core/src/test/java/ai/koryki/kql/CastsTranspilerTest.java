package ai.koryki.kql;

import ai.koryki.antlr.PanicException;
import ai.koryki.databases.FileAsserter;
import ai.koryki.databases.cases.TestUtil;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.*;
import ai.koryki.iql.query.Out;
import ai.koryki.iql.query.Query;
import ai.koryki.catalog.Util;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class CastsTranspilerTest {

    public static final String NORTHWIND_ROOT = "src/test/resources/ai/koryki/core/kql/casts";
    public static final String EXPECTED_SQL = "src/test/resources/ai/koryki/core/expected/kql/casts/sql";
    public static final String EXPECTED_IQL = "src/test/resources/ai/koryki/core/expected/kql/casts/iql";
    public static final String SUFFIX = ".kql";

    private static LinkResolver resolver;

    @BeforeAll
    public static void readNorthwindDB() throws IOException {

        resolver = NorthwindService.resolver();
    }

    static Stream<Path> testFiles() throws IOException {

        return Files.walk(Path.of(NORTHWIND_ROOT), FileVisitOption.FOLLOW_LINKS)
                .filter(p -> p.toString().endsWith(SUFFIX));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testFiles")
    void testEachFile(Path kql) throws IOException {

        test(kql);
    }

    private static void test(Path kql) throws IOException {


        KQLTranspiler transpiler = KQLTranspiler.builder(new FileInputStream(kql.toFile()), resolver).build();

        String sql;
        if (checkInvalid(kql, transpiler)) {
            return;
        }

        sql = transpiler.getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, java.time.ZoneId.of("UTC")));
        KQLParser.QueryContext ctx = transpiler.getCtx();
        Query query = transpiler.getQuery();
        List<Out> out = transpiler.getOut();
        String description = transpiler.getDescription();

        checkKql(kql, sql, ctx, description);
        checkIql(kql, query);
    }

    private static void checkKql(Path kql, String sql, KQLParser.QueryContext ctx, String description) throws IOException {
        Path expected = TestUtil.expected(kql, Path.of(NORTHWIND_ROOT), Path.of(EXPECTED_SQL), ".sql");
        //Path expected = FileAsserter.getSibling(kql, SUFFIX, ".sql");
        File expectedFile = expected.toFile();
        if (expectedFile.canRead()) {
            String content = Files.readString(expected);
            FileAsserter.scriptAssert(content, sql);

            roundtrip(ctx, description, sql);
        } else {
            Util.text(sql, expectedFile);
        }
    }

    private static void checkIql(Path kql, Query query) throws IOException {
        Path iql = TestUtil.expected(kql, Path.of(NORTHWIND_ROOT), Path.of(EXPECTED_IQL), ".iql");
        //Path iql = FileAsserter.getSibling(kql, SUFFIX, ".iql");
        if (iql.toFile().canRead()) {

            String content = Files.readString(iql);
            FileAsserter.scriptAssert(content, new IQLSerializer(query).toString());
            IQLReader iqlReader = new IQLReader(content, true);
            IQLParser.QueryContext ctx = iqlReader.getCtx();
            assertNotNull(ctx);
            IQLQueryMapper i2b = new IQLQueryMapper(ctx, "");
            Query script = i2b.toScript();
            assertNotNull(script);

        } else {
            Util.text(new IQLSerializer(query).toString(), iql.toFile());
        }
    }

    private static boolean checkInvalid(Path kql, KQLTranspiler transpiler) {
        if (kql.getFileName().toString().startsWith("invalid")) {
            try {
                transpiler.getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, java.time.ZoneId.of("UTC")));
                fail();
            } catch (PanicException e) {
                return true;
            }
        }
        return false;
    }

    private static void roundtrip(KQLParser.QueryContext ctx, String desc, String sql) {
        KQLFormatter bean2IQL = new KQLFormatter(ctx, desc);
        String kql2 = bean2IQL.format();
        String sql2 = KQLTranspiler.builder(kql2, resolver).build().getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, java.time.ZoneId.of("UTC")));
        FileAsserter.scriptAssert(sql, sql2);
    }

}
