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

package ai.koryki.tools.verification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The record of what a release was tested on, written from the run and not by hand.
 *
 * <p>Read after {@code ./gradlew test --continue}, with the databases up: the counts of every
 * module come from its test result files, the version of a database from the file its suite wrote
 * ({@code DatabaseVersionRecorder}), the commit, the pins and the version of the release from git
 * and the build files. A dialect counts as <em>verified</em> only when tests ran, none was skipped,
 * none failed, and the database said its version: a class whose server cannot be reached is skipped
 * and the build is green anyway, which is the thing this record exists to show.
 *
 * <p>It refuses to write from results older than the commit, and from a tree with changes in
 * tracked files, because a record of another state than the one it names is worse than none.
 */
public final class VerificationRecord {

    /** The modules that need a database, in the order the record lists them. */
    static final List<String> DIALECTS =
            List.of(
                    "duckdb",
                    "sqlite",
                    "postgresql",
                    "mariadb",
                    "oracle",
                    "mssql",
                    "trino",
                    "snowflake");

    private static final Pattern SUITE =
            Pattern.compile(
                    "<testsuite [^>]*?tests=\"(\\d+)\" skipped=\"(\\d+)\" failures=\"(\\d+)\" errors=\"(\\d+)\"");

    /** One module: what its test result files add up to, and the database it ran against. */
    record Module(String name, int tests, int skipped, int failed, String database) {

        boolean dialect() {
            return DIALECTS.contains(name);
        }

        /** Verified: it ran, nothing was skipped or failed, and the database said which it was. */
        boolean verified() {
            return tests > 0 && skipped == 0 && failed == 0 && (!dialect() || database != null);
        }

        String status() {
            if (failed > 0) {
                return "**failed**";
            }
            if (tests == 0) {
                return "not run";
            }
            if (skipped > 0) {
                return "**not verified**: " + skipped + " skipped, the server was not reached";
            }
            if (dialect() && database == null) {
                return "**not verified**: the database did not say its version";
            }
            return "verified";
        }
    }

    /** What the record says about the state it was made from. */
    record Info(
            String version,
            String date,
            String commit,
            String northwindPin,
            String northwindCommit,
            String northwindTag,
            String java) {}

    private VerificationRecord() {}

