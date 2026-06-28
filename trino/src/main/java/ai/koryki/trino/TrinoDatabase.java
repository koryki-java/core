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
package ai.koryki.trino;

import ai.koryki.jdbc.JdbcDatabase;
import ai.koryki.jdbc.ResultProcessor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Vendor base for every Trino connection: pins session
 * state at construction (see docs/TEMPORAL.md, "Time zones") so that
 * zone-aware reads and {@code now()} never depend on who runs the query.
 */
public class TrinoDatabase<P extends ResultProcessor<?>> extends JdbcDatabase<P> {

    public TrinoDatabase(String name, Connection conn) {
        this(name, conn, ZoneId.of("UTC"));
    }

    public TrinoDatabase(String name, Connection conn, ZoneId modelZone) {
        super(name, conn, modelZone);
    }

    /** SET TIME ZONE is the Trino session command (the JDBC "timezone" connection property is the alternative). */
    @Override
    protected String sessionTimeZoneStatement(ZoneId zone) {
        return "SET TIME ZONE '" + zoneLiteral(zone) + "'";
    }

    /**
     * Trino federates: an INSTANT surfaces with the Trino type its backend maps to, not a fixed one.
     * A zone-aware backend (Postgres {@code timestamptz}, Oracle/SQL Server with-time-zone) becomes
     * {@code timestamp with time zone} and reads as an {@link java.time.OffsetDateTime}; a naive
     * backend (e.g. the MariaDB connector) becomes {@code timestamp} (no zone), for which
     * {@code getObject(OffsetDateTime.class)} is unsupported. Branch on the reported type so the read
     * is correct for any backend without knowing which one is wired.
     */
    @Override
    protected Instant readInstant(ResultSet rs, int i, int jdbcType) throws SQLException {
        if (jdbcType == Types.TIMESTAMP_WITH_TIMEZONE) {
            return super.readInstant(rs, i, jdbcType);
        }
        return naiveInstant(rs, i);   // backend exposed it as a naive, model-zone timestamp
    }
}
