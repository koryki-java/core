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
package ai.koryki.duckdb;

import ai.koryki.jdbc.JdbcDatabase;
import ai.koryki.jdbc.ResultProcessor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DuckdbDatabase<P extends ResultProcessor<?>> extends JdbcDatabase<P> {

    public DuckdbDatabase() {
        super("duckdb", connection(null));
    }

    public DuckdbDatabase(String file) {
        super("duckdb", connection(file));
    }

    public DuckdbDatabase(String name, Connection connection) {
        super(name, connection);
    }

    /*
     * Copy resource to tempFile and crate connection
     */
    public static Connection fromResource(String resource, Path tempFile) {

        if (!Files.exists(tempFile)){
            try (InputStream in = DuckdbDatabase.class.getResourceAsStream(resource)) {

                if (in == null) {
                    throw new IllegalStateException("DB resource missing");
                }
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return connection(tempFile.toString());
    }

    public static Connection connection(String file) {

        File f = new File(file);
        if (!f.canRead() || f.length() == 0) {
            throw new RuntimeException("missing db " + file + " " + f.length());
        }
        String url =  "jdbc:duckdb:" + file;
        try {
            return DriverManager.getConnection(url);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}