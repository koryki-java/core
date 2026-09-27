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

import ai.koryki.jdbc.ResultProcessor;
import ai.koryki.sqlite.SqliteDatabase;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.ZoneId;

public class NorthwindSqlite {

    public static String SQLITE = "/ai/koryki/sqlite/databases/northwind/northwind.sqlite";

    public static <P extends ResultProcessor<?>> SqliteDatabase<P> northwind() {
        return northwind(ZoneId.of("UTC"));
    }

    /** Build the Northwind SQLite database in the given model zone (default UTC). */
    public static <P extends ResultProcessor<?>> SqliteDatabase<P> northwind(ZoneId modelZone) {
        return new SqliteDatabase<>("northwind", fromResource(SQLITE, true), modelZone);
    }

    /**
     * Copy the database out of the jar to a file of this process and open it.
     *
     * <p>The file is named after the process id and lives in the temporary directory. It used to be
     * one path for everybody, {@code /tmp/korykiai.sqlite}: processes that ran together deleted it
     * under each other, and one that opened it while another was still copying found it empty
     * ({@code missing db /tmp/korykiai.sqlite 0}). A name of one's own leaves nothing to race over.
     * The file is removed when the JVM ends properly; a process that is killed leaves its copy
     * behind. {@code SqliteDatabase} is unchanged, so callers that choose their own file are
     * unaffected.
     */
    public static Connection fromResource(String resource, boolean case_sensitive_like) {
        Path file = file();
        file.toFile().deleteOnExit();
        return SqliteDatabase.fromResource(resource, file, case_sensitive_like);
    }

    /** The file of this process -- a name, not a promise that it exists. */
    static Path file() {
        return Path.of(
                System.getProperty("java.io.tmpdir"),
                "korykiai-" + ProcessHandle.current().pid() + ".sqlite");
    }
}
