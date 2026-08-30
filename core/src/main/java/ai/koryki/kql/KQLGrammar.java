/*
 * Copyright 2025-2026 Johannes Zemlin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package ai.koryki.kql;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads the KQL grammar as a classpath resource and assembles it into one document.
 *
 * <p>The grammar was once a single {@code KQL.g4} file. Since the split into
 * {@code koryki-kqlcore} (without VISUALISE) and {@code koryki-kqlvisualise} (with), no archive
 * holds it whole: both artifacts ship several files under the same package path
 * {@code /ai/koryki/kql/} instead. That is why the grammar projects also publish their {@code .g4}
 * as a resource.
 *
 * <p>Which version applies is decided by the classpath alone. Both artifacts ship a subset of the
 * same six files, so neither list is hard-wired here: every known name is attempted and the
 * present ones are read. Including {@code koryki-kqlvisualise} yields six files,
 * {@code koryki-kqlcore} four — with no change to this class. Gradle rules out having both through
 * the shared capability.
 *
 * <p>Reading goes exclusively through {@link Class#getResourceAsStream(String)} with a full path,
 * never through a directory listing. That is deliberate: the main consumer is a Spring Boot fat
 * jar, where the grammar sits in an embedded jar under {@code BOOT-INF/lib}. The Boot classloader
 * finds individual resources there reliably; a directory listing via {@code FileSystems} fails on
 * the jar-in-jar URL.
 *
 * <p>The order is fixed and not a matter of taste: the result goes into an LLM prompt, whose prefix
 * cache only applies to a byte-identical beginning. Parser side first, then lexer side, each time
 * the base grammar before its fragments.
 */
public final class KQLGrammar {

    private static final String PACKAGE_PATH = "/ai/koryki/kql/";

    /**
     * Every file the grammar may consist of, in reading order. Absent ones are skipped — that is
     * the mechanism serving both artifacts without a case distinction.
     *
     * <p>When a file is added in the grammar projects it has to be entered here. So that this is
     * not silently forgotten, {@code KQLGrammarTest} checks this list against the resources
     * actually shipped.
     */
    private static final List<String> FILES = List.of(
            "KQLParser", "KQLRules", "KQLViz",
            "KQLLexer", "KQLTokens", "KQLVizLexer");

    /** The Apache header every file repeats — ~1 kB of prompt per file, saying nothing about KQL. */
    private static final Pattern LICENSE_HEADER = Pattern.compile("\\A\\s*/\\*.*?\\*/\\s*", Pattern.DOTALL);

    private static volatile String definition;

    private KQLGrammar() {
    }

    /**
     * The complete grammar as one document, the files separated by a {@code // <name>.g4} comment.
     * Read once and remembered: the content is fixed by the jar on the classpath.
     *
     * @throws IllegalStateException when the classpath holds no grammar file
     */
    public static String definition() {
        String local = definition;
        if (local == null) {
            synchronized (KQLGrammar.class) {
                local = definition;
                if (local == null) {
                    local = assemble();
                    definition = local;
                }
            }
        }
        return local;
    }

    /** The files actually present on the classpath — in reading order. */
    public static List<String> files() {

        List<String> found = new ArrayList<>();
        for (String name : FILES) {
            if (KQLGrammar.class.getResource(PACKAGE_PATH + name + ".g4") != null) {
                found.add(name);
            }
        }
        return List.copyOf(found);
    }

    private static String assemble() {

        StringBuilder sb = new StringBuilder();
        for (String name : FILES) {
            String body = read(name);
            if (body != null) {
                sb.append("// ").append(name).append(".g4\n").append(body).append("\n\n");
            }
        }

        if (sb.isEmpty()) {
            throw new IllegalStateException("no KQL grammar under " + PACKAGE_PATH
                    + " on the classpath — is the koryki-kqlcore or koryki-kqlvisualise jar missing?");
        }
        return sb.toString();
    }

    /** Reads one grammar file, or {@code null} when this artifact does not ship it. */
    private static String read(String name) {

        String resource = PACKAGE_PATH + name + ".g4";
        try (InputStream in = KQLGrammar.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return LICENSE_HEADER.matcher(body).replaceFirst("").strip();
        } catch (IOException e) {
            throw new UncheckedIOException("KQL grammar file " + resource + " not readable", e);
        }
    }
}
