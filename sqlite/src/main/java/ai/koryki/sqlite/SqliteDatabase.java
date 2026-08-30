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
package ai.koryki.sqlite;

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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public class SqliteDatabase<P extends ResultProcessor<?>> extends JdbcDatabase<P> {

    // No session zone pinning, but decoder requires non-null ZoneId
    public SqliteDatabase(String name, Connection connection, ZoneId modelZone) {
        super(name, connection, modelZone);
    }

    /**
     * SQLite stores dates as ISO-8601 TEXT ("yyyy-MM-dd"). The xerial JDBC driver's {@code getDate()}
     * parses text values with the configured {@code dateStringFormat} (default "yyyy-MM-dd HH:mm:ss.SSS"),
     * which fails for date-only strings. Read via {@code getString()} and parse directly instead.
     */
    @Override
    protected LocalDate readDateColumn(ResultSet rs, int i) throws SQLException {
        String s = rs.getString(i);
        return s != null && !s.isEmpty() ? LocalDate.parse(s) : null;
    }

    /**
     * SQLite has no zone-aware type and its driver does not support
     * {@code getObject(.., OffsetDateTime.class)}; the INSTANT column comes back as the text it was
     * stored as. Parse that text (carrying its explicit offset) into the absolute instant.
     */
    @Override
    protected Instant readInstant(ResultSet rs, int i, int jdbcType) throws SQLException {
        String s = rs.getString(i);
        if (s == null) {
            return null;
        }
        // stored as "yyyy-MM-dd HH:mm:ss+00" — space separator and an hour-only UTC offset.
        // Normalize to ISO-8601 so OffsetDateTime carries the explicit offset.
        String iso = s.trim().replace(' ', 'T');
        if (iso.endsWith("+00") || iso.endsWith("-00")) {
            iso = iso.substring(0, iso.length() - 3) + "Z";
        }
        return OffsetDateTime.parse(iso).toInstant();
    }

    /*
     * Copy resource to tempFile and crate connection
     */
    public static Connection fromResource(String resource, Path tempFile, boolean case_sensitive_like) {

        if (Files.exists(tempFile)){
            tempFile.toFile().delete();
        }

        try (InputStream in = SqliteDatabase.class.getResourceAsStream(resource)) {

            if (in == null) {
                throw new IllegalStateException("DB resource missing");
            }
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return connection(tempFile.toString(),  case_sensitive_like);
    }

    public static Connection connection(String file, boolean case_sensitive_like) {

        File f = new File(file);
        if (!f.canRead() || f.length() == 0) {
            throw new RuntimeException("missing db " + file + " " + f.length());
        }
        String url =  "jdbc:sqlite:" + file + (case_sensitive_like ? "?case_sensitive_like=true" : "");
        try {
            return DriverManager.getConnection(url);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}