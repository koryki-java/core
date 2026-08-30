package ai.koryki.tools.docs;

import ai.koryki.antlr.Text;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.StandardFunctions;
import ai.koryki.kql.KQLTranspiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Golden-file docs: generated from the function catalog into the repo's docs/
 * tree. Missing files are written; existing files fail the build on drift
 * (delete and re-run to regenerate).
 */
class FunctionDocsTest {

    static Stream<DocDialects.Doc> dialects() {
        return DocDialects.all().stream();
    }

    @Test
    void categoryPagesAreUpToDate() throws IOException {
        Map<String, String> pages =
                new FunctionDocGenerator().categoryPages(StandardFunctions.registry(), dialectSql());
        for (Map.Entry<String, String> e : pages.entrySet()) {
            FunctionDocGenerator.sync(Path.of("../docs/functions/" + e.getKey()), e.getValue());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    void dialectPageIsUpToDate(DocDialects.Doc d) throws IOException {
        String md = new FunctionDocGenerator().dialectPage(d.name(), d.title(), d.order(),
                StandardFunctions.registry(), d.dialect().getFunctionRenderer(), DocDialects.names());
        FunctionDocGenerator.sync(Path.of("../docs/dialects/" + d.name() + ".md"), md);
    }

    /**
     * Transpiles every documentation sample to every dialect, offline — {@link KQLTranspiler} plus
     * {@link SqlQueryRenderer}, no database — so the SQL on a function page is exactly what the
     * transpiler emits and docs generation stays cheap and deterministic.
     *
     * <p>A dialect that cannot render a sample (a function it marks unsupported) simply contributes
     * no SQL; the {@code unsupported} map, read from each dialect's own registry, is what puts it on
     * the page. {@code // ignore=} markers are deliberately <em>not</em> honoured here: they suppress
     * result comparison, not SQL generation, and the generated SQL is still worth documenting.
     */
    private static FunctionDocGenerator.DialectSql dialectSql() throws IOException {
        Map<String, LinkResolver> resolvers = DocDialects.resolvers();
        Map<String, Map<String, String>> sqlBySlug = new LinkedHashMap<>();

        for (DocDialects.Sample sample : DocDialects.samples().values()) {
            LinkResolver resolver = resolvers.get(sample.db());
            if (resolver == null) {
                continue;
            }
            Map<String, String> byDialect = new LinkedHashMap<>();
            for (DocDialects.Doc doc : DocDialects.all()) {
                try {
                    String sql = KQLTranspiler.builder(sample.kql(), resolver).build()
                            .getSql(new SqlQueryRenderer(doc.dialect(), ZoneId.of("UTC")));
                    byDialect.put(doc.name(), sql.lines()
                            .filter(l -> !l.startsWith("-- ignore=") && !l.startsWith("// ignore="))
                            .collect(Collectors.joining(Text.NL)).strip());
                } catch (RuntimeException ex) {
                    // a function this dialect cannot render — it contributes no block
                }
            }
            if (!byDialect.isEmpty()) {
                sqlBySlug.put(sample.slug(), byDialect);
            }
        }
        return new FunctionDocGenerator.DialectSql(DocDialects.names(), sqlBySlug, unsupported());
    }

    /** Function name -> the dialects whose own registry marks it unsupported. */
    private static Map<String, Set<String>> unsupported() {
        FunctionRegistry canonical = StandardFunctions.registry();
        Map<String, Set<String>> unsupported = new TreeMap<>();
        for (List<FunctionDefinition> set : canonical.all()) {
            String name = set.get(0).getName();
            for (DocDialects.Doc doc : DocDialects.all()) {
                List<FunctionDefinition> defs = doc.dialect().getFunctionRenderer().overloads(name);
                // allMatch, not defs.get(0): the page-level claim is "this dialect does not have the
                // function", so it must not fire when only one *arity* is missing (MariaDB has
                // trim(string) but no trim(string, characters)). Reading the first overload gave the
                // right answer only because the supported one happens to register first.
                if (!defs.isEmpty() && defs.stream().allMatch(FunctionDefinition::isUnsupported)) {
                    unsupported.computeIfAbsent(name, k -> new TreeSet<>()).add(doc.name());
                }
            }
        }
        return unsupported;
    }
}
