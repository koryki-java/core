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
package ai.koryki.postgresql.northwind;

import ai.koryki.databases.northwind.Script;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public class BuildNorthwind {

    public static void main(String[] args) throws IOException, InterruptedException, SQLException {

        try (Connection connection = NorthwindPostgresql.connection()) {
            connection.setAutoCommit(false);
            Script.executeScript(connection, "/ai/koryki/databases/northwind/postgresql/drop.sql");

            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/postgresql/tables.sql");

            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/postgresql/data_categories.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/postgresql/data_countries.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/postgresql/data_customers.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/postgresql/data_employees.sql");
            Script.executeScript(
                    connection,
                    "/ai/koryki/databases/northwind/postgresql/data_employees_territories.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/postgresql/data_order_details.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/postgresql/data_orders.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/postgresql/data_products.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/postgresql/data_region.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/postgresql/data_shippers.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/postgresql/data_suppliers.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/postgresql/data_territories.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/postgresql/data_us_states.sql");

            Script.executeScript(
                    connection,
                    "/ai/koryki/databases/northwind/postgresql/data_check_temporal.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/postgresql/data_check_type.sql");
            connection.commit();
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/postgresql/constraints.sql");
            connection.commit();
        }
    }
}
