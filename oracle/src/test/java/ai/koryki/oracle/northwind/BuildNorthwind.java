package ai.koryki.oracle.northwind;

import ai.koryki.databases.Script;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public class BuildNorthwind {

    public static void main(String[] args) throws IOException, InterruptedException, SQLException {

        try (Connection connection = NorthwindOracle.connection()) {
            connection.setAutoCommit(false);
            Script.executeScript(connection, "/ai/koryki/databases/northwind/oracle/drop.sql");

            Script.executeScript(connection, "/ai/koryki/databases/northwind/oracle/tables.sql");

            Script.executeScript(connection, "/ai/koryki/databases/northwind/oracle/data_categories.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/oracle/data_customers.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/oracle/data_employees.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/oracle/data_employees_territories.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/oracle/data_order_details.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/oracle/data_orders.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/oracle/data_products.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/oracle/data_region.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/oracle/data_shippers.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/oracle/data_suppliers.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/oracle/data_territories.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/oracle/data_us_states.sql");
            connection.commit();
            Script.executeScript(connection, "/ai/koryki/databases/northwind/oracle/constraints.sql");
            connection.commit();
        }
    }
}
