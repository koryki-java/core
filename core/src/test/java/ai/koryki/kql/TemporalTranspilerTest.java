package ai.koryki.kql;

import ai.koryki.antlr.PanicException;
import ai.koryki.catalog.Util;
import ai.koryki.databases.FileAsserter;
import ai.koryki.databases.cases.TestUtil;
import ai.koryki.databases.temporal.duckdb.TemporalService;
import ai.koryki.iql.*;
import ai.koryki.iql.query.Out;
import ai.koryki.iql.query.Query;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class TemporalTranspilerTest {

    public static final String TEMPORAL_ROOT = "src/test/resources/ai/koryki/core/kql/temporal";
    public static final String EXPECTED_SQL = "src/test/resources/ai/koryki/core/expected/kql/temporal/sql";
    public static final String EXPECTED_IQL = "src/test/resources/ai/koryki/core/expected/kql/temporal/iql";
    public static final String SUFFIX = ".kql";

    private static LinkResolver resolver;

    @BeforeAll
    public static void readNorthwindDB() throws IOException {

        resolver = TemporalService.resolver();
    }

    static Stream<Path> testFiles() throws IOException {

        return Files.walk(Path.of(TEMPORAL_ROOT), FileVisitOption.FOLLOW_LINKS)
                .filter(p -> p.toString().endsWith(SUFFIX));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testFiles")
    void testEachFile(Path kql) throws IOException {

        test(kql);
    }

    @Test
    public void testSingleFile() throws IOException {
        Path p = Path.of("src/test/resources/ai/koryki/core/kql/temporal/privatetest/duration_literal.kql");
        test(p);
    }

    private static void test(Path kql) throws IOException {


        KQLTranspiler transpiler = KQLTranspiler.builder(new FileInputStream(kql.toFile()), resolver).build();

        String sql;
        if (checkInvalid(kql, transpiler)) {
            return;
        }

        String db = "duckdb";
        String k =transpiler.getKql();
        if (db != null && k.contains("// ignore=" + db)) {
            return;
        }


        sql = ignoreSkip(transpiler.getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, java.time.ZoneId.of("UTC"))));
        KQLParser.QueryContext ctx = transpiler.getCtx();
        Query query = transpiler.getQuery();
        List<Out> out = transpiler.getOut();
        String description = transpiler.getDescription();

        checkKql(kql, sql, ctx, description);
        checkIql(kql, query);
    }


    private static void checkKql(Path kql, String sql, KQLParser.QueryContext ctx, String description) throws IOException {
        Path expected = TestUtil.expected(kql, Path.of(TEMPORAL_ROOT), Path.of(EXPECTED_SQL), ".sql");
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
        Path iql = TestUtil.expected(kql, Path.of(TEMPORAL_ROOT), Path.of(EXPECTED_IQL), ".iql");
        //Path iql = FileAsserter.getSibling(kql, SUFFIX, ".iql");
        String iql2 = ignoreSkip(new IQLSerializer(query).toString());
        if (iql.toFile().canRead()) {

            String content = Files.readString(iql);
            FileAsserter.scriptAssert(content, iql2);
            IQLReader iqlReader = new IQLReader(content, true);
            IQLParser.QueryContext ctx = iqlReader.getCtx();
            assertNotNull(ctx);
            IQLQueryMapper i2b = new IQLQueryMapper(ctx, "");
            Query script = i2b.toScript();
            assertNotNull(script);

        } else {
            Util.text(iql2, iql.toFile());
        }
    }

    /** Strip {@code // ignore=<dialect>} / {@code -- ignore=<dialect>} test directives, like KqlTranspilerTest. */
    private static String ignoreSkip(String s) {
        return s.lines()
                .filter(line -> !line.startsWith("-- ignore="))
                .filter(line -> !line.startsWith("// ignore="))
                .collect(Collectors.joining(System.lineSeparator()));
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
        String sql2 = ignoreSkip(KQLTranspiler.builder(kql2, resolver).build().getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, java.time.ZoneId.of("UTC"))));
        FileAsserter.scriptAssert(sql, sql2);
    }

}
