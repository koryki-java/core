package ai.koryki.kql;

import ai.koryki.antlr.Text;
import ai.koryki.databases.cases.Fixtures;

import ai.koryki.databases.FileAsserter;
import ai.koryki.databases.cases.TestUtil;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.LinkResolver;
import ai.koryki.catalog.Util;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TranslateToGermanTest {


    public static final Path SHARED = Fixtures.queries("northwind");
    public static final Path LOCAL = Path.of("src/test/resources/ai/koryki/core/kql/northwind");

    public static final Path EXPECTED_TXT = Fixtures.expected("kql", "northwind").resolve("de");
    public static final Path LOCAL_TXT = Path.of("src/test/resources/ai/koryki/core/expected/kql/northwind/de");
    public static final Path EXPECTED_SQL = Fixtures.expected("kql", "northwind").resolve("sql");
    public static final Path LOCAL_SQL = Path.of("src/test/resources/ai/koryki/core/expected/kql/northwind/sql");

    public static final String SUFFIX = ".kql";
    private static Stream<Path> walk(Path root) throws IOException {
        // The local root is absent wherever a schema has no core-owned fixtures.
        return Files.isDirectory(root)
                ? Files.walk(root, FileVisitOption.FOLLOW_LINKS).filter(p -> p.toString().endsWith(SUFFIX))
                : Stream.empty();
    }

    private static Path root(Path kql) { return kql.startsWith(LOCAL) ? LOCAL : SHARED; }

    private static Path txtRoot(Path kql) { return kql.startsWith(LOCAL) ? LOCAL_TXT : EXPECTED_TXT; }

    private static Path sqlRoot(Path kql) { return kql.startsWith(LOCAL) ? LOCAL_SQL : EXPECTED_SQL; }


    static Stream<Path> testFiles() throws IOException {

        return Stream.concat(walk(SHARED), walk(LOCAL))
                // 'invalid' queries assert a validation failure (see KqlTranspilerTest);
                // they have no SQL golden and are not translation cases.
                .filter(p -> !p.getFileName().toString().startsWith("invalid"));
    }

    @Test
    public void testSingleFile() throws IOException {
        Path p = SHARED.resolve("window/window_rank_revenue_per_category.kql");
        test(p);
    }

    /**
     * Invariant: block IDs are user-invented names, never vocabulary. The block
     * is deliberately named like the English entity "customers" — its
     * definition and its reference must survive translation untouched, while
     * real entities (orders) are translated.
     */
    @Test
    public void blockIdIsNeverTranslated() throws IOException {
        String kql = """
                WITH customers AS (
                 FIND orders o
                 FETCH o.order_id id
                )
                FIND customers c
                FETCH c.id
                """;

        String kqlDe = translateToGerman(kql);

        org.junit.jupiter.api.Assertions.assertTrue(kqlDe.contains("WITH customers AS"),
                "block definition must keep its ID:\n" + kqlDe);
        org.junit.jupiter.api.Assertions.assertTrue(kqlDe.contains("FIND customers c"),
                "block reference must keep its ID:\n" + kqlDe);
        org.junit.jupiter.api.Assertions.assertTrue(kqlDe.contains("bestellungen"),
                "real entities must still be translated:\n" + kqlDe);

        assertTranspilesAgainstGermanModel(kqlDe);
    }

    /**
     * Invariant: an output field without a header is a model attribute and
     * must be translated — otherwise the translated query references a column
     * that does not exist in the target vocabulary.
     */
    @Test
    public void headerlessOutputAttributeIsTranslated() throws IOException {
        String kql = """
                FIND customers c
                FETCH c.company_name
                """;

        String kqlDe = translateToGerman(kql);

        org.junit.jupiter.api.Assertions.assertTrue(kqlDe.contains("c.firma"),
                "header-less output attribute must be translated:\n" + kqlDe);
        org.junit.jupiter.api.Assertions.assertFalse(kqlDe.contains("company_name"),
                "the English attribute name must not survive translation:\n" + kqlDe);

        assertTranspilesAgainstGermanModel(kqlDe);
    }

    /**
     * Invariant: function names are catalog vocabulary, not model vocabulary —
     * they survive translation untouched while their column arguments are
     * translated.
     */
    @Test
    public void functionNameIsNeverTranslated() throws IOException {
        String kql = """
                FIND customers c
                FETCH substr(c.company_name, 1, 3) abbrev
                """;

        String kqlDe = translateToGerman(kql);

        org.junit.jupiter.api.Assertions.assertTrue(kqlDe.contains("substr("),
                "the function name must survive translation:\n" + kqlDe);
        org.junit.jupiter.api.Assertions.assertTrue(kqlDe.contains("c.firma"),
                "the column argument must be translated:\n" + kqlDe);
        org.junit.jupiter.api.Assertions.assertFalse(kqlDe.contains("company_name"),
                "the English attribute name must not survive translation:\n" + kqlDe);

        assertTranspilesAgainstGermanModel(kqlDe);
    }

    /**
     * Invariant: operators are syntax, not vocabulary — the operator keyword
     * survives translation while its operands are translated.
     */
    @Test
    public void operatorIsNeverTranslated() throws IOException {
        String kql = """
                FIND customers c
                FILTER c.company_name LIKE 'A%'
                FETCH c.company_name
                """;

        String kqlDe = translateToGerman(kql);

        org.junit.jupiter.api.Assertions.assertTrue(kqlDe.contains("LIKE 'A%'"),
                "the operator and its literal must survive translation:\n" + kqlDe);
        org.junit.jupiter.api.Assertions.assertTrue(kqlDe.contains("c.firma"),
                "the operand column must be translated:\n" + kqlDe);

        assertTranspilesAgainstGermanModel(kqlDe);
    }

    /**
     * Invariant: a string literal is data, never vocabulary — it survives
     * translation verbatim even when it spells an entity name ("orders").
     */
    @Test
    public void stringLiteralIsNeverTranslated() throws IOException {
        String kql = """
                FIND customers c
                FILTER c.company_name = 'orders'
                FETCH c.company_name
                """;

        String kqlDe = translateToGerman(kql);

        org.junit.jupiter.api.Assertions.assertTrue(kqlDe.contains("'orders'"),
                "the string literal must survive translation verbatim:\n" + kqlDe);
        org.junit.jupiter.api.Assertions.assertFalse(kqlDe.contains("'bestellungen'"),
                "a literal must not be translated as vocabulary:\n" + kqlDe);

        assertTranspilesAgainstGermanModel(kqlDe);
    }

    /**
     * Invariant: an explicit output header is a user-invented name (like a
     * block ID) and survives translation, while the underlying attribute is
     * still translated.
     */
    @Test
    public void explicitOutputHeaderIsNeverTranslated() throws IOException {
        String kql = """
                FIND customers c
                FETCH c.company_name shortname
                """;

        String kqlDe = translateToGerman(kql);

        org.junit.jupiter.api.Assertions.assertTrue(kqlDe.contains("shortname"),
                "the user-invented output header must survive translation:\n" + kqlDe);
        org.junit.jupiter.api.Assertions.assertTrue(kqlDe.contains("c.firma"),
                "the underlying attribute must still be translated:\n" + kqlDe);
        org.junit.jupiter.api.Assertions.assertFalse(kqlDe.contains("company_name"),
                "the English attribute name must not survive translation:\n" + kqlDe);

        assertTranspilesAgainstGermanModel(kqlDe);
    }

    private static String translateToGerman(String kql) throws IOException {
        LinkResolver resolverEn = NorthwindService.resolver(Locale.ENGLISH);
        KQLTranspiler transpilerEn = KQLTranspiler.builder(kql, resolverEn).build();

        LinkResolver resolverDe = NorthwindService.resolver(Locale.GERMAN);
        DictionaryTranslator dictionary = LinkResolver.dictionary(resolverEn.getModel(), resolverDe.getModel());
        return new KQLFormatter(transpilerEn.getCtx(), transpilerEn.getDescription(), resolverEn, dictionary).format();
    }

    private static void assertTranspilesAgainstGermanModel(String kqlDe) throws IOException {
        LinkResolver resolverDe = NorthwindService.resolver(Locale.GERMAN);
        assertNotNull(KQLTranspiler.builder(kqlDe, resolverDe).build().getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, java.time.ZoneId.of("UTC"))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testFiles")
    void testEachFile(Path kql) throws IOException {

        test(kql);
    }

    private void test(Path kql) throws IOException {
        // Rendered with DuckDB; a sample flagged ignore=duckdb (unsupported function) can't render.
        if (Files.readString(kql).contains("// ignore=duckdb")) {
            return;
        }

        LinkResolver resolverEn = NorthwindService.resolver(Locale.ENGLISH);
        ai.koryki.kql.KQLTranspiler transpilerEn = new ai.koryki.kql.KQLTranspiler(Files.readString(kql), resolverEn);

        KQLParser.QueryContext ctx = transpilerEn.getCtx();
        LinkResolver resolverDe = NorthwindService.resolver(Locale.GERMAN);
        DictionaryTranslator dictionary = LinkResolver.dictionary(resolverEn.getModel(), resolverDe.getModel()) ;
        KQLFormatter formatter2de = new KQLFormatter(ctx, transpilerEn.getDescription(), resolverEn, dictionary);
        String kqlDe = formatter2de.format();

        kqlDe = ignoreSkip(kqlDe);

        KQLTranspiler transpilerDe = KQLTranspiler.builder(kqlDe, resolverDe).build();

        String sql;
        try {
            sql = transpilerDe.getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, java.time.ZoneId.of("UTC")));
        } catch (RuntimeException e) {
            if (ai.koryki.databases.cases.TestUtil.cannotRunOnThisDialect(e)) {
                return;
            }
            throw e;
        }
        assertNotNull(sql);
        Path expectedKql = TestUtil.expected(kql, root(kql), txtRoot(kql), "_de.txt");

        File expectedKqlFile = expectedKql.toFile();
        Path expectedSql = TestUtil.expected(kql, root(kql), sqlRoot(kql), ".sql");

        if (expectedKqlFile.canRead()) {
            String content = Files.readString(expectedKql);
            content = ignoreSkip(content);
            FileAsserter.scriptAssert(content, kqlDe);

            content = Files.readString(expectedSql);
            FileAsserter.scriptAssert(content, sql);
        } else {
            Fixtures.writeOrFail(kqlDe, expectedKqlFile);
        }
    }

    private static String ignoreSkip(String kqlDe) {
        // skip ignore-lines
        kqlDe = kqlDe.lines()
                .filter(line -> !line.startsWith("// ignore="))
                .collect(Collectors.joining(Text.NL));
        return kqlDe;
    }
}
