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

            Script.executeScript(connection, "/ai/koryki/databases/duckdb/northwind/db/tables.sql");
            Script.executeScript(connection, "/ai/koryki/databases/duckdb/northwind/db/data_categories.sql");
            Script.executeScript(connection, "/ai/koryki/databases/duckdb/northwind/db/data_customers.sql");
            Script.executeScript(connection, "/ai/koryki/databases/duckdb/northwind/db/data_employees.sql");
            Script.executeScript(connection, "/ai/koryki/databases/duckdb/northwind/db/data_employees_territories.sql");
            Script.executeScript(connection, "/ai/koryki/databases/duckdb/northwind/db/data_order_details.sql");
            Script.executeScript(connection, "/ai/koryki/databases/duckdb/northwind/db/data_orders.sql");
            Script.executeScript(connection, "/ai/koryki/databases/duckdb/northwind/db/data_products.sql");
            Script.executeScript(connection, "/ai/koryki/databases/duckdb/northwind/db/data_region.sql");
            Script.executeScript(connection, "/ai/koryki/databases/duckdb/northwind/db/data_shippers.sql");
            Script.executeScript(connection, "/ai/koryki/databases/duckdb/northwind/db/data_suppliers.sql");
            Script.executeScript(connection, "/ai/koryki/databases/duckdb/northwind/db/data_territories.sql");
            Script.executeScript(connection, "/ai/koryki/databases/duckdb/northwind/db/data_us_states.sql");

            Script.executeScript(connection, "/ai/koryki/databases/duckdb/northwind/db/data_check_type.sql");
            Script.executeScript(connection, "/ai/koryki/databases/duckdb/northwind/db/data_check_temporal.sql");
            connection.commit();
        }
    }
}
