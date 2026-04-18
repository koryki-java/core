package ai.koryki.iql;

import ai.koryki.antlr.PanicException;
import ai.koryki.databases.FileAsserter;
import ai.koryki.databases.northwind.NorthwindService;
import ai.koryki.iql.query.Out;
import ai.koryki.iql.query.Query;
import ai.koryki.iql.rules.Aggregate;
import ai.koryki.scaffold.Util;
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

import static org.junit.jupiter.api.Assertions.*;

public class IqlTranspilerTest {

    public static final String NORTHWIND_ROOT = "src/test/resources/ai/koryki/iql/northwind";
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
        IQLTranspiler transpiler = new IQLTranspiler(new Aggregate() {}, new FileInputStream(iql.toFile()), resolver);

        String sql;
        if (checkInvalid(iql, transpiler)) {
            return;
        }

        sql = transpiler.getSql(new SqlQueryRenderer());
        IQLParser.QueryContext ctx = transpiler.getCtx();
        Query query = transpiler.getQuery();
        List<Out> out = transpiler.getOut();
        String description = transpiler.getDescription();



        Path expected = getSibling(iql, SUFFIX, ".sql");
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
                transpiler.getSql(new SqlQueryRenderer());
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
        String sql2 = new IQLTranspiler(new Aggregate() {}, iql2, resolver).getSql(new SqlQueryRenderer());
        FileAsserter.scriptAssert(sql, sql2);
    }

    private static Path getSibling(Path file, String ext, String target) {
        return file.resolveSibling(file.getFileName().toString().replace(ext, target));
    }
}
