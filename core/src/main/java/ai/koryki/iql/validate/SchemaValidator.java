package ai.koryki.iql.validate;

import ai.koryki.antlr.Range;
import ai.koryki.iql.*;
import ai.koryki.iql.query.*;
import org.antlr.v4.runtime.RuleContext;

import java.util.*;

public class SchemaValidator implements Collector<List<Violation>> {

    private LinkResolver resolver;

    private List<Violation> violations = new ArrayList<>();
    private Map<Object, RuleContext> iqlToContext;
    private Map<String, Select> blockIdToSelectMap = new HashMap<>();
    private Map<String, Source> aliasToTable = new HashMap<>();
    private Map<String, Source> blockIdToLeadingTableMap;
    private Map<String, Source> recursiveAliasToTableMap;

    public SchemaValidator(LinkResolver resolver, Map<String, Source> blockIdToLeadingTableMap, Map<Object, RuleContext> iqlToContext) {
        this.resolver = resolver;
        this.blockIdToLeadingTableMap = blockIdToLeadingTableMap;
        this.iqlToContext = iqlToContext;
    }

    @Override
    public List<Violation> collect() {
        return violations;
    }

    @Override
    public boolean visit(Deque<Object> deque, Block block) {
        blockIdToSelectMap.put(block.getId(), SelectScopeCollector.getLeadingSelect(block.getSet()));
        recursiveAliasToTableMap = Walker.apply(block, new AliasToSourceCollector());

        return true;
    }

    @Override
    public void leave(Block block) {
        recursiveAliasToTableMap = null;
    }


    @Override
    public boolean visit(Deque<Object> deque, Source table) {

        aliasToTable.put(table.getAlias(), table);

        if (recursiveAliasToTableMap != null && recursiveAliasToTableMap.containsKey(table.getName())) {
            // recursive table in CTE
            return true;
        }

        if (!blockIdToLeadingTableMap.containsKey(table.getName())) {
            if (!resolver.getModel().getEntity(table.getName()).isPresent()) {
                violations.add(new Violation(table, Range.range(iqlToContext.get(table)), "invalid table"));
            }
        }
        return true;
    }

    @Override
    public boolean visit(Deque<Object> deque, Field field) {

        Source selectTable = aliasToTable.get(field.getAlias());
        if (selectTable == null) {
           violations.add(new Violation(field, Range.range(iqlToContext.get(field)), "unknown alias " + field.getAlias()));
        } else {
            Select select = blockIdToSelectMap.get(selectTable.getName());
            if (select != null) {
                List<Out> outs = SqlQueryRenderer.collectOut(select);
                if (outs.stream().filter(o -> match(field, o)).findFirst().isPresent()) {
                    return true;
                } else {
                    violations.add(new Violation(field, Range.range(iqlToContext.get(field)), "unknown header " + field.getName()));
                }
            } else {
                Optional<ai.koryki.scaffold.domain.Entity> optional = resolver.getModel().getEntity(selectTable.getName());
                if (!optional.isPresent()) {
                    violations.add(new Violation(field, Range.range(iqlToContext.get(field)), "invalid table " + selectTable.getName()));
                }
            }
        }
        return true;
    }

    private static boolean match(Field column, Out o) {
        return column.getName().equals(o.getHeader()) || (o.getExpression().getField() != null && column.getName().equals(o.getExpression().getField().getName()));
    }
}
