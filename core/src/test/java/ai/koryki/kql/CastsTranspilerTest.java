package ai.koryki.kql;

import ai.koryki.databases.cases.Fixtures;

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

    public static final Path SHARED = Fixtures.queries("casts");
    public static final Path LOCAL = Path.of("src/test/resources/ai/koryki/core/kql/casts");
    public static final Path EXPECTED_SQL = Fixtures.expected("kql", "casts").resolve("sql");
    public static final Path LOCAL_SQL = Path.of("src/test/resources/ai/koryki/core/expected/kql/casts/sql");
    public static final Path EXPECTED_IQL = Fixtures.expected("kql", "casts").resolve("iql");
    public static final Path LOCAL_IQL = Path.of("src/test/resources/ai/koryki/core/expected/kql/casts/iql");
    public static final String SUFFIX = ".kql";

    private static LinkResolver resolver;

    @BeforeAll
    public static void readNorthwindDB() throws IOException {

        resolver = NorthwindService.resolver();
    }
    /** Both roots: the shared fixtures and the ones only core has. */
    private static Stream<Path> walk(Path root) throws IOException {
        // The local root is absent wherever a schema has no core-owned fixtures.
        return Files.isDirectory(root)
                ? Files.walk(root, FileVisitOption.FOLLOW_LINKS).filter(p -> p.toString().endsWith(SUFFIX))
                : Stream.empty();
    }

    /** Which root applies is decided by the one the fixture was found under. */
    private static Path root(Path kql) { return kql.startsWith(LOCAL) ? LOCAL : SHARED; }

    private static Path sqlRoot(Path kql) { return kql.startsWith(LOCAL) ? LOCAL_SQL : EXPECTED_SQL; }

    private static Path iqlRoot(Path kql) { return kql.startsWith(LOCAL) ? LOCAL_IQL : EXPECTED_IQL; }


    static Stream<Path> testFiles() throws IOException {

        return Stream.concat(walk(SHARED), walk(LOCAL));
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
        Path expected = TestUtil.expected(kql, root(kql), sqlRoot(kql), ".sql");
        //Path expected = FileAsserter.getSibling(kql, SUFFIX, ".sql");
        File expectedFile = expected.toFile();
        if (expectedFile.canRead()) {
            String content = Files.readString(expected);
            FileAsserter.scriptAssert(content, sql);

            roundtrip(ctx, description, sql);
        } else {
            Fixtures.writeOrFail(sql, expectedFile);
        }
    }

    private static void checkIql(Path kql, Query query) throws IOException {
        Path iql = TestUtil.expected(kql, root(kql), iqlRoot(kql), ".iql");
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
            Fixtures.writeOrFail(new IQLSerializer(query).toString(), iql.toFile());
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
