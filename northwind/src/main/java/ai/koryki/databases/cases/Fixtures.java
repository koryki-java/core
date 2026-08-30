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
package ai.koryki.databases.cases;

import ai.koryki.antlr.Text;
import ai.koryki.catalog.Util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The switch that decides whether a missing golden file is written or reported as a failure.
 *
 * <p>The fixture tests used to rewrite a missing {@code .sql} or {@code .csv} file silently. That
 * is convenient while creating a fixture, but it has an unpleasant property: a golden deleted by
 * accident, or one whose path has moved, is recreated on the next run from the <em>current</em>
 * behaviour — and the run is green. The test then only confirms itself. That is exactly how a
 * regression goes unnoticed.
 *
 * <p>From now on, only whoever asks for it writes:
 *
 * <pre>./gradlew test -Dfixtures.write=true</pre>
 *
 * <p>Without the switch a missing golden fails and names the expected path. Same shape as the
 * {@code docs.write} and {@code goldens.write} switches already in use.
 */
public final class Fixtures {

    /** The system property that enables writing. */
    public static final String PROPERTY = "fixtures.write";

    /** The system property pointing at the fixture project. Mandatory — there is no fallback. */
    public static final String ROOT_PROPERTY = "test.root";

    /**
     * The system property carrying the path from the project root down to the corpus. Mandatory,
     * for the same reason {@link #ROOT_PROPERTY} is.
     *
     * <p>It is a property rather than a constant because {@code build.gradle} needs the same path,
     * to register the corpus as a task input — without that, a changed fixture leaves the test
     * {@code UP-TO-DATE}. Groovy cannot read a Java constant, so the path used to be written in both
     * places. Renaming the fixture project moved it three times in one day, and the Gradle copy was
     * left behind every time; it now supplies the value instead of repeating it.
     */
    public static final String CORPUS_PROPERTY = "test.corpus";

    private static volatile Path root;

    /** The path below {@link #root()} where the fixture module keeps the corpus. */
    private static String base() {
        String value = System.getProperty(CORPUS_PROPERTY);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "The property " + CORPUS_PROPERTY + " is not set — it says where below "
                            + ROOT_PROPERTY + " the fixture module keeps the corpus." + Text.NL
                            + "It is passed by the test task; a run outside Gradle has to set it.");
        }
        return value;
    }

    /**
     * The fixture project's root directory, from {@code -Dtest.root}.
     *
     * <p>Deliberately without a fallback to a guessed path: a wrongly guessed directory would show
     * up as "no fixtures found", and a run with zero fixtures looks like a passing one. Better an
     * abort that says what is missing.
     *
     * <p>The subtree is checked too — otherwise a typo in the path would appear as a suddenly
     * vanished corpus rather than as what it is.
     */
    public static Path root() {

        Path local = root;
        if (local != null) {
            return local;
        }

        Path resolved = resolve(System.getProperty(ROOT_PROPERTY));
        root = resolved;
        return resolved;
    }

    /**
     * The resolution alone, separate from the system property, so both failure cases are testable:
     * for a mandatory setting the message is the entire user interface.
     */
    static Path resolve(String value) {

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "The property " + ROOT_PROPERTY + " is not set — it says where the shared"
                            + " test fixtures live." + Text.NL
                            + "Set it with: ./gradlew test -D" + ROOT_PROPERTY + "=/path/to/koryki-java/northwind"
                            + Text.NL
                            + "Permanently: put " + ROOT_PROPERTY + "=... into gradle.properties.");
        }

        Path candidate = Path.of(value);
        if (!Files.isDirectory(candidate.resolve(base()))) {
            throw new IllegalStateException(
                    ROOT_PROPERTY + "=" + value + " does not point at the fixture project:" + Text.NL
                            + candidate.resolve(base()) + " is not a directory." + Text.NL
                            + "Expected the root directory of koryki-java/northwind.");
        }

        return candidate;
    }

    /**
     * The corpus directory: the root plus the path the fixture module keeps it under.
     *
     * <p>Exists so the corpus path is stated once. {@code BASE} used to be a private constant, so callers needing the
     * directory itself — rather than one of the {@code queries}/{@code expected} sub-paths — spelled
     * the literal out again. Renaming the fixture module then broke them one at a time, each in a
     * later phase than the last: the Gradle input first, then a test, then another.
     *
     * <p>One copy is unavoidable: {@code build.gradle} registers the same directory as a task input
     * so a changed fixture is not treated as up to date, and Groovy cannot read this constant.
     */
    public static Path corpus() {
        return root().resolve(base());
    }

    /** The queries of a schema — dialect-independent, every dialect reads the same ones. */
    public static Path queries(String schema) {
        return root().resolve(base()).resolve("queries/kql").resolve(schema);
    }

    /** The expected SQL: different per dialect, hence filed under the dialect's name. */
    public static Path expectedSql(String dialect, String schema) {
        return expected(dialect, schema).resolve("sql");
    }

    /**
     * The expected result as CSV — <em>not</em> per dialect but shared: the same query must return
     * the same rows everywhere. That is the whole point of the suite.
     */
    public static Path expectedCsv(String schema) {
        return expected("kql", schema).resolve("csv");
    }

    /** The violation goldens: per dialect, because what a dialect rejects is its own property. */
    public static Path expectedViolations(String dialect, String schema) {
        return expected(dialect, schema).resolve("violations");
    }

    /** An expectation directory below {@code expected/<dialect>/<schema>}. */
    public static Path expected(String dialect, String schema) {
        return root().resolve(base()).resolve("expected").resolve(dialect).resolve(schema);
    }

    /** The IQL sources and their expectations. */
    public static Path iql(String schema) {
        return root().resolve(base()).resolve("iql").resolve(schema);
    }

    private Fixtures() {
    }

    /**
     * Whether missing golden files may be written. Besides the system property,
     * {@code FIXTURES_WRITE} from the environment is accepted — handy for CI runs that pass no JVM
     * arguments through.
     */
    public static boolean writeEnabled() {
        return Boolean.getBoolean(PROPERTY)
                || "true".equalsIgnoreCase(System.getenv("FIXTURES_WRITE"));
    }

    /**
     * Writes the golden when writing is enabled, and fails otherwise.
     *
     * @param content what would go into the file — that is, the current behaviour
     * @param target  the expected but absent file
     */
    public static void writeOrFail(Object content, File target) {
        if (!writeEnabled()) {
            throw new AssertionError(
                    "Golden missing: " + target
                            + Text.NL
                            + "It is no longer written automatically, or the test would only confirm"
                            + " the current behaviour."
                            + Text.NL
                            + "Create it with: ./gradlew test -D" + PROPERTY + "=true");
        }
        Util.text(content, target);
    }
}
