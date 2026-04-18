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
package ai.koryki.iql;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.iql.logic.Normalizer;
import ai.koryki.iql.query.*;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IQLQueryMapper {

    private IQLParser.QueryContext script;
    private String description;
    private Map<Object, RuleContext> iqlToContext = new HashMap<>();


    public IQLQueryMapper(IQLReader reader) {
        this(reader.getQuery(), reader.getDescription());
    }

    public IQLQueryMapper(IQLParser.QueryContext script, String description) {
        this.script = script;
        this.description = description;
    }

    public Query toScript() {

        Query bean = build(script, () -> new Query());
        bean.setDescription(description);
        if (script.cte() != null) {
            bean.setBlock(toBlock(script.cte()));
        }
        bean.setSet(toSet(script.set()));
        return bean;
    }

    public List<Block> toBlock(IQLParser.CteContext cte) {

        List<Block> list = new ArrayList<>();
        if (cte != null) {
            cte.block().forEach(b -> list.add(toBlock(b)));
        }
        return list;
    }

    public Block toBlock(IQLParser.BlockContext block) {
        Block bean = build(block, () -> new Block());
        bean.setId(block.id.getText());

        if (block.PLACEHOLDER() != null) {
            bean.setPlaceholder(block.PLACEHOLDER().getText());
        } else {
            bean.setSet(toSet(block.set()));
        }
        return bean;
    }

    public Query toQuery(IQLParser.QueryContext query) {
        Query bean = build(query, () -> new Query());

        bean.getBlock().addAll(toBlock(query.cte()));
        bean.setSet(toSet(query.set()));
        return bean;
    }

    public Set toSet(IQLParser.SetContext set) {

        String op = set.INTERSECT() != null ? set.INTERSECT().getText() :
                set.MINUS() != null ? set.MINUS().getText() :
                set.UNION() != null ? set.UNION().getText() :
                set.UNIONALL() != null ? set.UNIONALL().getText() : null;

        if (set.LEFT_PAREN() != null) {
            return toSet(set.set().get(0));
        } else if (set.select() != null) {
            Set bean = build(set, () -> new Set());
            bean.setSelect(toSelect(set.select()));
            return bean;
        } else if (op != null) {
            Set bean = build(set, () -> new Set());
            bean.setOperator(op);
            bean.setLeft(toSet(set.set().get(0)));
            bean.setRight(toSet(set.set().get(1)));
            return bean;
        } else {
            throw new KorykiaiException();
        }
    }

    public Select toSelect(IQLParser.SelectContext select) {

        if (select.LEFT_PAREN() != null) {
            return toSelect(select.select());
        } else {
            Select bean = build(select, () -> new Select());
            bean.setStart(toEntity(select.join_entity()));

            if (select.DISTINCT() != null) {
                bean.setDistinct(true);
            }
            if (select.ROLLUP() != null) {
                bean.setRollup(true);
            }

            if (select.link() != null) {
                bean.setJoin(toJoin(select.link()));
            }
            if (select.filter() != null) {
                LogicalExpression n = Normalizer.normalize(toLogicalNode(select.filter()));
                bean.setFilter(n);
            }
            if (select.having() != null) {
                LogicalExpression n = Normalizer.normalize(toLogicalNode(select.having()));
                bean.setHaving(n);
            }
            if (select.limitClause() != null) {
                bean.setLimit(Integer.parseInt(select.limitClause().INT().getText()));
            }
            return bean;
        }
    }

    public List<Join> toJoin(IQLParser.LinkContext link) {

        List<Join> list = new ArrayList<>();
        for (IQLParser.JoinContext j : link.join()) {
            list.add(toJoin(j));
        }

        return list ;
    }

    public Join toJoin(IQLParser.JoinContext join) {

        Join bean = build(join, () -> new Join());
        bean.setCrit(join.crit.getText());
        if (join.ref != null) {
            bean.setRef(join.ref.getText());
        }
        bean.setOptional(join.OPTIONAL() != null);
        //bean.setInvers(join.INVERS() != null);
        if (join.join_entity() != null) {
            bean.setSource(toEntity(join.join_entity()));
        }
        if (join.link() != null) {
            bean.setJoin(toJoin(join.link()));
        }

        return bean;
    }

    public Exists toExists(IQLParser.ExistsContext exists) {

        Exists bean = build(exists, () -> new Exists());
        bean.setParent(exists.parent.getText());
        bean.setCrit(exists.crit.getText());
        //bean.setInvers(exists.INVERS() != null);
        bean.setSource(toEntity(exists.exists_entity()));

        if (exists.link() != null) {
            bean.setJoin(toJoin(exists.link()));
        }
        return bean;
    }

    public Source toEntity(IQLParser.Join_entityContext entity) {

        Source table = build(entity, () -> new Source());
        table.setName(entity.source().tab.getText());
        if (entity.source().alias != null) {
            table.setAlias(entity.source().alias.getText());
        }

        for (IQLParser.OutContext o : entity.out()) {
            table.getOut().add(toOut(o));
        }
        if (entity.filter() != null) {
            LogicalExpression n = Normalizer.normalize(toLogicalNode(entity.filter()));
            table.setFilter(n);
        }
        for (IQLParser.OrderContext o : entity.order()) {
            table.getOrder().add(toOrder(o));
        }
        for (IQLParser.GroupContext g : entity.group()) {
            table.getGroup().add(toGroup(g));
        }
        if (entity.having() != null) {
            LogicalExpression n = Normalizer.normalize(toLogicalNode(entity.having()));
            table.setHaving(n);
        }

        return table;
    }

    public Source toEntity(IQLParser.Exists_entityContext entity) {

        Source table = build(entity, () -> new Source());

        table.setName(entity.source().tab.getText());
        if (entity.source().alias != null) {
            table.setAlias(entity.source().alias.getText());
        }

        LogicalExpression f = Normalizer.normalize(toLogicalNode(entity.filter()));
        table.setFilter(f);
        /*for (IQLParser.OrderContext o : entity.order()) {
            table.getOrder().add(toOrder(o));
        }*/
        for (IQLParser.GroupContext g : entity.group()) {
            table.getGroup().add(toGroup(g));
        }
        LogicalExpression h = Normalizer.normalize(toLogicalNode(entity.having()));
        table.setHaving(h);

        return table;
    }

    public Out toOut(IQLParser.OutContext out) {

        Out bean = build(out, () -> new Out());
        if (out.h != null) {
            bean.setHeader(out.h.getText());
        }
        bean.setExpression(toExpression(out.expression()));

        if (out.label != null) {
            bean.setLabel(Identifier.unquote(out.label.getText()));
        }

        if (out.idx != null) {
            bean.setIdx(Integer.valueOf(out.idx.getText()));
        }
        return bean;
    }

    public List<Expression> toExpression(List<IQLParser.ExpressionContext> expression) {
        return expression.stream().map(e -> toExpression(e)).toList();
    }


    public Expression toExpression(IQLParser.ExpressionContext expression) {

        if (expression.LEFT_PAREN() != null) {
            if (expression.expression() != null) {
                return toExpression(expression.expression());
            } else if (expression.select() != null) {
                Expression bean = build(expression, () -> new Expression());
                bean.setSelect(toSelect(expression.select()));
                return bean;
            } else {
                throw new KorykiaiException();
            }
        } else if (expression.date_literal() != null) {
            return toExpression(expression.date_literal());
        } else if (expression.INT() != null) {
            Expression bean = build(expression, () -> new Expression());
            bean.setNumber(Double.valueOf(expression.INT().getText()));
            return bean;
        } else if (expression.NUMBER() != null) {
            Expression bean = build(expression, () -> new Expression());
            bean.setNumber(Double.valueOf(expression.NUMBER().getText()));
            return bean;
        } else if (expression.SQ_STRING() != null) {
            Expression bean = build(expression, () -> new Expression());
            bean.setText(expression.SQ_STRING().getText());
            return bean;
        } else if (expression.field() != null) {
            return toExpression(expression.field());
        } else if (expression.function() != null) {
            return toExpression(expression.function());
        } else {
            throw new KorykiaiException();
        }
    }

    public Expression toExpression(IQLParser.FieldContext column) {
        Expression bean = build(column, () -> new Expression());

        Field c = build(column, () -> new Field());
        if (column.alias != null) {
            c.setAlias(column.alias.getText());
        }
        c.setName(column.col.getText());
        bean.setField(c);
        return bean;
    }

    public Expression toExpression(IQLParser.FunctionContext function) {
        Expression bean = build(function, () -> new Expression());

        Function f = build(function, () -> new Function());
        f.setFunc(function.ID().getText());

        for (IQLParser.ArgumentContext a : function.argument()) {
            f.getArguments().add(toExpression(a));
        }

        if (function.window() != null) {
            f.setWindow(toWindow(function.window()));
        }

        bean.setFunction(f);
        return bean;
    }

    public Window toWindow(IQLParser.WindowContext window) {
        Window bean = build(window, () -> new Window());

        if (window.partitionex != null) {
            bean.setPartition(toExpression(window.partitionex));
        }
        if (window.orderex != null) {
            bean.setOrder(toExpression(window.orderex));
        }
        if (window.frame() != null) {
            bean.setLower(toLimit(window.frame().lower));
            bean.setUpper(toLimit(window.frame().upper));
        }

        return bean;
    }

    public Limit toLimit(IQLParser.LimitContext limit) {

        if (limit.INT() != null) {
            int i = Integer.parseInt(limit.INT().getText());
            if (limit.FOLLOWING() != null) {
                return build(limit, () -> Limit.FOLLOWING(i));
            } else if (limit.PRECEDING() != null) {
                return build(limit, () -> Limit.PRECEDING(i));
            } else {
                throw new KorykiaiException("invalid limit");
            }
        } else if (limit.CURRENT() != null) {
            return build(limit, () -> Limit.CURRENT_ROW());
        } else if (limit.UNBOUNDED() != null) {
            if (limit.FOLLOWING() != null) {
                return build(limit, () -> Limit.UNBOUNDED_FOLLOWING());
            } else if (limit.PRECEDING() != null) {
                return build(limit, () -> Limit.UNBOUNDED_PRECEDING());
            } else {
                throw new KorykiaiException("invalid limit");
            }
        } else {
            throw new KorykiaiException("invalid limit");
        }
    }


    public Expression toExpression(IQLParser.ArgumentContext argument) {
         if (argument.expression() != null) {
             return toExpression(argument.expression());
         } else {
             Expression e = build(argument, () -> new Expression());
             e.setIdentity(argument.identity.getText());
             return e;
         }
    }

    public Expression toExpression(IQLParser.Date_literalContext date) {
        Expression bean = build(date, () -> new Expression());

        if (date.TIME_FORMAT() != null) {
            bean.setLocalTime(LocalTime.parse(Identifier.unquote(date.TIME_FORMAT().getText())));
        } else if (date.TIMESTAMP_FORMAT() != null) {
            bean.setLocalDateTime(LocalDateTime.parse(Identifier.unquote(date.TIMESTAMP_FORMAT().getText())));
        } else if (date.DATE_FORMAT() != null) {
            bean.setLocalDate(LocalDate.parse(Identifier.unquote(date.DATE_FORMAT().getText())));
        }
        return bean;
    }

    public LogicalExpression toLogicalNode(IQLParser.FilterContext filter) {
        if (filter == null) {
            return null;
        }

        return toLogicalNode(filter.logical_expression());
    }

    public LogicalExpression toLogicalNode(IQLParser.HavingContext having) {
        if (having == null) {
            return null;
        }

        return toLogicalNode(having.logical_expression());
    }


    public LogicalExpression toLogicalNode(IQLParser.Logical_expressionContext logicalExpression) {

        if (logicalExpression.unary_logical_expression() != null) {
            return LogicalExpression.value(toUnaryLogicalExpression(logicalExpression.unary_logical_expression()));
        } else if (logicalExpression.NOT() != null) {
            LogicalExpression n = toLogicalNode(logicalExpression.negate);
            return LogicalExpression.not(n);
        } else {
            LogicalExpression left = toLogicalNode(logicalExpression.left);
            LogicalExpression right = toLogicalNode(logicalExpression.right);
            return logicalExpression.AND() != null ? LogicalExpression.and(left, right) :  LogicalExpression.or(left, right);
        }
    }

    public static boolean isAnd(ParseTree tree) {
        return tree instanceof TerminalNode && tree.getText().equalsIgnoreCase("AND");
    }

    public UnaryLogicalExpression toUnaryLogicalExpression(IQLParser.Unary_logical_expressionContext unaryLogicalExpressionContext) {

        if (unaryLogicalExpressionContext.PLACEHOLDER() != null) {
            UnaryLogicalExpression bean = build(unaryLogicalExpressionContext.logical_expression(), () -> new UnaryLogicalExpression());
            bean.setLeft(toExpression(unaryLogicalExpressionContext.expression().get(0)));
            if (unaryLogicalExpressionContext.operator() != null) {
                bean.setOp(unaryLogicalExpressionContext.operator().getText());
            }
            bean.setPlaceholder( unaryLogicalExpressionContext.PLACEHOLDER().getText());
            return bean;
        } else if (unaryLogicalExpressionContext.logical_expression() != null) {
            UnaryLogicalExpression bean = build(unaryLogicalExpressionContext.logical_expression(), () -> new UnaryLogicalExpression());
            LogicalExpression f = Normalizer.normalize(toLogicalNode(unaryLogicalExpressionContext.logical_expression()));
            bean.setNode(f);
            return bean;
        } else if (unaryLogicalExpressionContext.operator() != null) {
            UnaryLogicalExpression bean = build(unaryLogicalExpressionContext, () -> new UnaryLogicalExpression());
            //bean.setNot(unaryLogicalExpressionContext.NOT() != null);
            bean.setOp(unaryLogicalExpressionContext.operator().getText());
            bean.setLeft(toExpression(unaryLogicalExpressionContext.expression().get(0)));
            for (int i = 1; i < unaryLogicalExpressionContext.expression().size(); i++) {
                bean.getRight().add(toExpression(unaryLogicalExpressionContext.expression().get(i)));
            }
            return bean;
        } if (unaryLogicalExpressionContext.exists() != null) {
            UnaryLogicalExpression bean = build(unaryLogicalExpressionContext.exists(), () -> new UnaryLogicalExpression());
            //bean.setNot(unaryLogicalExpressionContext.NOT() != null);
            if (unaryLogicalExpressionContext.parent != null) {
                bean.setParent(unaryLogicalExpressionContext.parent.getText());
            }
            bean.setExists(toExists(unaryLogicalExpressionContext.exists()));
            return bean;
        } else {
            throw new KorykiaiException();
        }
    }

    public Order toOrder(IQLParser.OrderContext order) {
        Order bean = build(order, () -> new Order());
        if (order.expression() != null) {
            bean.setExpression(toExpression(order.expression()));
        }
        if (order.DESC() != null) {
            bean.setSort(Order.SORT.DESC);
        } else if (order.ASC() != null) {
            bean.setSort(Order.SORT.ASC);
        }
        if (order.idx != null) {
            bean.setIdx(Integer.valueOf(order.idx.getText()));
        }

        return bean;
    }

    public Group toGroup(IQLParser.GroupContext group) {
        Group bean = build(group, () -> new Group());
        bean.setExpression(toExpression(group.expression()));
        if (group.idx != null) {
            bean.setIdx(Integer.valueOf(group.idx.getText()));
        }
        return bean;
    }

    private <O> O build(RuleContext ctx, java.util.function.Supplier<O> s) {
        O o = s.get();
        iqlToContext.put(o, ctx);
        return o;
    }



    public Map<Object, RuleContext> getIqlToContext() {
        return iqlToContext;
    }
}
