package ai.koryki.kql;

import ai.koryki.antlr.AbstractReader;
import ai.koryki.databases.FileAsserter;
import ai.koryki.databases.SchemaUtil;
import ai.koryki.databases.northwind.NorthwindService;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.FunctionRenderer;
import ai.koryki.iql.LinkResolver;
import ai.koryki.scaffold.Util;
import ai.koryki.scaffold.domain.Attribute;
import ai.koryki.scaffold.domain.Entity;
import ai.koryki.scaffold.domain.Link;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
//        Path kql = Paths.get("src/test/resources/ai/koryki/kql/northwind/privatetest/identity/block_find_fetch_join_count_identity.kql");
//        test(kql);
//    }

    private void test(Path kql) throws IOException {

        LinkResolver resolverEn = NorthwindService.resolver(Locale.ENGLISH);
        ai.koryki.kql.KQLTranspiler transpilerEn = new ai.koryki.kql.KQLTranspiler(Files.readString(kql), resolverEn);

        KQLParser.QueryContext ctx = transpilerEn.getCtx();

        LinkResolver resolverDe = NorthwindService.resolver(Locale.GERMAN);

        Map<String, String> de2enLink = resolverDe.getModel().getLinks().stream().collect(Collectors.toMap(Link::getName, Link::getBase));

        Map<String, TableDictionary> de2enSchema = resolverDe.getModel().getEntities().stream().collect(Collectors.toMap(Entity::getName, (e) -> {

            TableDictionary t = new TableDictionary();
            t.setName(e.getTable());
            t.setColumns(e.getAttributes().stream().collect(Collectors.toMap(Attribute::getName, Attribute::getColumn)));
            return t;
        }));

        Map<String, String> en2deLink = Translator.swapMap(de2enLink);
        Map<String, TableDictionary> en2deSchema = Translator.swapDictionary(de2enSchema);

        DictionaryTranslator dt = new DictionaryTranslator(en2deLink, en2deSchema);

        ai.koryki.kql.KQLFormatter formatter2de = new ai.koryki.kql.KQLFormatter(ctx, transpilerEn.getDescription(), resolverEn, dt);
        String kqlDe = formatter2de.format();

        ai.koryki.kql.KQLTranspiler transpilerDe = new ai.koryki.kql.KQLTranspiler(kqlDe, resolverDe);

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
