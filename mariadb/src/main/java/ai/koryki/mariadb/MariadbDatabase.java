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
package ai.koryki.mariadb;

import ai.koryki.jdbc.JdbcDatabase;
import ai.koryki.jdbc.ResultProcessor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Vendor base for every Mariadb connection: pins session
 * state at construction (see docs/TEMPORAL.md, "Time zones") so that
 * zone-aware reads and {@code now()} never depend on who runs the query.
 */
public class MariadbDatabase<P extends ResultProcessor<?>> extends JdbcDatabase<P> {

    public MariadbDatabase(String name, Connection conn) {
        this(name, conn, ZoneId.of("UTC"));
    }

    public MariadbDatabase(String name, Connection conn, ZoneId modelZone) {
        super(name, conn, modelZone);
    }

    /**
     * Pin via a numeric offset, not a named zone — works without the mysql tz tables and makes
     * TIMESTAMP (instant-stored) reads reproducible. (A region model zone falls back to its name,
     * which then needs the server's tz tables.)
     */
    @Override
    protected String sessionTimeZoneStatement(ZoneId zone) {
        return "SET time_zone = '" + offsetLiteral(zone) + "'";
    }

    /**
     * MariaDB stores an INSTANT as a session-converted {@code TIMESTAMP}. Under the model-zone-pinned
     * session its wall-clock already <em>is</em> the model-zone representation. The base
     * {@code getObject(OffsetDateTime.class)} would instead stamp that naive wall-clock with the JVM
     * offset and shift the instant, so read it as a naive model-zone timestamp regardless of {@code jdbcType}.
     */
    @Override
    protected Instant readInstant(ResultSet rs, int i, int jdbcType) throws SQLException {
        return naiveInstant(rs, i);
    }
}
