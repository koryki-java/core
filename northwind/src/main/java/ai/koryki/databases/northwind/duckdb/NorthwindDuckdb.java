package ai.koryki.databases.northwind.duckdb;


import ai.koryki.duckdb.DuckdbDatabase;
import ai.koryki.jdbc.ResultProcessor;

public class NorthwindDuckdb {

    public static String DUCKDB = "/Users/johanneszemlin/IdeaProjects/koryki-java/core/northwind/src/main/resources/ai/koryki/databases/northwind/duckdb/northwind.duckdb";

    public static <P extends ResultProcessor<?>> DuckdbDatabase<P> northwind() {


        return new DuckdbDatabase<>( "",DuckdbDatabase.connection(DUCKDB));

        //return DuckdbDatabase.fromResource(DUCKDB, "/tmp/korykiai.duckdb"                       );
    }
}
