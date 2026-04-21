package ai.koryki.kql;

import ai.koryki.databases.FileAsserter;
import ai.koryki.databases.cases.CSVAssert;
import ai.koryki.databases.cases.StableFormatInfo;
import ai.koryki.databases.northwind.NorthwindService;
import ai.koryki.databases.northwind.duckdb.NorthwindDuckdb;
import ai.koryki.duckdb.SqlQueryRenderer;
import ai.koryki.iql.LinkResolver;
import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.jdbc.Database;
import ai.koryki.jdbc.ListResult;
import ai.koryki.scaffold.Util;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class EngineTest {

    public static final String NORTHWIND_ROOT = "src/test/resources/ai/koryki/kql/northwind";
    public static final String SUFFIX = ".kql";

    private static LinkResolver resolver;
    private static Database<ListResult<ColumnInfo>> database;
    private static Engine<ColumnInfo, ListResult<ColumnInfo>> engine;

    @BeforeAll
    public static void readNorthwindDB() throws IOException {

        resolver = NorthwindService.resolver();
        database = NorthwindDuckdb.northwind();
        engine = new Engine<>(database, resolver, new SqlQueryRenderer(), StableFormatInfo::new);
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
//    void test1() throws IOException {
//
//        Path kql = Paths.get("../../core/core/src/test/resources/ai/koryki/kql/northwind/privatetest/window/window_in_block.kql");
//        test(kql);
//    }


    private static void test(Path kql) throws IOException {

        Path sibling = FileAsserter.getSibling(kql, SUFFIX, ".csv");
        File expectedFile = sibling.toFile();

        String query = Files.readString(kql);
        CSVAssert csv = new CSVAssert(engine, query, kql.toFile().getName());
        String result = csv.getCsv();
        if (expectedFile.canRead()) {
            String expected = Files.readString(sibling);
            csv.check(result, expected);
        } else {
            Util.text(result, expectedFile);
        }
    }
}
