package ai.koryki.kql;

import ai.koryki.databases.FileAsserter;
import ai.koryki.databases.northwind.NorthwindService;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.FunctionRenderer;
import ai.koryki.iql.LinkResolver;
import ai.koryki.scaffold.Util;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TranslateTest {

    public static final String NORTHWIND_ROOT = "src/test/resources/ai/koryki/kql/northwind";

    public static final String SUFFIX = ".kql";

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
//        Path kql = Paths.get("/Users/johanneszemlin/IdeaProjects/koryki-java/core/core/src/test/resources/ai/koryki/kql/northwind/simple/customersmorethan10ordersin2023.kql");
//        test(kql);
//    }

    private void test(Path kql) throws IOException {

        LinkResolver resolverEn = NorthwindService.resolver(Locale.ENGLISH);
        ai.koryki.kql.KQLTranspiler transpilerEn = new ai.koryki.kql.KQLTranspiler(Files.readString(kql), resolverEn);

        KQLParser.QueryContext ctx = transpilerEn.getCtx();

        LinkResolver resolverDe = NorthwindService.resolver(Locale.GERMAN);

//        Map<String, String> de2enLink = resolverDe.getModel().getLinks().stream().collect(Collectors.toMap(Link::getName, Link::getBase));
//
//        Map<String, TableDictionary> de2enSchema = resolverDe.getModel().getEntities().stream().collect(Collectors.toMap(Entity::getName, (e) -> {
//
//            TableDictionary t = new TableDictionary();
//            t.setName(e.getDialectTable());
//            t.setColumns(e.getAttributes().stream().collect(Collectors.toMap(Attribute::getName, Attribute::getColumn)));
//            return t;
//        }));
//
//        Map<String, String> en2deLink = LinkResolver.swapMap(de2enLink);
//        Map<String, TableDictionary> en2deSchema = LinkResolver.swapDictionary(de2enSchema);

        DictionaryTranslator dt = LinkResolver.dictionary(resolverEn.getModel(), resolverDe.getModel()) ;

        KQLFormatter formatter2de = new KQLFormatter(ctx, transpilerEn.getDescription(), resolverEn, dt);
        String kqlDe = formatter2de.format();

        KQLTranspiler transpilerDe = new KQLTranspiler(kqlDe, resolverDe);

        String sql = transpilerDe.getSql(new SqlQueryRenderer(new FunctionRenderer() {}));
        assertNotNull(sql);

        Path expectedKql = FileAsserter.getSibling(kql, SUFFIX, "_de.txt");
        File expectedKqlFile = expectedKql.toFile();
        Path expectedSql = FileAsserter.getSibling(kql, SUFFIX, ".sql");

        if (expectedKqlFile.canRead()) {
            String content = Files.readString(expectedKql);
            FileAsserter.scriptAssert(content, kqlDe);

            content = Files.readString(expectedSql);
            FileAsserter.scriptAssert(content, sql);
        } else {
            Util.text(kqlDe, expectedKqlFile);
        }
    }
}
