package ai.koryki.mariadb.northwind;

import ai.koryki.databases.Script;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public class BuildNorthwind {

    public static void main(String[] args) throws IOException, InterruptedException, SQLException {

        try (Connection connection = NorthwindMariadb.connection()) {
            connection.setAutoCommit(false);
            // Load instants in the model zone (UTC) so TIMESTAMP literals store the intended instant —
            // must match MariadbDatabase's read-side "SET time_zone = '+00:00'" (docs/TEMPORAL.md).
            try (java.sql.Statement s = connection.createStatement()) {
                s.execute("SET time_zone = '+00:00'");
            }
            Script.executeScript(connection, "/ai/koryki/mariadb/databases/northwind/drop.sql");

            Script.executeScript(connection, "/ai/koryki/mariadb/databases/northwind/tables.sql");

            Script.executeScript(connection, "/ai/koryki/mariadb/databases/northwind/data_categories.sql");
            Script.executeScript(connection, "/ai/koryki/mariadb/databases/northwind/data_customers.sql");
            Script.executeScript(connection, "/ai/koryki/mariadb/databases/northwind/data_employees.sql");
            Script.executeScript(connection, "/ai/koryki/mariadb/databases/northwind/data_employees_territories.sql");
            Script.executeScript(connection, "/ai/koryki/mariadb/databases/northwind/data_order_details.sql");
            Script.executeScript(connection, "/ai/koryki/mariadb/databases/northwind/data_orders.sql");
            Script.executeScript(connection, "/ai/koryki/mariadb/databases/northwind/data_products.sql");
            Script.executeScript(connection, "/ai/koryki/mariadb/databases/northwind/data_region.sql");
            Script.executeScript(connection, "/ai/koryki/mariadb/databases/northwind/data_shippers.sql");
            Script.executeScript(connection, "/ai/koryki/mariadb/databases/northwind/data_suppliers.sql");
            Script.executeScript(connection, "/ai/koryki/mariadb/databases/northwind/data_territories.sql");
            Script.executeScript(connection, "/ai/koryki/mariadb/databases/northwind/data_us_states.sql");

           Script.executeScript(connection, "/ai/koryki/mariadb/databases/northwind/data_check_temporal.sql");
           Script.executeScript(connection, "/ai/koryki/mariadb/databases/northwind/data_check_type.sql");

            connection.commit();
            Script.executeScript(connection, "/ai/koryki/mariadb/databases/northwind/constraints.sql");
            connection.commit();
        }
    }
}