    /** The modules below {@code root} that have test results, with their counts and databases. */
    static List<Module> read(Path root) throws IOException {
        List<Module> modules = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(root)) {
            for (Path dir : (Iterable<Path>) dirs.sorted()::iterator) {
                Path results = dir.resolve("build/test-results/test");
                if (!Files.isDirectory(results)) {
                    continue;
                }
                int tests = 0;
                int skipped = 0;
                int failed = 0;
                try (Stream<Path> files = Files.list(results)) {
                    for (Path file :
                            (Iterable<Path>)
                                    files.filter(p -> p.toString().endsWith(".xml"))::iterator) {
                        Matcher m = SUITE.matcher(Files.readString(file));
                        if (m.find()) {
                            tests += Integer.parseInt(m.group(1));
                            skipped += Integer.parseInt(m.group(2));
                            failed += Integer.parseInt(m.group(3)) + Integer.parseInt(m.group(4));
                        }
                    }
                }
                String name = dir.getFileName().toString();
                modules.add(new Module(name, tests, skipped, failed, database(dir, name)));
            }
        }
        modules.sort(
                Comparator.comparingInt(
                                (Module m) ->
                                        DIALECTS.contains(m.name())
                                                ? DIALECTS.indexOf(m.name())
                                                : 100)
                        .thenComparing(Module::name));
        return modules;
    }

    private static String database(Path module, String name) throws IOException {
        Path file = module.resolve("build/verification/" + name + ".properties");
        if (!Files.exists(file)) {
            return null;
        }
        Properties properties = new Properties();
        try (var in = Files.newInputStream(file)) {
            properties.load(in);
        }
        return properties.getProperty("version");
    }

    /** The record as the page it is written to. */
    static String render(Info info, List<Module> modules) {
        StringBuilder b = new StringBuilder();
        b.append(
                "<!-- generated by the verificationRecord task of the tools module; do not edit -->\n");
        b.append("<!-- verification: version=")
                .append(info.version())
                .append(" commit=")
                .append(info.commit())
                .append(" -->\n\n");
        b.append("# Verification of ").append(info.version()).append("\n\n");
        b.append("| | |\n|---|---|\n");
        b.append("| version | ").append(info.version()).append(" |\n");
        b.append("| date | ").append(info.date()).append(" |\n");
        b.append("| commit | `").append(info.commit()).append("` |\n");
        b.append("| northwind catalogs | pinned ").append(info.northwindPin());
        b.append(", submodule at `").append(info.northwindCommit()).append("`");
        if (info.northwindTag() != null) {
            b.append(" (").append(info.northwindTag()).append(")");
        }
        b.append(" |\n");
        b.append("| java | ").append(info.java()).append(" |\n\n");
        b.append("| module | tests | skipped | failed | database | status |\n");
        b.append("|---|---:|---:|---:|---|---|\n");
        for (Module m : modules) {
            b.append("| ")
                    .append(m.name())
                    .append(" | ")
                    .append(m.tests())
                    .append(" | ")
                    .append(m.skipped())
                    .append(" | ")
                    .append(m.failed())
                    .append(" | ")
                    .append(
                            m.dialect()
                                    ? (m.database() == null ? "not reached" : m.database())
                                    : "none")
                    .append(" | ")
                    .append(m.status())
                    .append(" |\n");
        }
        List<String> unverified =
                modules.stream().filter(m -> !m.verified()).map(Module::name).toList();
        b.append("\n");
        if (unverified.isEmpty()) {
            b.append("Every module is verified.\n");
        } else {
            b.append("Not verified: ").append(String.join(", ", unverified)).append(".\n");
        }
        return b.toString();
    }

    private static String run(Path dir, String... command)
            throws IOException, InterruptedException {
        Process p =
                new ProcessBuilder(command)
                        .directory(dir.toFile())
                        .redirectErrorStream(true)
                        .start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        p.waitFor();
        return out;
    }

    private static String property(Path file, String key) throws IOException {
        Matcher m =
                Pattern.compile("(?m)^" + Pattern.quote(key) + "\\s*=\\s*\"?([^\"\\s]+)\"?")
                        .matcher(Files.readString(file));
        return m.find() ? m.group(1) : "unknown";
    }

    /**
     * Usage: {@code VerificationRecord <core directory> [allow-dirty]}; writes {@code
     * docs/verification/<version>.md} and prints it.
     */
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        boolean allowDirty = args.length > 1 && "allow-dirty".equals(args[1]);

        String commit = run(root, "git", "rev-parse", "HEAD");
        long commitTime = Long.parseLong(run(root, "git", "log", "-1", "--format=%ct"));
        String dirty = run(root, "git", "status", "--porcelain", "--untracked-files=no");
        if (!dirty.isEmpty() && !allowDirty) {
            throw new IllegalStateException(
                    "tracked files have changes, so the record would name a state it was not made from:\n"
                            + dirty);
        }

        List<Module> modules = read(root);
        // a result file written before the commit is a result of another state
        try (Stream<Path> files =
                Files.walk(root, 5)
                        .filter(
                                p ->
                                        !root.relativize(p).startsWith("northwind-fixtures")
                                                && p.toString().contains("build/test-results/test/")
                                                && p.toString().endsWith(".xml"))) {
            long oldest =
                    files.mapToLong(p -> p.toFile().lastModified() / 1000)
                            .min()
                            .orElse(Long.MAX_VALUE);
            if (oldest < commitTime) {
                throw new IllegalStateException(
                        "some test results are older than the commit: run ./gradlew test --continue again");
            }
        }

        Path versionFile = root.resolve("gradle.properties");
        String version = property(versionFile, "version");
        Path libs = root.resolve("gradle/libs.versions.toml");
        Path fixtures = root.resolve("northwind-fixtures");
        Info info =
                new Info(
                        version,
                        LocalDate.now().toString(),
                        commit,
                        property(libs, "koryki-northwind"),
                        Files.isDirectory(fixtures)
                                ? run(fixtures, "git", "rev-parse", "--short", "HEAD")
                                : "none",
                        Files.isDirectory(fixtures) ? tag(fixtures) : null,
                        System.getProperty("java.version"));

        String page = render(info, modules);
        Path out = root.resolve("docs/verification/" + version + ".md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, page);
        System.out.println(page);
        System.out.println("written: " + out);
    }

    private static String tag(Path dir) throws IOException, InterruptedException {
        String tag = run(dir, "git", "describe", "--tags", "--exact-match");
        return tag.startsWith("fatal") || tag.isEmpty() ? null : tag;
    }
}
