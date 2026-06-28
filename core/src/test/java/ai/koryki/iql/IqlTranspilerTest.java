package ai.koryki.iql;

import ai.koryki.antlr.PanicException;
import ai.koryki.databases.FileAsserter;
import ai.koryki.databases.cases.TestUtil;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class IqlTranspilerTest {

    public static final String NORTHWIND_ROOT = "src/test/resources/ai/koryki/core/iql/northwind";
    public static final String EXPECTED = "src/test/resources/ai/koryki/core/expected/iql/northwind/sql";
    public static final String SUFFIX = ".iql";

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
    void testEachFile(Path iql) throws IOException {

        test(iql);
    }

    private static void test(Path iql) throws IOException {
        IQLTranspiler transpiler = IQLTranspiler.builder(new FileInputStream(iql.toFile()), resolver).build();

        String sql;
        if (checkInvalid(iql, transpiler)) {
            return;
        }

        sql = transpiler.getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, java.time.ZoneId.of("UTC")));
        IQLParser.QueryContext ctx = transpiler.getCtx();
        Query query = transpiler.getQuery();

        Path expected =  TestUtil.expected(iql, Path.of(NORTHWIND_ROOT), Path.of(EXPECTED), ".sql");
        File expectedFile = expected.toFile();
        if (expectedFile.canRead()) {
            String content = Files.readString(expected);
            FileAsserter.scriptAssert(content, sql);

            roundtrip(query, sql);
        } else {
            Util.text(sql, expectedFile);

        }
    }

    private static boolean checkInvalid(Path iql, IQLTranspiler transpiler) {
        if (iql.getFileName().toString().startsWith("invalid")) {
            try {
                transpiler.getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, java.time.ZoneId.of("UTC")));
                fail();
            } catch (PanicException e) {
                return true;
            }
        }
        return false;
    }

    private static void roundtrip(Query query, String sql) {
        IQLSerializer bean2IQL = new IQLSerializer(query);
        String iql2 = bean2IQL.toString();
        String sql2 = IQLTranspiler.builder(iql2, resolver).build().getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, java.time.ZoneId.of("UTC")));
        FileAsserter.scriptAssert(sql, sql2);
    }

//    private static Path getSibling(Path file, String ext, String target) {
//        return file.resolveSibling(file.getFileName().toString().replace(ext, target));
//    }
}
