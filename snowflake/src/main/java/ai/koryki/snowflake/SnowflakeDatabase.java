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
package ai.koryki.snowflake;

import ai.koryki.jdbc.JdbcDatabase;
import ai.koryki.jdbc.ResultProcessor;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Properties;

public class SnowflakeDatabase<C extends ResultProcessor<?>> extends JdbcDatabase<C> {

    public SnowflakeDatabase(String name, Connection conn) {
        this(name, conn, ZoneId.of("UTC"));
    }

    public SnowflakeDatabase(String name, Connection conn, ZoneId modelZone) {
        super(name, conn, modelZone);
    }

    /** Pin the session so TIMESTAMP_LTZ reads and now() are reproducible (TEMPORAL.md). */
    @Override
    protected String sessionTimeZoneStatement(ZoneId zone) {
        return "ALTER SESSION SET TIMEZONE = '" + zoneLiteral(zone) + "'";
    }

    /**
     * The Snowflake driver does not support {@code getObject(.., OffsetDateTime.class)}. A
     * {@code TIMESTAMP_TZ} comes back as a {@code SnowflakeTimestampWithTimezone} (a
     * {@link java.sql.Timestamp} whose epoch already holds the absolute instant), so
     * {@code getTimestamp().toInstant()} yields the correct point in time, JVM-zone-independently.
     */
    @Override
    protected Instant readInstant(ResultSet rs, int i, int jdbcType) throws SQLException {
        Timestamp ts = rs.getTimestamp(i);
        return ts != null ? ts.toInstant() : null;
    }

    public static Connection connection(String user, String url) throws Exception {
        return connection(user, loadPrivateKey(System.getProperty("snowflake.privatekey")), url);
    }

    public static Connection connection(String user, PrivateKey pk, String url) throws SQLException {

        Properties props = new Properties();
        props.setProperty("user", user);
        props.put("privateKey", pk);

        return DriverManager.getConnection(url, props);
    }

    public static PrivateKey loadPrivateKey(String filename) throws Exception {

        if (filename == null) {
            throw new IllegalArgumentException("private key filename is null");
        }
        String key = new String(Files.readAllBytes(Paths.get(filename)));

        // remove Header und Footer
        key = key.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(key);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }
}
