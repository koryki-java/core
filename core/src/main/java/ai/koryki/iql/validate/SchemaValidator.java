package ai.koryki.iql.validate;

import ai.koryki.antlr.Range;
import ai.koryki.iql.*;
import ai.koryki.iql.query.*;
import org.antlr.v4.runtime.RuleContext;

import java.util.*;

public class SchemaValidator implements Collector<List<Violation>> {

    private final LinkResolver resolver;

    private final List<Violation> violations = new ArrayList<>();
    private final Map<Object, RuleContext> iqlToContext;
    private final Map<String, Select> blockIdToSelectMap = new HashMap<>();
    private final Map<String, Source> aliasToSource = new HashMap<>();
    private final Map<String, Source> blockIdToLeadingSourceMap;
    private Map<String, Source> recursiveAliasToSourceMap;

    public SchemaValidator(LinkResolver resolver, Map<String, Source> blockIdToLeadingSourceMap, Map<Object, RuleContext> iqlToContext) {
        this.resolver = resolver;
        this.blockIdToLeadingSourceMap = blockIdToLeadingSourceMap;
        this.iqlToContext = iqlToContext;
    }

    @Override
    public List<Violation> collect() {
        return violations;
    }

    @Override
    public boolean visit(Deque<Object> deque, Block block) {
        blockIdToSelectMap.put(block.getId(), SelectScopeCollector.getLeadingSelect(block.getSet()));
        recursiveAliasToSourceMap = Walker.apply(block, new AliasToSourceCollector());

        return true;
    }

    @Override
    public void leave(Block block) {
        recursiveAliasToSourceMap = null;
    }


    @Override
    public boolean visit(Deque<Object> deque, Source source) {

        aliasToSource.put(source.getAlias(), source);

        if (recursiveAliasToSourceMap != null && recursiveAliasToSourceMap.containsKey(source.getName())) {
            // recursive source in CTE
            return true;
        }

        if (!blockIdToLeadingSourceMap.containsKey(source.getName())) {
            if (resolver.getModel().getEntity(source.getName()).isEmpty()) {
                violations.add(new Violation(source, Range.range(iqlToContext.get(source)), "invalid source"));
            }
        }
        return true;
    }

    @Override
    public boolean visit(Deque<Object> deque, Field field) {

        Source selectTable = aliasToSource.get(field.getAlias());
        if (selectTable == null) {
           violations.add(new Violation(field, Range.range(iqlToContext.get(field)), "unknown alias " + field.getAlias()));
        } else {
            Select select = blockIdToSelectMap.get(selectTable.getName());
            if (select != null) {
                List<Out> outs = SqlQueryRenderer.collectOut(select);
                if (outs.stream().anyMatch(o -> match(field, o))) {
                    return true;
                } else {
                    violations.add(new Violation(field, Range.range(iqlToContext.get(field)), "unknown header " + field.getName()));
                }
            } else {
                Optional<ai.koryki.scaffold.domain.Entity> optional = resolver.getModel().getEntity(selectTable.getName());
                if (optional.isEmpty()) {
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
