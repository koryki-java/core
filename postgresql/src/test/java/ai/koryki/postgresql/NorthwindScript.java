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
package ai.koryki.postgresql;

import ai.koryki.databases.northwind.Script;
import ai.koryki.postgresql.northwind.NorthwindPostgresql;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;

public class NorthwindScript {

    public static void main(String[] args) throws IOException, InterruptedException, SQLException {

        Connection connection = NorthwindPostgresql.connection();

        File drop =
                new File(
                        "postgresql/src/test/resources/ai/koryki/databases/northwind/postgresql/drop.sql");
        Script.executeScript(connection, Files.readString(drop.toPath()));

        File tables =
                new File(
                        "postgresql/src/test/resources/ai/koryki/databases/northwind/postgresql/tables.sql");
        Script.executeScript(connection, Files.readString(tables.toPath()));

        File data =
                new File(
                        "postgresql/src/test/resources/ai/koryki/databases/northwind/postgresql/data.sql");
        Script.executeScript(connection, Files.readString(data.toPath()));

        File constraints =
                new File(
                        "postgresql/src/test/resources/ai/koryki/databases/northwind/postgresql/constraints.sql");
        Script.executeScript(connection, Files.readString(constraints.toPath()));
    }
}
