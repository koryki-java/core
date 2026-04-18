package ai.koryki.iql.validate;

import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.Walker;
import ai.koryki.iql.query.Query;
import ai.koryki.iql.query.Source;
import ai.koryki.iql.rules.Aggregate;
import org.antlr.v4.runtime.RuleContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Validator {

    private LinkResolver resolver;
    private Aggregate aggregat;
    private Map<String, Source> blockIdToLeadingTableMap;
    private Map<Object, RuleContext> iqlToContext;
    private Query bean;
    private List<Violation> violations;

    public Validator(Aggregate aggregat, Query bean, LinkResolver resolver, Map<String, Source> blockIdToLeadingTableMap, Map<Object, RuleContext> iqlToContext) {
        this.aggregat = aggregat;
        this.bean = bean;
        this.resolver = resolver;
        this.blockIdToLeadingTableMap = blockIdToLeadingTableMap;
        this.iqlToContext = iqlToContext;
    }

    public List<Violation> validate() {

        if (violations == null) {
            violations = new ArrayList<>();
            violations.addAll(Walker.apply(bean, new FunctionValidator(aggregat, iqlToContext)));
            violations.addAll(Walker.apply(bean, new SchemaValidator(resolver, blockIdToLeadingTableMap, iqlToContext)));
        }
        return violations;
    }
}
