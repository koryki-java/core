package ai.koryki.kql;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.databases.FileAsserter;
import ai.koryki.databases.cases.TestUtil;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.*;
import ai.koryki.iql.query.Out;
import ai.koryki.iql.query.Query;
import ai.koryki.catalog.Util;
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

public class KqlTranspilerTest {

    public static final String NORTHWIND_ROOT = "src/test/resources/ai/koryki/core/kql/northwind";
    public static final String EXPECTED_SQL = "src/test/resources/ai/koryki/core/expected/kql/northwind/sql";
    public static final String EXPECTED_IQL = "src/test/resources/ai/koryki/core/expected/kql/northwind/iql";
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

    @Test
    public void testSingleFile() throws IOException {
        Path p = Path.of("src/test/resources/ai/koryki/core/kql/northwind/privatetest/window/window_rank_revenue_per_category.kql");
        test(p);
    }

    private static void test(Path kql) throws IOException {


        if (checkInvalid(kql)) {
            return;
        }

        KQLTranspiler transpiler = KQLTranspiler.builder(new FileInputStream(kql.toFile()), resolver).build();
        String sql = transpiler.getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, java.time.ZoneId.of("UTC")));
        sql = ignoreSkip(sql);
        KQLParser.QueryContext ctx = transpiler.getCtx();
        Query query = transpiler.getQuery();
        List<Out> out = transpiler.getOut();
        String description = transpiler.getDescription();

        checkKql(kql, sql, ctx, description);
        checkIql(kql, query);
    }

    private static String ignoreSkip(String kqlDe) {
        // skip ignore-lines
        kqlDe = kqlDe.lines()
                .filter(line -> !line.startsWith("-- ignore="))
                .filter(line -> !line.startsWith("// ignore="))
                .collect(Collectors.joining(System.lineSeparator()));
        return kqlDe;
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
        String iql2 = new IQLSerializer(query).toString();
        iql2 = ignoreSkip(iql2);
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

    private static boolean checkInvalid(Path kql) throws IOException {
        if (!kql.getFileName().toString().startsWith("invalid")) {
            return false;
        }
        // Validation (arity / operator-family checks) needs the dialect catalog,
        // unlike the catalog-free transpile path used for valid queries.
        KQLTranspiler transpiler = KQLTranspiler.builder(new FileInputStream(kql.toFile()), resolver).functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer()).build();
        try {
            transpiler.getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, java.time.ZoneId.of("UTC")));
            fail("expected the invalid query to be rejected: " + kql);
        } catch (KorykiaiException expected) {
            // parse (PanicException) or validation (ValidateException) failure — both expected
        }
        return true;
    }

    private static void roundtrip(KQLParser.QueryContext ctx, String desc, String sql) {
        KQLFormatter bean2IQL = new KQLFormatter(ctx, desc);
        String kql2 = bean2IQL.format();
        String sql2 = KQLTranspiler.builder(kql2, resolver).build().getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, java.time.ZoneId.of("UTC")));
        sql2 = ignoreSkip(sql2);
        FileAsserter.scriptAssert(sql, sql2);
    }

}
