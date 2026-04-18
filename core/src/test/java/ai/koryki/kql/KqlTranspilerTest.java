package ai.koryki.kql;

import ai.koryki.antlr.PanicException;
import ai.koryki.databases.FileAsserter;
import ai.koryki.databases.northwind.NorthwindService;
import ai.koryki.iql.*;
import ai.koryki.iql.query.Out;
import ai.koryki.iql.query.Query;
import ai.koryki.scaffold.Util;
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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class KqlTranspilerTest {

    public static final String NORTHWIND_ROOT = "src/test/resources/ai/koryki/kql/northwind";
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

//    @Test
//    public void testSingle() throws IOException {
//        Path p = Path.of("src/test/resources/ai/koryki/kql/northwind/privatetest/window/window_join_with_aggregat_window.kql");
//        test(p);
//    }

    private static void test(Path kql) throws IOException {

        if (kql.toString().contains("block_find_fetch_join_count_identity")) {
            System.out.println();
        }

        KQLTranspiler transpiler = new KQLTranspiler(new FileInputStream(kql.toFile()), resolver);

        String sql;
        if (checkInvalid(kql, transpiler)) {
            return;
        }

        sql = transpiler.getSql(new SqlQueryRenderer());
        KQLParser.QueryContext ctx = transpiler.getCtx();
        Query query = transpiler.getQuery();
        List<Out> out = transpiler.getOut();
        String description = transpiler.getDescription();

        checkKql(kql, sql, ctx, description);
        checkIql(kql, query);
    }

    private static void checkKql(Path kql, String sql, KQLParser.QueryContext ctx, String description) throws IOException {
        Path expected = FileAsserter.getSibling(kql, SUFFIX, ".sql");
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
        Path iql = FileAsserter.getSibling(kql, SUFFIX, ".iql");
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
                transpiler.getSql(new SqlQueryRenderer());
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
        String sql2 = new KQLTranspiler(kql2, resolver).getSql(new SqlQueryRenderer());
        FileAsserter.scriptAssert(sql, sql2);
    }

}
