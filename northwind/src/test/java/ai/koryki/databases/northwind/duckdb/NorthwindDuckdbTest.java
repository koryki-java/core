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
package ai.koryki.databases.northwind.duckdb;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * That programs which start together no longer take the database file from under each other.
 *
 * <p>Before the process id was in the file name, six started at once left one alive and five ended
 * with {@code Could not set lock on file}.
 */
class NorthwindDuckdbTest {

    @Test
    void theFileIsNamedAfterTheProcessAndLivesInTheTemporaryDirectory() {
        Path file = NorthwindDuckdb.file();

        assertAll(
                () ->
                        assertEquals(
                                "korykiai-" + ProcessHandle.current().pid() + ".duckdb",
                                file.getFileName().toString()),
                () ->
                        assertEquals(
                                Path.of(System.getProperty("java.io.tmpdir")), file.getParent()));
    }

    /**
     * Real processes, because the lock that used to kill them is taken between processes: inside
     * one JVM DuckDB hands out the database it already has open for a path and never notices a
     * conflict.
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

        List<String> files = answers.stream().map(a -> a.substring(a.indexOf(' ') + 1)).toList();
        assertAll(
                () ->
                        assertTrue(
                                answers.stream().allMatch(a -> a.startsWith("14 ")),
                                answers.toString()),
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
}
