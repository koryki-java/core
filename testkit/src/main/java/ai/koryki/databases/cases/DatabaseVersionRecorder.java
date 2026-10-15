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

import ai.koryki.jdbc.JdbcDatabase;
import ai.koryki.kql.HeaderInfo;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * Writes down which database a dialect's suite ran against, as the database says it.
 *
 * <p>A record of what a release was tested on has to come from the run, not from a person who types
 * the version of a container: this test asks the database, and writes {@code
 * build/verification/<module>.properties} in the module that ran it. A dialect test that extends it
 * carries the same availability condition as the rest of its suite, so a database that cannot be
 * reached is skipped and leaves no file, which the record then says as it is. The {@code
 * verificationRecord} task of the {@code tools} module reads the files.
 */
public abstract class DatabaseVersionRecorder {

    /** The name of the module, as its directory: the file is named after it. */
    protected abstract String module();

    /** What the database says its product and version are; one line. */
    protected abstract String version() throws Exception;

    @Test
    public void recordTheVersion() throws Exception {
        String version = version().strip();
        int newline = version.indexOf('\n');
        if (newline >= 0) {
            version = version.substring(0, newline).strip();
        }
        Properties properties = new Properties();
        properties.setProperty("module", module());
        properties.setProperty("version", version);
        properties.setProperty("recordedAt", Instant.now().toString());

        Path file = Path.of("build", "verification", module() + ".properties");
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            properties.store(out, "the database the suite of this module ran against");
        } catch (IOException e) {
            throw new IllegalStateException("cannot write " + file, e);
        }
    }

    /** The first column of the first row of a query. */
    protected static String first(
            JdbcDatabase<ListWithSqlResult<HeaderInfo>> database, String sql) {
        Object value = database.execute(sql, ListWithSqlResult::new).getRows().get(0).get(0);
        return String.valueOf(value);
    }
}
