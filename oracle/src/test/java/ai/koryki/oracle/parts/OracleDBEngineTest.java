package ai.koryki.oracle.parts;

import ai.koryki.catalog.Util;
import ai.koryki.databases.FileAsserter;
import ai.koryki.databases.cases.TestUtil;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.LinkResolver;
import ai.koryki.kql.KQLTranspiler;
import ai.koryki.oracle.iql.SqlQueryRenderer;
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
import java.util.stream.Stream;

public class OracleDBEngineTest {

    private static final String QUERIES     = "src/test/resources/ai/koryki/oracle/queries/parts";
    private static final String EXPECTED_SQL = "src/test/resources/ai/koryki/oracle/expected/parts/sql";

    private static LinkResolver resolver;

    @BeforeAll
    public static void setup() throws IOException {
        resolver = NorthwindService.resolver();
    }

    static Stream<Path> testFiles() throws IOException {
        return Files.walk(Path.of(QUERIES), FileVisitOption.FOLLOW_LINKS)
                .filter(p -> p.toString().endsWith(".kql"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testFiles")
    void testEachFile(Path kql) throws IOException {
        check(kql);
    }

    @Test
    public void testSingleFile() throws IOException {
        check(Path.of(QUERIES + "/to_date_parts.kql"));
    }

    private static void check(Path kql) throws IOException {
        KQLTranspiler transpiler = KQLTranspiler.builder(new FileInputStream(kql.toFile()), resolver).build();
        String sql = transpiler.getSql(new SqlQueryRenderer(java.time.ZoneId.of("UTC")));

        Path expected = TestUtil.expected(kql, Path.of(QUERIES), Path.of(EXPECTED_SQL), ".sql");
        File expectedFile = expected.toFile();
        if (expectedFile.canRead()) {
            FileAsserter.scriptAssert(Files.readString(expected), sql);
        } else {
            Util.text(sql, expectedFile);
        }
    }
}
