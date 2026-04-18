package ai.koryki.databases.northwind;

import ai.koryki.iql.FunctionRenderer;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.IQLVisibilityContext;
import ai.koryki.iql.query.Order;
import org.antlr.v4.runtime.RuleContext;

import java.util.Map;

public class Select2Sql extends SqlSelectRenderer {

    public Select2Sql(Map<Object, RuleContext> iqlToContext, LinkResolver resolver, IQLVisibilityContext visibilityContext, FunctionRenderer functionRenderer) {
        super(iqlToContext, resolver, visibilityContext, functionRenderer);
    }

    protected String toSql(Order order, int indent) {
        return super.toSql(order, indent);
    }

}
