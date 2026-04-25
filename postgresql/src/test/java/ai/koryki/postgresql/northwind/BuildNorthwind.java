package ai.koryki.postgresql.northwind;

import ai.koryki.databases.Script;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public class BuildNorthwind {

    public static void main(String[] args) throws IOException, InterruptedException, SQLException {

        try (Connection connection = NorthwindPostgresql.connection()) {
            connection.setAutoCommit(false);
            Script.executeScript(connection, "/ai/koryki/databases/northwind/postgresql/drop.sql");

            Script.executeScript(connection, "/ai/koryki/databases/northwind/postgresql/tables.sql");

            Script.executeScript(connection, "/ai/koryki/databases/northwind/postgresql/data_categories.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/postgresql/data_customers.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/postgresql/data_employees.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/postgresql/data_employees_territories.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/postgresql/data_order_details.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/postgresql/data_orders.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/postgresql/data_products.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/postgresql/data_region.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/postgresql/data_shippers.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/postgresql/data_suppliers.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/postgresql/data_territories.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/postgresql/data_us_states.sql");
            connection.commit();
            Script.executeScript(connection, "/ai/koryki/databases/northwind/postgresql/constraints.sql");
            connection.commit();
        }
    }
}
