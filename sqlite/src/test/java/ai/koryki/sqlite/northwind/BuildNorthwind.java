package ai.koryki.sqlite.northwind;


import ai.koryki.databases.Script;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class BuildNorthwind {

    public static void main(String[] args) throws SQLException, IOException {

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:build/northwind.sqlite" )) {

            connection.setAutoCommit(false);

            Script.executeScript(connection, "/ai/koryki/databases/northwind/sqlite/tables.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/sqlite/data_categories.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/sqlite/data_customers.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/sqlite/data_employees.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/sqlite/data_employees_territories.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/sqlite/data_order_details.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/sqlite/data_orders.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/sqlite/data_products.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/sqlite/data_region.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/sqlite/data_shippers.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/sqlite/data_suppliers.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/sqlite/data_territories.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/sqlite/data_us_states.sql");
            // self-contained (CREATE TABLE + data): the rest of this dialect's scripts are not in the repo
            Script.executeScript(connection, "/ai/koryki/databases/northwind/sqlite/data_countries.sql");

            Script.executeScript(connection, "/ai/koryki/databases/northwind/sqlite/data_check_type.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/sqlite/data_check_temporal.sql");
            connection.commit();
        }
    }
}
