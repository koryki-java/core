package ai.koryki.mssql.northwind;

import ai.koryki.databases.Script;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public class BuildNorthwind {

    public static void main(String[] args) throws IOException, InterruptedException, SQLException {

        try (Connection connection = NorthwindMssql.connection()) {
            // Text columns carry COLLATE Latin1_General_100_BIN2_UTF8 in tables.sql: UTF-8
            // storage with binary comparison/order, matching DuckDB byte semantics and the
            // case/accent-sensitive goldens (same reasoning as MariaDB's mandatory utf8mb4_bin).
            // Column-level, so the build works regardless of the server/database default —
            // a Latin1 default would store é as one byte and break octet_length/md5.
            connection.setAutoCommit(false);
            Script.executeScript(connection, "/ai/koryki/databases/northwind/mssql/drop.sql");

            Script.executeScript(connection, "/ai/koryki/databases/northwind/mssql/tables.sql");

            Script.executeScript(connection, "/ai/koryki/databases/northwind/mssql/data_categories.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/mssql/data_customers.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/mssql/data_employees.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/mssql/data_employees_territories.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/mssql/data_order_details.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/mssql/data_orders.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/mssql/data_products.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/mssql/data_region.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/mssql/data_shippers.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/mssql/data_suppliers.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/mssql/data_territories.sql");
            Script.executeScript(connection, "/ai/koryki/databases/northwind/mssql/data_us_states.sql");
            // self-contained (CREATE TABLE + data)
            Script.executeScript(connection, "/ai/koryki/databases/northwind/mssql/data_countries.sql");

           Script.executeScript(connection, "/ai/koryki/databases/northwind/mssql/data_check_temporal.sql");
           Script.executeScript(connection, "/ai/koryki/databases/northwind/mssql/data_check_type.sql");

            connection.commit();
            Script.executeScript(connection, "/ai/koryki/databases/northwind/mssql/constraints.sql");
            connection.commit();
        }
    }
}
