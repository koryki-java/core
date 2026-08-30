package ai.koryki.snowflake.iql;

public class SqlQueryRenderer extends ai.koryki.iql.SqlQueryRenderer {

    public SqlQueryRenderer(java.time.ZoneId modelZone) {
        super(SnowflakeDialect.INSTANCE, modelZone);
    }
}
