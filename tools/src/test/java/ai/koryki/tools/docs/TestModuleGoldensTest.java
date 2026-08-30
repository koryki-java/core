package ai.koryki.tools.docs;

import ai.koryki.databases.cases.Fixtures;
import ai.koryki.antlr.Text;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.kql.KQLTranspiler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Offline generator for the shared test-module per-dialect SQL goldens
 * ({@code koryki-java/test/.../expected/<dialect>/<schema>/sql/docs/*.sql}).
 * Transpiles every documentation sample to every dialect without a live
 * database — the same {@link SqlQueryRenderer} the engine tests use in their
 * SQL-golden step, so the output is byte-identical. Opt-in, since it writes
 * into the sibling project: run with {@code -Dgoldens.write=true}.
 *
 * <p>A sample is skipped for a dialect when it carries a {@code // ignore=<dialect>}
 * line or fails to transpile (a function the dialect does not support), mirroring
 * the coverage of the hand-run goldens.
 */
class TestModuleGoldensTest {

    

    @Test
    void generateTestModuleGoldens() throws IOException {
        if (!Boolean.getBoolean("goldens.write")) {
            return;
        }
        Map<String, LinkResolver> resolvers = DocDialects.resolvers();
        Map<String, SqlDialect> dialects = DocDialects.byName();
        int written = 0;
        for (Map.Entry<String, LinkResolver> se : resolvers.entrySet()) {
            String schema = se.getKey();
            Path samplesRoot = Fixtures.queries(schema).resolve("docs");
            if (!Files.isDirectory(samplesRoot)) {
                continue;
            }
            // The base now comes from -Dtest.root instead of a parent chain across a resolved
            // symlink -- which no longer exists.
            Path testBase = Fixtures.corpus();
            try (Stream<Path> walk = Files.walk(samplesRoot, FileVisitOption.FOLLOW_LINKS)) {
                for (Path kql : (Iterable<Path>) walk.filter(p -> p.toString().endsWith(".kql"))::iterator) {
                    String kqlText = Files.readString(kql);
                    String relSql = samplesRoot.relativize(kql).toString().replaceFirst("\\.kql$", ".sql");
                    for (Map.Entry<String, SqlDialect> de : dialects.entrySet()) {
                        String dialect = de.getKey();
                        if (kqlText.contains("// ignore=" + dialect)) {
                            continue;
                        }
                        String sql;
                        try {
                            sql = KQLTranspiler.builder(kqlText, se.getValue()).build()
                                    .getSql(new SqlQueryRenderer(de.getValue(), ZoneId.of("UTC")));
                        } catch (RuntimeException ex) {
                            continue; // a function the dialect does not support — no golden
                        }
                        sql = sql.lines()
                                .filter(l -> !l.startsWith("-- ignore=") && !l.startsWith("// ignore="))
                                .collect(Collectors.joining(Text.NL));
                        Path out = testBase.resolve("expected").resolve(dialect)
                                .resolve(schema).resolve("sql").resolve("docs").resolve(relSql);
                        Files.createDirectories(out.getParent());
                        Files.writeString(out, sql);
                        written++;
                    }
                }
            }
        }
        System.out.println("test-module goldens written: " + written);
    }
}
