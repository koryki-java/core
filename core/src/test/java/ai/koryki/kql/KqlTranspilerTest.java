package ai.koryki.kql;

import ai.koryki.antlr.Text;
import ai.koryki.databases.cases.Fixtures;

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

    public static final Path SHARED = Fixtures.queries("northwind");
    public static final Path LOCAL = Path.of("src/test/resources/ai/koryki/core/kql/northwind");
    public static final Path EXPECTED_SQL = Fixtures.expected("kql", "northwind").resolve("sql");
    public static final Path LOCAL_SQL = Path.of("src/test/resources/ai/koryki/core/expected/kql/northwind/sql");
    public static final Path EXPECTED_IQL = Fixtures.expected("kql", "northwind").resolve("iql");
    public static final Path LOCAL_IQL = Path.of("src/test/resources/ai/koryki/core/expected/kql/northwind/iql");
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

    @Test
    public void testSingleFile() throws IOException {
        Path p = SHARED.resolve("window/window_rank_revenue_per_category.kql");
        test(p);
    }

    private static void test(Path kql) throws IOException {


        if (checkInvalid(kql)) {
            return;
        }

        KQLTranspiler transpiler = KQLTranspiler.builder(new FileInputStream(kql.toFile()), resolver).build();
        // These tests render with DuckDB; a sample using a function DuckDB declares unsupported
        // can't render. TestUtil.unsupportedOnThisDialect derives that from the catalog, so the
        // fixture no longer needs a hand-written ignore=duckdb marker.
        if (transpiler.getKql().contains("// ignore=duckdb")) {
            return;
        }
        // Pass-through is an escape hatch, not something the corpus should lean on: every fixture
        // must use catalogued functions only. This also guards the unknown-function warning against
        // false positives — anything the grammar handles structurally must not be reported here.
        if (!transpiler.warnings().isEmpty()) {
            fail(kql + " produced warnings: " + transpiler.warnings());
        }
        String sql;
        try {
            sql = transpiler.getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, java.time.ZoneId.of("UTC")));
        } catch (RuntimeException e) {
            if (ai.koryki.databases.cases.TestUtil.cannotRunOnThisDialect(e)) {
                return;   // DuckDB declares one of this fixture's functions unsupported
            }
            throw e;
        }
        sql = ignoreSkip(sql);
        KQLParser.QueryContext ctx = transpiler.getCtx();
        Query query = transpiler.getQuery();
        List<Out> out = transpiler.getOut();
        String description = transpiler.getDescription();

        checkKql(kql, sql, ctx, description, query);
        checkIql(kql, query, sql);
    }

    private static String ignoreSkip(String kqlDe) {
        // skip ignore-lines
        kqlDe = kqlDe.lines()
                .filter(line -> !line.startsWith("-- ignore="))
                .filter(line -> !line.startsWith("// ignore="))
                .collect(Collectors.joining(Text.NL));
        return kqlDe;
    }

    private static void checkKql(Path kql, String sql, KQLParser.QueryContext ctx, String description, Query original) throws IOException {


        Path expected = TestUtil.expected(kql, root(kql), sqlRoot(kql), ".sql");
        //Path expected = FileAsserter.getSibling(kql, SUFFIX, ".sql");
        File expectedFile = expected.toFile();
        if (expectedFile.canRead()) {
            String content = Files.readString(expected);
            FileAsserter.scriptAssert(content, sql);

            roundtrip(ctx, description, sql, original);
        } else {
            Fixtures.writeOrFail(sql, expectedFile);
        }
    }

    private static void checkIql(Path kql, Query query, String sql) throws IOException {
        Path iql = TestUtil.expected(kql, root(kql), iqlRoot(kql), ".iql");
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

            // the round-trip must not change the SQL: transpiling the serialized IQL has to
            // reproduce the KQL-rendered SQL byte for byte (catches lost parenthesization,
            // window sort order, set-operation grouping and non-idempotent rules)
            IQLTranspiler roundtrip = IQLTranspiler.builder(content, resolver).build();
            String sql2 = ignoreSkip(roundtrip.getSql(
                    new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, java.time.ZoneId.of("UTC"))));
            FileAsserter.scriptAssert(sql, sql2);

        } else {
            Fixtures.writeOrFail(iql2, iql.toFile());
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

    /**
     * Re-formats the query to KQL, transpiles that, and requires the result to be the same query.
     *
     * <p>Compared on <em>two</em> levels, because SQL alone is too coarse: a fetch label reaches no
     * SQL, so {@code KQLFormatter} could drop it — and did — without moving a single character here.
     * The IQL keeps everything the model carries, so comparing it catches whatever the formatter
     * loses on the way out, for every construct rather than the one that was noticed.
     */
    private static void roundtrip(KQLParser.QueryContext ctx, String desc, String sql, Query original) {
        KQLFormatter bean2IQL = new KQLFormatter(ctx, desc);
        String kql2 = bean2IQL.format();
        KQLTranspiler again = KQLTranspiler.builder(kql2, resolver).build();
        String sql2 = again.getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, java.time.ZoneId.of("UTC")));
        sql2 = ignoreSkip(sql2);
        FileAsserter.scriptAssert(sql, sql2);
        if (original != null) {
            FileAsserter.scriptAssert(new IQLSerializer(original).toString(),
                    new IQLSerializer(again.getQuery()).toString());
        }
    }

}
