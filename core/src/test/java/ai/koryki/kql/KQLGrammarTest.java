package ai.koryki.kql;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that the grammar is read from the classpath — and above all that {@link KQLGrammar}
 * overlooks no file.
 */
class KQLGrammarTest {

    @Test
    void readsTheGrammarFromTheClasspath() {

        String g = KQLGrammar.definition();

        assertFalse(g.isBlank(), "grammar is empty");
        // The rules have to be really there, not just the file headings.
        assertTrue(g.contains("parser grammar KQLParser"), "parser grammar is missing");
        assertTrue(g.contains("lexer grammar KQLLexer"), "lexer grammar is missing");
        assertTrue(g.contains("query"), "start rule is missing");

        // Each file's Apache header is stripped; it costs ~1 kB of prompt per file.
        assertFalse(g.contains("Apache License"), "license header not removed");
    }

    @Test
    void everyFoundFileOccursExactlyOnceInTheDocument() {

        String g = KQLGrammar.definition();
        List<String> files = KQLGrammar.files();

        assertFalse(files.isEmpty(), "no grammar file found on the classpath");
        for (String name : files) {
            assertEquals(1, count(g, "// " + name + ".g4"), name + " does not occur exactly once");
        }
    }

    /**
     * The actual guard: {@link KQLGrammar} knows a fixed list of names. If a file were added in the
     * grammar projects without being entered there, it would silently drop out of the document —
     * and nobody would notice. Here it is checked against the artifact actually shipped.
     *
     * <p>Listing is permissible here because the test classpath consists of directories and plain
     * jars — both can be listed. {@link KQLGrammar} itself must not do it: a Spring Boot fat jar is
     * possible there, and a jar inside a jar cannot be opened that way.
     */
    @Test
    void theNameListCoversEveryShippedFile() throws Exception {

        // getResources, not getResource: several classpath entries provide the package path —
        // core's own class directory (without .g4) comes before the grammar project's.
        List<URL> dirs = java.util.Collections.list(
                KQLGrammarTest.class.getClassLoader().getResources("ai/koryki/kql/"));
        assertFalse(dirs.isEmpty(), "grammar package path not on the classpath");

        java.util.SortedSet<String> found = new java.util.TreeSet<>();
        for (URL dir : dirs) {
            if ("file".equals(dir.getProtocol())) {
                try (Stream<Path> s = Files.list(Path.of(dir.toURI()))) {
                    s.map(p -> p.getFileName().toString()).forEach(n -> add(found, n));
                }
            } else if ("jar".equals(dir.getProtocol())) {
                // The normal case in a Gradle test run: kqlcore sits on the classpath as a jar.
                java.net.JarURLConnection c = (java.net.JarURLConnection) dir.openConnection();
                c.setUseCaches(false);   // otherwise the JarFile would belong to the shared cache
                try (java.util.jar.JarFile jar = c.getJarFile()) {
                    jar.stream().map(java.util.zip.ZipEntry::getName)
                            .filter(n -> n.startsWith("ai/koryki/kql/"))
                            .map(n -> n.substring("ai/koryki/kql/".length()))
                            .filter(n -> !n.contains("/"))
                            .forEach(n -> add(found, n));
                }
            }
        }
        List<String> shipped = List.copyOf(found);

        assertFalse(shipped.isEmpty(), "no .g4 in the shipped package path");
        assertEquals(shipped, KQLGrammar.files().stream().sorted().toList(),
                "KQLGrammar.FILES does not cover the shipped grammar files exactly");
    }

    /** Takes the name when it is a grammar file — without the extension. */
    private static void add(java.util.SortedSet<String> into, String fileName) {
        if (fileName.endsWith(".g4")) {
            into.add(fileName.substring(0, fileName.length() - ".g4".length()));
        }
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }
}
