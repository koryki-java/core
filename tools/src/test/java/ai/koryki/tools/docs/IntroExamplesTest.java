package ai.koryki.tools.docs;

import ai.koryki.antlr.Text;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.kql.KQLTranspiler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Every complete query written into a hand-authored category intro must actually transpile.
 *
 * <p>The per-function "Sample query" blocks are real fixtures and are transpiled by
 * {@link FunctionDocsTest}, but the intro fragments under
 * {@code resources/ai/koryki/tools/docs/intro/} are free prose — nothing used to check that the
 * KQL in them parses, and an example using an operator the grammar does not have shipped that
 * way. A block is picked up when its first line starts with {@code FIND}, i.e. it is a whole
 * query rather than a deliberately abstract fragment such as {@code FILTER a OR b AND c}.
 */
class IntroExamplesTest {

    private static final Path INTROS = Path.of("src/main/resources/ai/koryki/tools/docs/intro");

    /** One extractable example: where it came from (for the test name) and its KQL. */
    record Example(String where, String kql) {
        @Override
        public String toString() {
            return where;
        }
    }

    static Stream<Example> examples() throws IOException {
        List<Example> examples = new ArrayList<>();
        if (!Files.isDirectory(INTROS)) {
            return Stream.of();   // run outside the module: nothing to check
        }
        try (Stream<Path> files = Files.list(INTROS)) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".md")).sorted()::iterator) {
                examples.addAll(queries(p.getFileName().toString(), Files.readString(p)));
            }
        }
        return examples.stream();
    }

    /**
     * The whole-query blocks of one fragment. Both markdown block styles are read: an indented
     * block (four spaces) and a fenced one. Indentation is stripped so the KQL is handed to the
     * transpiler exactly as a reader would copy it.
     */
    private static List<Example> queries(String file, String markdown) {
        List<Example> found = new ArrayList<>();
        List<String> block = new ArrayList<>();
        int blockStart = 0;
        boolean fenced = false;
        String[] lines = markdown.split(Text.NL, -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("```")) {
                if (fenced) {
                    add(found, file, blockStart, block);
                }
                fenced = !fenced;
                blockStart = i + 2;
                continue;
            }
            boolean inBlock = fenced || line.startsWith("    ");
            if (inBlock) {
                if (block.isEmpty()) {
                    blockStart = i + 1;
                }
                block.add(fenced ? line : line.substring(4));
            } else if (!line.isBlank()) {
                add(found, file, blockStart, block);
            }
        }
        add(found, file, blockStart, block);
        return found;
    }

    private static void add(List<Example> found, String file, int line, List<String> block) {
        String kql = String.join("\n", block).strip();
        block.clear();
        if (kql.startsWith("FIND")) {
            found.add(new Example(file + ":" + line, kql));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("examples")
    void introExampleTranspiles(Example example) throws IOException {
        LinkResolver resolver = DocDialects.resolvers().get("northwind");
        KQLTranspiler.builder(example.kql(), resolver).build()
                .getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")));
    }
}
