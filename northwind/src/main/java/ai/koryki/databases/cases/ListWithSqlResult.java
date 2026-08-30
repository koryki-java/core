package ai.koryki.databases.cases;

import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.jdbc.ListResult;

public class ListWithSqlResult<C extends ColumnInfo> extends ListResult<C> {


    private String sql;

    public String getSql() {
        return sql;
    }

    @Override
    public void setSql(String sql) {
        this.sql = sql;
    }
}
