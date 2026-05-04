package ai.koryki.oracle.iql;

import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.query.Function;

public class FunctionRenderer implements ai.koryki.iql.FunctionRenderer {

    @Override
    public String toText(SqlSelectRenderer renderer, Function function, int indent) {
        return cast(renderer, function, indent, "VARCHAR(4000)");
    }

}
