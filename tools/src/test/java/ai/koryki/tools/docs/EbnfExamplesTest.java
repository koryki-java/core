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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The worked examples in {@code docs/KQL_EBNF.md} must still say what the transpiler does.
 *
 * <p>{@link IntroExamplesTest} checks that the KQL in the category intros parses. This checks
 * more, because these examples make a claim: each one names an outcome after a {@code →}, and half
 * of them are deliberately rejected queries whose error message is the point. An example that only
 * had to parse would let the right-hand side drift arbitrarily far from the truth — which is what a
 * reference document must not do.
 *
 * <p><b>Format.</b> A block fenced as {@code ```kql} holds one or more examples. An example is a
 * KQL query followed by a line whose first non-blank characters are {@code →}; everything after the
 * arrow is the expectation. A query may span several lines, so a new example starts at the first
 * line after an arrow line.
 *
 * <pre>
 * ```kql
 * FIND orders o FETCH o.order_date - 30d d
 *   →  o.order_date - INTERVAL '30 day' AS d
 * ```
 * </pre>
 *
 * <p><b>What the expectation means.</b> One rule for both directions, so an example cannot quietly
 * change category: the text must appear in the rendered SQL if the query transpiles, and in the
 * error message if it does not. Writing an expectation that names an error therefore fails as soon
 * as the query starts working, and vice versa — the case that matters, because the examples
 * documenting a rejection are the ones a future change is most likely to invalidate silently.
 *
 * <p>Whitespace is normalised on both sides before comparing; nothing else is. No ellipses: an
 * expectation shortened with {@code …} would pass against almost anything.
 */
class EbnfExamplesTest {

    private static final Path DOC = Path.of("../docs/KQL_EBNF.md");

    /** The arrow that separates a query from what it is claimed to produce. */
    private static final String ARROW = "→";

    record Example(String where, String kql, String expected) {
        @Override
        public String toString() {
            return where + ": " + kql.lines().findFirst().orElse("");
        }
    }

    static Stream<Example> examples() throws IOException {
        if (!Files.isRegularFile(DOC)) {
            // Run from outside the module the relative path does not resolve. Failing loudly beats
            // reporting zero examples as a pass -- an empty run looks exactly like a green one.
            throw new IllegalStateException("not found: " + DOC.toAbsolutePath().normalize()
                    + " — run this from the tools module, as Gradle does");
        }
        List<Example> found = new ArrayList<>();
        String[] lines = Files.readString(DOC).split(Text.NL, -1);
        boolean inBlock = false;
        List<String> query = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("```")) {
                if (inBlock && !query.isEmpty()) {
                    fail("unterminated example at " + DOC.getFileName() + ":" + (start + 1));
                }
                inBlock = line.startsWith("```kql");
                continue;
            }
            if (!inBlock || line.isBlank()) {
                continue;
            }
            if (line.strip().startsWith(ARROW)) {
                if (query.isEmpty()) {
                    fail("expectation without a query at " + DOC.getFileName() + ":" + (i + 1));
                }
                found.add(new Example(DOC.getFileName() + ":" + (start + 1),
                        String.join(Text.NL, query), line.strip().substring(ARROW.length()).strip()));
                query.clear();
                continue;
            }
            if (query.isEmpty()) {
                start = i;
            }
            query.add(line);
        }
        if (!query.isEmpty()) {
            fail("example without an expectation at " + DOC.getFileName() + ":" + (start + 1));
        }
        return found.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("examples")
    void theExampleStillSaysWhatTheTranspilerDoes(Example example) throws IOException {
        LinkResolver resolver = DocDialects.resolvers().get("northwind");
        String actual;
        String kind;
        try {
            actual = KQLTranspiler.builder(example.kql(), resolver)
                    .functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer()).build()
                    .getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")));
            kind = "rendered SQL";
        } catch (RuntimeException e) {
            actual = e.getMessage() == null ? e.toString() : e.getMessage();
            kind = e.getClass().getSimpleName();
        }
        assertTrue(flat(actual).contains(flat(example.expected())),
                example.where() + Text.NL
                        + "  query:    " + example.kql().replace(Text.NL, " ") + Text.NL
                        + "  expected: " + example.expected() + Text.NL
                        + "  " + kind + ": " + flat(actual));
    }

    /** Compare on content, not on layout: the renderer indents, the document does not. */
    private static String flat(String s) {
        return s.replaceAll("\\s+", " ").strip();
    }
}
