/*
 * Copyright 2025-2026 Johannes Zemlin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package ai.koryki.iql.validate;

import ai.koryki.antlr.Range;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.Collector;
import ai.koryki.iql.Visitor;
import ai.koryki.iql.query.*;
import ai.koryki.iql.rules.Aggregate;
import org.antlr.v4.runtime.RuleContext;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

public class FunctionValidator implements Visitor, Collector<List<Violation>> {

    private List<Violation> violations = new ArrayList<>();
    private Map<Object, RuleContext> iqlToContext;
    private Aggregate aggregat;

    public FunctionValidator(Aggregate aggregat, Map<Object, RuleContext> iqlToContext) {
        this.aggregat = aggregat;
        this.iqlToContext = iqlToContext;
    }
    @Override
    public boolean visit(Deque<Object> deque, Select select) {

        SqlQueryRenderer.collectOut(select).stream().forEach(o -> {

            Expression e = o.getExpression();

            boolean a = isAggregatOfColumnOrIdentity(e, aggregat);
            boolean scalar = isScalarOfColumn(e, aggregat);
            if (a && scalar) {
                violations.add(new Violation(e, Range.range(iqlToContext.get(e)), "invalid aggregation"));
            }
        });
        return true;
    }

    @Override
    public boolean visit(Deque<Object> deque, UnaryLogicalExpression logicalExpression) {

            boolean aggregat = isAggregat(logicalExpression);
            boolean scalar = isScalar(logicalExpression);
            if (aggregat && scalar) {
                violations.add(new Violation(logicalExpression, Range.range(iqlToContext.get(logicalExpression)), "invalid aggregation"));
            }
            return true;
        }

    private boolean isAggregat(UnaryLogicalExpression logicalExpression) {
        if (logicalExpression.getLeft() != null) {
            boolean leftAggregat = isAggregatOfColumnOrIdentity(logicalExpression.getLeft(), aggregat);
            if (leftAggregat) {
                return true;
            }
        }

        if (logicalExpression.getExists() != null) {
            return false;
        }

        boolean rightAggregat = logicalExpression.getRight().stream().anyMatch(r -> isAggregatOfColumnOrIdentity(r, aggregat));
        if (rightAggregat) {
            return true;
        }

        if (logicalExpression.getNode() != null) {
            boolean nodeAggregat = isAggregat(logicalExpression.getNode());
            if (nodeAggregat) {
                return true;
            }
        }
        if (logicalExpression.getExists() != null) {
            boolean existsAggregat = isAggregat(logicalExpression.getExists());
            if (existsAggregat) {
                return true;
            }
        }
        return false;
    }

    private boolean isScalar(UnaryLogicalExpression logicalExpression) {
        if (logicalExpression.getLeft() != null) {
            boolean leftScalar = isScalarOfColumn(logicalExpression.getLeft(), aggregat);
            if (leftScalar) {
                return true;
            }
        }
        // TODO exists

        boolean rightScalar = logicalExpression.getRight().stream().anyMatch(r -> isScalarOfColumn(r, aggregat));
        if (rightScalar) {
            return true;
        }

        if (logicalExpression.getNode() != null) {
            boolean nodeScalar = isScalar(logicalExpression.getNode());
            if (nodeScalar) {
                return true;
            }
        }
        if (logicalExpression.getExists() != null) {
            boolean existsScalar = isScalar(logicalExpression.getExists());
            if (existsScalar) {
                return true;
            }
        }
        return false;
    }

    public boolean isAggregat(LogicalExpression logicalExpression) {

        if (logicalExpression.getUnaryRelationalExpression() != null) {
            return isAggregat(logicalExpression.getUnaryRelationalExpression());
        }

        return logicalExpression.getChildren().stream().anyMatch(l -> isAggregat(l));
    }

    public boolean isScalar(LogicalExpression logicalExpression) {

        if (logicalExpression.getUnaryRelationalExpression() != null) {
            return isScalar(logicalExpression.getUnaryRelationalExpression());
        }

        return logicalExpression.getChildren().stream().anyMatch(l -> isScalar(l));
    }

    public boolean isAggregat(Exists exists) {
        return false;
//        if (exists.getTable().getFilter() != null && isAggregat(exists.getTable().getFilter())) {
//            return true;
//        }
//        return exists.getJoin().stream().anyMatch(j -> isAggregat(j));
    }

    public boolean isAggregat(Join join) {

        if (isAggregat(join.getSource())) {
            return true;
        }
        return join.getJoin().stream().anyMatch(j -> isAggregat(j));
    }

    public boolean isAggregat(Source table) {
        if (table.getFilter() != null && isAggregat(table.getFilter())) {
            return true;
        }
        if (table.getHaving() != null && isAggregat(table.getHaving())) {
            return true;
        }

        return false;
    }

    public boolean isScalar(Exists exists) {

        if (exists.getSource().getFilter() != null && isScalar(exists.getSource().getFilter())) {
            return true;
        }
        return exists.getJoin().stream().anyMatch(j -> isScalar(j));
    }

    public boolean isScalar(Join join) {

        if (isScalar(join.getSource())) {
            return true;
        }
        return join.getJoin().stream().anyMatch(j -> isScalar(j));
    }

    public boolean isScalar(Source table) {
        if (table.getFilter() != null && isScalar(table.getFilter())) {
            return true;
        }
        if (table.getHaving() != null && isScalar(table.getHaving())) {
            return true;
        }

        return false;
    }

    public static boolean isAggregat(Function function, Aggregate aggregat) {

        return aggregat.isAggregat(function.getFunc()) != null;
    }

    public static boolean isAggregatOfColumnOrIdentity(Expression expression, Aggregate aggregat) {

        if (expression.getFunction() != null && isAggregatOfColumnOrIdentity(expression.getFunction(), aggregat)) {
            return true;
        }
        return false;
    }

    public static boolean isAggregatOfColumnOrIdentity(Function function, Aggregate aggregat) {
        if (isAggregat(function, aggregat) && hasColumnOrIdentity(function, true, aggregat)) {
            return true;
        }
        return function.getArguments().stream().anyMatch(e -> isAggregatOfColumnOrIdentity(e, aggregat));
    }

    private boolean isScalarOfColumn(Expression expression, Aggregate aggregat) {

        if (expression.getField() != null) {
            return true;
        } else if (expression.getFunction() != null) {
            return isScalarOfColumn(aggregat, expression.getFunction());
        } else {
            return false;
        }
    }

    private boolean isScalarOfColumn(Aggregate aggregat, Function function) {

        if (hasColumnOrIdentity(function, false, aggregat)) {
            return !isAggregat(function, aggregat) ;
        }

        boolean result = function.getArguments().stream()
                .filter(e -> e.getFunction() != null)
                .map(e -> e.getFunction())
                .filter(f -> !isAggregat(f, aggregat))
                .anyMatch(f -> isScalarOfColumn(aggregat, f));
        return result;
    }

    private static boolean hasColumnOrIdentity(Function function, boolean aggregat, Aggregate aggregatx) {
        if (function.getArguments().stream().anyMatch(e -> e.getField() != null || e.getIdentity() != null)) {
            return true;
        }
        boolean result = function.getArguments().stream()
                .filter(e -> e.getFunction() != null)
                .map(e -> e.getFunction())
                .filter(f -> (aggregat | !isAggregat(f, aggregatx)))
                .anyMatch(f -> hasColumnOrIdentity(f, aggregat, aggregatx));

        return result;
    }

    @Override
    public List<Violation> collect() {
        return violations;
    }
}
