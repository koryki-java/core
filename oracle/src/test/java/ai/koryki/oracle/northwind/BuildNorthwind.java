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
package ai.koryki.oracle.northwind;

import ai.koryki.databases.northwind.Script;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public class BuildNorthwind {

    public static void main(String[] args) throws IOException, InterruptedException, SQLException {

        try (Connection connection = NorthwindOracle.connection()) {
            connection.setAutoCommit(false);
            Script.executeScript(connection, "/ai/koryki/databases/northwind/oracle/drop.sql");

            Script.executeScript(connection, "/ai/koryki/databases/northwind/oracle/tables.sql");

            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/oracle/data_categories.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/oracle/data_countries.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/oracle/data_customers.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/oracle/data_employees.sql");
            Script.executeScript(
                    connection,
                    "/ai/koryki/databases/northwind/oracle/data_employees_territories.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/oracle/data_order_details.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/oracle/data_orders.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/oracle/data_products.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/oracle/data_region.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/oracle/data_shippers.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/oracle/data_suppliers.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/oracle/data_territories.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/oracle/data_us_states.sql");

            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/oracle/data_check_temporal.sql");
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/oracle/data_check_type.sql");

            connection.commit();
            Script.executeScript(
                    connection, "/ai/koryki/databases/northwind/oracle/constraints.sql");
            connection.commit();
        }
    }
}
