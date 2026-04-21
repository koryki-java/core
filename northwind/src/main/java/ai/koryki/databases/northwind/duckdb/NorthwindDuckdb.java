package ai.koryki.databases.northwind.duckdb;


import ai.koryki.duckdb.DuckdbDatabase;
import ai.koryki.jdbc.ResultProcessor;

public class NorthwindDuckdb {

    public static String DUCKDB = "/ai/koryki/databases/northwind/duckdb/northwind.duckdb";

    public static <P extends ResultProcessor<?>> DuckdbDatabase<P> northwind() {
        return DuckdbDatabase.fromResource(DUCKDB, "/tmp/korykiai.duckdb"                       );
    }
}
