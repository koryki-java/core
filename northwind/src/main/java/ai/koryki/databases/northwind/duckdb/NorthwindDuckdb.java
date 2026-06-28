package ai.koryki.databases.northwind.duckdb;


import ai.koryki.duckdb.DuckdbDatabase;
import ai.koryki.jdbc.ResultProcessor;

import java.nio.file.Path;
import java.sql.Connection;
import java.time.ZoneId;

public class NorthwindDuckdb {

    public static String DUCKDB = "/ai/koryki/databases/duckdb/northwind/northwind.duckdb";

    public static <P extends ResultProcessor<?>> DuckdbDatabase<P> northwind() {
        return northwind(ZoneId.of("UTC"));
    }

    /** Build the Northwind DuckDB database in the given model zone (default UTC). */
    public static <P extends ResultProcessor<?>> DuckdbDatabase<P> northwind(ZoneId modelZone) {
        return new DuckdbDatabase<>("ai/koryki/databases/northwind", fromResource(DUCKDB), modelZone);
    }

    public static Connection fromResource(String resource) {
        return DuckdbDatabase.fromResource(resource, Path.of("/tmp/korykiai.duckdb"));
    }

}
