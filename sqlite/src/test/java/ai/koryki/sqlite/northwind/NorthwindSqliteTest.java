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
package ai.koryki.sqlite.northwind;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * That programs which start together no longer take the database file from under each other.
 *
 * <p>The Northwind SQLite database is copied out of the jar before it is opened, and that copy used
 * to go to one path shared by every process on the machine, {@code /tmp/korykiai.sqlite}: each one
 * deleted whatever was there, wrote its own copy and opened what it found. Test JVMs that run side
 * by side -- module tasks in parallel, a local run next to a CI run -- therefore delete, half-write
 * and open the same file. The same fix as for the DuckDB copy: a name of one's own, so that there
 * is nothing to race over.
 */
class NorthwindSqliteTest {

    @Test
    void theFileIsNamedAfterTheProcessAndLivesInTheTemporaryDirectory() {
        Path file = NorthwindSqlite.file();

        assertAll(
                () ->
                        assertEquals(
                                "korykiai-" + ProcessHandle.current().pid() + ".sqlite",
                                file.getFileName().toString()),
                () ->
                        assertEquals(
                                Path.of(System.getProperty("java.io.tmpdir")), file.getParent()));
    }

    /**
     * Real processes: a thread of this JVM would share this JVM's file name whatever the name was,
     * and it is between processes that a file can be replaced under a reader.
     */
    @Test
    void processesStartedAtTheSameTimeDoNotTakeTheFileFromEachOther(@TempDir Path logs)
            throws Exception {
        int processes = 5;
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");

        List<Process> started = new ArrayList<>();
        List<File> outputs = new ArrayList<>();
        for (int i = 0; i < processes; i++) {
            File output = logs.resolve("process-" + i + ".log").toFile();
            outputs.add(output);
            started.add(
                    new ProcessBuilder(java, "-cp", classpath, DatabaseChild.class.getName())
                            .redirectErrorStream(true)
                            .redirectOutput(output)
                            .start());
        }

        List<String> answers = new ArrayList<>();
        for (int i = 0; i < processes; i++) {
            Process process = started.get(i);
            assertTrue(process.waitFor(90, TimeUnit.SECONDS), "process " + i + " did not finish");
            String output = Files.readString(outputs.get(i).toPath());
            assertEquals(0, process.exitValue(), "process " + i + " died:\n" + output);
            answers.add(output.trim());
        }

        // what a single, undisturbed start gets: the yardstick for "the same database everywhere"
        long expected = categories();
        List<String> files = answers.stream().map(a -> a.substring(a.indexOf(' ') + 1)).toList();
        assertAll(
                () ->
                        assertTrue(
                                answers.stream().allMatch(a -> a.startsWith(expected + " ")),
                                "every process saw " + expected + " categories: " + answers),
                () ->
                        assertEquals(
                                processes,
                                files.stream().distinct().count(),
                                "every process had a file of its own: " + files),
                // A proper shutdown removes it, so a start does not leave a copy behind.
                () ->
                        assertTrue(
                                files.stream().noneMatch(f -> Files.exists(Path.of(f))),
                                "files left behind: " + files));
    }

    private static long categories() throws Exception {
        try (Connection connection = NorthwindSqlite.fromResource(NorthwindSqlite.SQLITE, true);
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT count(*) FROM categories")) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
