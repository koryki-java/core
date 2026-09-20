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

import ai.koryki.duckdb.DuckdbDatabase;
import ai.koryki.jdbc.ResultProcessor;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.ZoneId;

public class NorthwindDuckdb {

    public static String DUCKDB = "/ai/koryki/duckdb/databases/northwind/northwind.duckdb";

    public static <P extends ResultProcessor<?>> DuckdbDatabase<P> northwind() {
        return northwind(ZoneId.of("UTC"));
    }

    /** Build the Northwind DuckDB database in the given model zone (default UTC). */
    public static <P extends ResultProcessor<?>> DuckdbDatabase<P> northwind(ZoneId modelZone) {
        return new DuckdbDatabase<>(
                "ai/koryki/databases/northwind", fromResource(DUCKDB), modelZone);
    }

    public static Connection fromResource(String resource) {
        return DuckdbDatabase.fromResource(resource, Path.of("/tmp/korykiai.duckdb"));
    }
}
