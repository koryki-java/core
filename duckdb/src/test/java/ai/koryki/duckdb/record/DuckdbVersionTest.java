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

package ai.koryki.duckdb.record;

import ai.koryki.databases.cases.DatabaseVersionRecorder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * The version of DuckDB this suite ran against, for the record of a release: the one in the driver.
 */
public class DuckdbVersionTest extends DatabaseVersionRecorder {

    @Override
    protected String module() {
        return "duckdb";
    }

    @Override
    protected String version() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:");
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT version()")) {
            rs.next();
            return "DuckDB " + rs.getString(1);
        }
    }
}
