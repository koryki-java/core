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
package ai.koryki.oracle;

import ai.koryki.jdbc.JdbcDatabase;
import ai.koryki.jdbc.ResultProcessor;

import java.sql.Connection;
import java.time.ZoneId;

/**
 * Vendor base for every Oracle connection: pins session
 * state at construction (see docs/TEMPORAL.md, "Time zones") so that
 * zone-aware reads and {@code now()} never depend on who runs the query.
 */
public class OracleDatabase<P extends ResultProcessor<?>> extends JdbcDatabase<P> {

    public OracleDatabase(String name, Connection conn) {
        this(name, conn, ZoneId.of("UTC"));
    }

    public OracleDatabase(String name, Connection conn, ZoneId modelZone) {
        super(name, conn, modelZone);
        setDecoder(new OracleDecoder(modelZone));   // native INTERVALYM / INTERVALDS -> Interval
    }

    /** Pin the session so TIMESTAMP WITH LOCAL TIME ZONE reads and CURRENT_TIMESTAMP are reproducible. */
    @Override
    protected String sessionTimeZoneStatement(ZoneId zone) {
        return "ALTER SESSION SET TIME_ZONE = '" + zoneLiteral(zone) + "'";
    }
}
