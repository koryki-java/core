package ai.koryki.databases.northwind.duckdb;


import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import ai.koryki.databases.*;

public class BuildNorthwind {

    public static void main(String[] args) throws SQLException, IOException {

        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:build/northwind.duckdb" )) {

            connection.setAutoCommit(false);

            Script.executeScript(connection, "/ai/koryki/databases/northwind/duckdb/tables.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/duckdb/data_categories.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/duckdb/data_customers.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/duckdb/data_employees.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/duckdb/data_employees_territories.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/duckdb/data_order_details.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/duckdb/data_orders.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/duckdb/data_products.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/duckdb/data_region.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/duckdb/data_shippers.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/duckdb/data_suppliers.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/duckdb/data_territories.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/duckdb/data_us_states.sql");
            connection.commit();
        }
    }
}
