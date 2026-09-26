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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * One start of a program that opens the Northwind database, as a process of its own: open it, ask
 * it one question, say what came back and where the file was.
 *
 * <p>Only {@link NorthwindDuckdbTest} runs this. It has to be a process and not a thread: the lock
 * that used to kill programs is taken by the operating system between processes, and inside one JVM
 * DuckDB shares a database it already has open for a path and never notices a conflict.
 */
public final class DatabaseChild {

    private DatabaseChild() {}

    public static void main(String[] args) throws Exception {
        try (Connection connection = NorthwindDuckdb.fromResource(NorthwindDuckdb.DUCKDB);
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT count(*) FROM categories")) {
            rows.next();
            System.out.println(rows.getLong(1) + " " + NorthwindDuckdb.file());
        }
    }
}
