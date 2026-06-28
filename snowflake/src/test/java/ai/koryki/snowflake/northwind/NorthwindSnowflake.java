package ai.koryki.snowflake.northwind;

import ai.koryki.jdbc.ResultProcessor;
import ai.koryki.snowflake.SnowflakeDatabase;

import java.sql.Connection;
import java.time.ZoneId;

public class NorthwindSnowflake<C extends ResultProcessor<?>> extends SnowflakeDatabase<C> {

    public NorthwindSnowflake() throws Exception {
        super("northwind", connection());
    }

    /** Connect in the given model zone (default UTC). */
    public NorthwindSnowflake(ZoneId modelZone) throws Exception {
        super("northwind", connection(), modelZone);
    }

    public static Connection connection() throws Exception {

        return connection(
                System.getProperty("snowflake.northwind.user"),
                System.getProperty("snowflake.northwind.url") );
    }

}
