package ai.koryki.databases.northwind.duckdb;


import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import ai.koryki.databases.*;

public class BuildNorthwind {

    public static void main(String[] args) throws SQLException, IOException {

        try (Connection c = DriverManager.getConnection("jdbc:duckdb:build/northwind.duckdb" )) {

            c.setAutoCommit(false);

            String tables = "/ai/koryki/databases/northwind/duckdb/tables.sql";
            String data = "/ai/koryki/databases/northwind/duckdb/data.sql";

            Script.executeScript(c, tables);
            Script.executeScript(c, data);
            c.commit();
        }
    }

}
