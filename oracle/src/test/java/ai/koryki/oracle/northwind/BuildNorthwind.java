package ai.koryki.oracle.northwind;

import ai.koryki.databases.Script;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public class BuildNorthwind {

    public static void main(String[] args) throws IOException, InterruptedException, SQLException {

        try (Connection connection = NorthwindOracle.connection()) {
            connection.setAutoCommit(false);
            Script.executeScript(connection, "/ai/koryki/oracle/databases/northwind/drop.sql");

            Script.executeScript(connection, "/ai/koryki/oracle/databases/northwind/tables.sql");

            Script.executeScript(connection, "/ai/koryki/oracle/databases/northwind/data_categories.sql");
            Script.executeScript(connection, "/ai/koryki/oracle/databases/northwind/data_customers.sql");
            Script.executeScript(connection, "/ai/koryki/oracle/databases/northwind/data_employees.sql");
            Script.executeScript(connection, "/ai/koryki/oracle/databases/northwind/data_employees_territories.sql");
            Script.executeScript(connection, "/ai/koryki/oracle/databases/northwind/data_order_details.sql");
            Script.executeScript(connection, "/ai/koryki/oracle/databases/northwind/data_orders.sql");
            Script.executeScript(connection, "/ai/koryki/oracle/databases/northwind/data_products.sql");
            Script.executeScript(connection, "/ai/koryki/oracle/databases/northwind/data_region.sql");
            Script.executeScript(connection, "/ai/koryki/oracle/databases/northwind/data_shippers.sql");
            Script.executeScript(connection, "/ai/koryki/oracle/databases/northwind/data_suppliers.sql");
            Script.executeScript(connection, "/ai/koryki/oracle/databases/northwind/data_territories.sql");
            Script.executeScript(connection, "/ai/koryki/oracle/databases/northwind/data_us_states.sql");

           Script.executeScript(connection, "/ai/koryki/oracle/databases/northwind/data_check_temporal.sql");
           Script.executeScript(connection, "/ai/koryki/oracle/databases/northwind/data_check_type.sql");

            connection.commit();
            Script.executeScript(connection, "/ai/koryki/oracle/databases/northwind/constraints.sql");
            connection.commit();
        }
    }
}
