package ai.koryki.databases.northwind.duckdb;


import ai.koryki.duckdb.DuckdbDatabase;
import ai.koryki.jdbc.ResultProcessor;

import java.nio.file.Path;
import java.sql.Connection;

public class NorthwindDuckdb {

    public static String DUCKDB = "/ai/koryki/databases/northwind/duckdb/northwind.duckdb";

    public static <P extends ResultProcessor<?>> DuckdbDatabase<P> northwind() {

        return new DuckdbDatabase<>( "northwind", fromResource(DUCKDB));
    }

    public static Connection fromResource(String resource) {
        return DuckdbDatabase.fromResource(resource, Path.of("/tmp/korykiai.duckdb"));
    }

}
