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
package ai.koryki.sqlite.northwind;

import ai.koryki.databases.northwind.Script;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class BuildNorthwind {

    public static void main(String[] args) throws SQLException, IOException {

        try (Connection connection =
                DriverManager.getConnection("jdbc:sqlite:build/northwind.sqlite")) {

            connection.setAutoCommit(false);

            Script.executeScript(connection, "/ai/koryki/databases/northwind/sqlite/tables.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/sqlite/data_categories.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/sqlite/data_customers.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/sqlite/data_employees.sql");
            Script.executeScript(
                    connection,
                    "/ai/koryki/databases/northwind/sqlite/data_employees_territories.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/sqlite/data_order_details.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/sqlite/data_orders.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/sqlite/data_products.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/sqlite/data_region.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/sqlite/data_shippers.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/sqlite/data_suppliers.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/sqlite/data_territories.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/sqlite/data_us_states.sql");
            // self-contained (CREATE TABLE + data): the rest of this dialect's scripts are not in
            // the repo
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/sqlite/data_countries.sql");

            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/sqlite/data_check_type.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/sqlite/data_check_temporal.sql");
            connection.commit();
        }
    }
}
