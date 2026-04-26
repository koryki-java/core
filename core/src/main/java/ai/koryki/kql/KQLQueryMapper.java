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
package ai.koryki.kql;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.antlr.Range;
import ai.koryki.iql.Identifier;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SelectScopeCollector;
import ai.koryki.iql.query.*;
import ai.koryki.iql.query.Set;
import ai.koryki.iql.logic.Normalizer;
import org.antlr.v4.runtime.RuleContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

public class KQLQueryMapper {

    private KQLParser.QueryContext script;
    private String description;
    private final LinkResolver resolver;

    private final Map<String, Source> blockIdToSourceMap = new HashMap<>();
    private final Map<Object, RuleContext> iqlToContext = new HashMap<>();

    public KQLQueryMapper(LinkResolver resolver, KQLParser.QueryContext script, String description) {
        this.resolver = resolver;

        this.script = script;
        this.description = description;
    }

    private <O> O build(RuleContext ctx, java.util.function.Supplier<O> s) {
        O o = s.get();
        iqlToContext.put(o, ctx);
        return o;
    }

    public Query toBean() {

        Query bean = build(script, Query::new);
        bean.setDescription(description);
        if (script.block() != null) {
            bean.setBlock(toList(script.block()));
        }
        bean.setSet(toSet(script.set()));

        return bean;
    }

    private List<Block> toList(List<KQLParser.BlockContext> cte) {

        List<Block> map = new ArrayList<>();
        for (KQLParser.BlockContext b : cte) {
            Block block = build(b, Block::new);
            block.setId(b.ID().getText());
            if (b.PLACEHOLDER() != null) {
                block.setPlaceholder(b.PLACEHOLDER().getText());
            } else {
                block.setSet(toSet(b.set()));
                blockIdToSourceMap.put(block.getId(), SelectScopeCollector.getLeading(block.getSet()));
            }
            map.add(block);
        }
        return map;
    }

    private Set toSet(KQLParser.SetContext set) {
        Set bean = build(set, Set::new);

        String op = set.SET_INTERSECT() != null ? set.SET_INTERSECT().getText() :
                set.SET_MINUS() != null ? set.SET_MINUS().getText() :
                set.SET_UNION() != null ? set.SET_UNION().getText() :
                set.SET_UNIONALL() != null ? set.SET_UNIONALL().getText() : null;

        if (set.LEFT_PAREN() != null) {
            return toSet(set.set(0));
        } else if (op != null) {
            bean.setOperator(op);
            bean.setLeft(toSet(set.set(0)));
            bean.setRight(toSet(set.set(1)));
        } else if (set.select() != null) {
            bean.setSelect(toSelect(set.select()));
        } else {
            throw new KorykiaiException();
        }

        return bean;
    }

    private Select toSelect(KQLParser.SelectContext select) {

        Select bean = build(select, Select::new);

        Map<String, Source> aliasToTable = buildJoinTree(select, bean);

        if (select.filterClause() != null) {
            LogicalExpression node = toLogicalNode(aliasToTable, select.filterClause().logical_expression());
            node = Normalizer.normalize(node);
            bean.setFilter(node);
        }
        KQLParser.FetchClauseContext fetchClause = select.fetchClause();
        if (fetchClause != null) {
            bean.setDistinct(fetchClause.DISTINCT() != null);
            bean.setRollup(fetchClause.ROLLUP() != null);
            int idx = 1;
            for (KQLParser.FetchItemContext r : fetchClause.fetchItem()) {
                Out out = toOut(r, idx);
                bean.getOut().add(out);
                Order order = toOrder(r, idx);
                if (order != null) {
                    bean.getOrder().add(order);
                }
                idx++;
            }
        }

        if (select.limitClause() != null && select.limitClause().INT() != null) {
            int limit = Integer.parseInt(select.limitClause().INT().getText());
            if (limit > 0) {
                bean.setLimit(limit);
            }
        }
        return bean;
    }

    private Map<String, Source> buildJoinTree(KQLParser.SelectContext select, Select bean) {
        Map<String, Source> aliasToTable = new HashMap<>();
        Source start = toTable(select.source());

        Source bTable = blockIdToSourceMap.get(start.getName());
        aliasToTable.put(start.getAlias(), bTable != null ? bTable : start);

        bean.setStart(start);
        HashMap<String, List<Join>> joins = new HashMap<>();

        // store first link
        joins.put(start.getAlias(), bean.getJoin());

        List<KQLParser.LinkContext> links = select.link();
        if (!links.isEmpty()) {
            String from = start.getAlias();
            iterateLinks(links, from, aliasToTable, joins);
        }
        return aliasToTable;
    }

    private void iterateLinks(List<KQLParser.LinkContext> links, String from, Map<String, Source> aliasToTable, HashMap<String, List<Join>> joins) {
        for (KQLParser.LinkContext link : links) {

            String f = link.from != null ? link.from.getText() : from;

            Join join = toJoin(link, aliasToTable);
            if (join.getCrit() == null) {
                // anonyme criteria must be resolved here
                Source fromTable = blockIdToSourceMap.get(f);

                if (fromTable == null) {
                    fromTable = aliasToTable.get(f);
                }
                String t = link.source().alias.getText();

                // first test for blockId, as visibilityContext may already contain alias, but this is the table itself
                Source toTable = blockIdToSourceMap.get(link.source().name.getText());
                if (toTable == null) {
                    toTable = aliasToTable.get(t);
                }

                Optional<String> l = resolver.findLink(Range.range(iqlToContext.get(toTable)) , fromTable.getName(),
                        toTable.getName(), null);

                if (l.isEmpty()) {
                    throw new KorykiaiException(fromTable.getAlias() + " " + fromTable.getName() + " " + toTable.getName() + " " + toTable.getAlias() + " " + Range.range(iqlToContext.get(join)));
                }

                join.setCrit(l.get());
            }

            List<Join> j = findStartLink(f, joins);
            j.add(join);
            if (join.getSource() != null) {
                joins.put(join.getSource().getAlias(), join.getJoin());
                from = join.getSource().getAlias();
            } else {
                joins.put(join.getRef(), join.getJoin());
                from = join.getRef();
            }
        }
    }

    private LogicalExpression toLogicalNode(Map<String, Source> aliasToTable, KQLParser.Logical_expressionContext logicalExpression) {

        if (logicalExpression.unary_logical_expression() != null) {
            return build(logicalExpression, () -> LogicalExpression.value(toUnaryLogicalExpression(aliasToTable, logicalExpression.unary_logical_expression())));
        } else if (logicalExpression.NOT() != null) {
            LogicalExpression n = toLogicalNode(aliasToTable, logicalExpression.negate);
            return build(logicalExpression, () -> LogicalExpression.not(n));
        } else {
            LogicalExpression left = toLogicalNode(aliasToTable, logicalExpression.left);
            LogicalExpression right = toLogicalNode(aliasToTable, logicalExpression.right);
            return build(logicalExpression, () -> logicalExpression.AND() != null ? LogicalExpression.and(left, right) : LogicalExpression.or(left, right));
        }
    }

    private UnaryLogicalExpression toUnaryLogicalExpression(Map<String, Source> aliasToTable, KQLParser.Unary_logical_expressionContext unaryLogicalExpressionContext) {

        if (unaryLogicalExpressionContext.PLACEHOLDER() != null) {
            UnaryLogicalExpression bean = build(unaryLogicalExpressionContext, UnaryLogicalExpression::new);
            bean.setLeft(toExpression(unaryLogicalExpressionContext.expression().getFirst()));
            if (unaryLogicalExpressionContext.operator() != null) {
                bean.setOp(unaryLogicalExpressionContext.operator().getText());
            }
            bean.setPlaceholder(unaryLogicalExpressionContext.PLACEHOLDER().getText());
            return bean;
        } else if (unaryLogicalExpressionContext.logical_expression() != null) {
            UnaryLogicalExpression bean = build(unaryLogicalExpressionContext, UnaryLogicalExpression::new);
            bean.setNode(toLogicalNode(aliasToTable, unaryLogicalExpressionContext.logical_expression()));
            return bean;
        } else if (unaryLogicalExpressionContext.operator() != null) {
            UnaryLogicalExpression bean = build(unaryLogicalExpressionContext, UnaryLogicalExpression::new);
            bean.setOp(unaryLogicalExpressionContext.operator().getText());
            bean.setLeft(toExpression(unaryLogicalExpressionContext.expression().getFirst()));
            for (int i = 1; i < unaryLogicalExpressionContext.expression().size(); i++) {
                bean.getRight().add(toExpression(unaryLogicalExpressionContext.expression().get(i)));
            }
            return bean;
        } else if (unaryLogicalExpressionContext.exists() != null) {
            UnaryLogicalExpression bean = build(unaryLogicalExpressionContext, UnaryLogicalExpression::new);
            bean.setExists(toExists(aliasToTable, unaryLogicalExpressionContext.exists()));
            return bean;
        } else {
            throw new KorykiaiException();
        }
    }

    private Exists toExists(Map<String, Source> aliasToTable, KQLParser.ExistsContext exists) {

        Exists e = build(exists, Exists::new);

        KQLParser.LinkContext first = exists.link(0);

        String parentAlias = first.from.getText();
        e.setParent(parentAlias);

        Source fromTable = aliasToTable.get(parentAlias);

        String toTableName = first.source().name.getText();
        String toAlias = first.source().alias.getText();
        Source toTable = build(first.source(), Source::new);
        toTable.setName(toTableName);
        toTable.setAlias(toAlias);

        String crit = first.crit != null ? first.crit.getText() : null;
        e.setCrit(crit);

        if (e.getCrit() == null) {
            Optional<String> l = resolver.findLink(Range.range(iqlToContext.get(toTable)), fromTable.getName(),
                    toTable.getName());
            if (l.isEmpty()) {
                throw new KorykiaiException(fromTable.getAlias() + " " + fromTable.getName() + " " + toTable.getName() + " " + toTable.getAlias());
            }
            e.setCrit(l.get());
        }

        e.setSource(toTable);

        Map<String, Source> aToT = new HashMap<>();
        aToT.put(toTable.getAlias(), toTable);

        HashMap<String, List<Join>> joins = new HashMap<>();
        // store first link
        joins.put(e.getSource().getAlias(), e.getJoin());
        String from = e.getSource().getAlias();
        iterateLinks(
                exists.link().subList(1, exists.link().size()),
                from,
                aToT,
                joins
        );

        if (exists.filterClause() != null) {
            LogicalExpression node = toLogicalNode(aToT, exists.filterClause().logical_expression());
            e.setFilter(node);
        }
        return e;
    }

    private Out toOut(KQLParser.FetchItemContext fetch, int idx) {

        Out o = build(fetch, Out::new);
        o.setIdx(idx);
        o.setExpression(toExpression(fetch.expression()));
        if (fetch.h != null) {
            o.setHeader(fetch.h.getText());
        }

        fetch.ASC();
        fetch.DESC();
        int i = 0;
        if (fetch.idx != null) {
            i = Integer.parseInt(fetch.idx.getText());
        };
        if (fetch.label != null) {
            o.setLabel(fetch.label.getText());
        }
        return o;
    }

    private Order toOrder(KQLParser.FetchItemContext fetch, int idx) {

        if (fetch.ASC() == null && fetch.DESC() == null) {
            return null;
        }

        Order o = build(fetch, Order::new);
        o.setIdx(idx);
        if (fetch.ASC() != null) {
            o.setSort(Order.SORT.ASC);
        } else if (fetch.DESC() != null) {
            o.setSort(Order.SORT.DESC);
        }
        int i = 0;
        if (fetch.idx != null) {
            i = Integer.parseInt(fetch.idx.getText());
        } else {
            i = idx;
        }
        o.setIdx(i);
        o.setExpression(toExpression(fetch.expression()));

        return o;
    }

    private List<Expression> toExpression(List<KQLParser.ExpressionContext> expression) {

        return expression.stream().map(this::toExpression).toList();
    }


    private Expression toExpression(KQLParser.ExpressionContext expression) {

        if (expression.select() != null) {
            Expression bean = build(expression, Expression::new);
            bean.setSelect(toSelect(expression.select()));
            return bean;
        } else if (expression.LEFT_PAREN() != null) {
            return toExpression(expression.expression(0));
        } else if (expression.MULT() != null) {
            String name = ai.koryki.iql.rules.Math.multiply.name();
            return mathFunction(expression, name);
        } else if (expression.DIV() != null) {
            String name = ai.koryki.iql.rules.Math.divide.name();
            return mathFunction(expression, name);
        } else if (expression.PLUS() != null) {
            String name = ai.koryki.iql.rules.Math.add.name();
            return mathFunction(expression, name);
        } else if (expression.BAR() != null) {
            String name = ai.koryki.iql.rules.Math.minus.name();
            return mathFunction(expression, name);
        } else if (expression.date_literal() != null) {
            return toExpression(expression.date_literal());
        } else if (expression.field() != null) {
            Expression bean = build(expression, Expression::new);
            bean.setField(toField(expression.field()));
            return bean;
        } else if (expression.function() != null) {
            Expression bean = build(expression, Expression::new);
            bean.setFunction(toFunction(expression.function()));
            return bean;
        } else if (expression.INT() != null) {
            Expression bean = build(expression, Expression::new);
            bean.setNumber(Double.valueOf(expression.INT().getText()));
            return bean;
        } else if (expression.NUMBER() != null) {
            Expression bean = build(expression, Expression::new);
            bean.setNumber(Double.valueOf(expression.NUMBER().getText()));
            return bean;
        } else if (expression.SQ_STRING() != null) {
            Expression bean = build(expression, Expression::new);
            bean.setText(expression.SQ_STRING().getText());
            return bean;
        } else {
            throw new KorykiaiException();
        }
    }

    private Expression mathFunction(KQLParser.ExpressionContext expression, String name) {
        Expression bean = build(expression, Expression::new);
        Function f = build(expression, Function::new);
        f.setFunc(name);
        f.setArguments(Arrays.asList(toExpression(expression.left), toExpression(expression.right)));
        bean.setFunction(f);
        return bean;
    }

    private Expression toExpression(KQLParser.Date_literalContext date) {
        Expression bean = build(date, Expression::new);

        if (date.TIME_FORMAT() != null) {
            bean.setLocalTime(LocalTime.parse(Identifier.unquote(date.TIME_FORMAT().getText())));
        } else if (date.TIMESTAMP_FORMAT() != null) {
            bean.setLocalDateTime(LocalDateTime.parse(Identifier.unquote(date.TIMESTAMP_FORMAT().getText())));
        } else if (date.DATE_FORMAT() != null) {
            bean.setLocalDate(LocalDate.parse(Identifier.unquote(date.DATE_FORMAT().getText())));
        }
        return bean;
    }

    private Field toField(KQLParser.FieldContext column) {

        Field c = build(column, Field::new);
        if (column.alias != null) {
            c.setAlias(column.alias.getText());
        }
        c.setName(column.name.getText());

        return c;
    }

    private Function toFunction(KQLParser.FunctionContext function) {

        Function f = build(function, Function::new);
        f.setFunc(function.ID().getText());

        for (KQLParser.ArgumentContext a : function.argument()) {
            if (a.expression() != null) {
                f.getArguments().add(toExpression(a.expression()));
            } else {
                Expression e = build(a, Expression::new);
                e.setIdentity(a.identity.getText());
                f.getArguments().add(e);
            }
        }
        if (function.window() != null) {
            f.setWindow(toWindow(function.window()));
        }
        return f;
    }

    public Window toWindow(KQLParser.WindowContext window) {
        Window bean = build(window, Window::new);

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

    public Limit toLimit(KQLParser.LimitContext limit) {

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
            return build(limit, Limit::CURRENT_ROW);
        } else if (limit.UNBOUNDED() != null) {
            if (limit.FOLLOWING() != null) {
                return build(limit, Limit::UNBOUNDED_FOLLOWING);
            } else if (limit.PRECEDING() != null) {
                return build(limit, Limit::UNBOUNDED_PRECEDING);
            } else {
                throw new KorykiaiException("invalid limit");
            }
        } else {
            throw new KorykiaiException("invalid limit");
        }
    }


    private List<Join> findStartLink(String from, Map<String, List<Join>> joins) {
        //String from =  link.from.getText();
        List<Join> j = joins.get(from);
        return j;
    }

    private Join toJoin(KQLParser.LinkContext link, Map<String, Source> aliasToTable) {

        String toTableName = link.source().name.getText();
        String crit = link.crit != null ? link.crit.getText() : null;
        String toAlias = link.source().alias.getText();
        Join bean = build(link, Join::new);
        bean.setCrit(crit);


        if (link.PLUS() != null) {
            bean.setOptional(true);
        }

        Source t = aliasToTable.get(toAlias);
        if (t == null) {
            t = build(link.source(), Source::new);
            t.setName(toTableName);
            t.setAlias(toAlias);
            aliasToTable.put(toAlias, t);
            bean.setSource(t);
        } else {
            bean.setRef(toAlias);
        }

        return bean;
    }

    private Source toTable(KQLParser.SourceContext table) {
        Source bean = build(table, Source::new);

        bean.setAlias(table.alias.getText());
        bean.setName(table.name.getText());

        return bean;
    }

    public Map<String, Source> getBlockIdToSourceMap() {
        return blockIdToSourceMap;
    }

    public Map<Object, RuleContext> getIqlToContext() {
        return iqlToContext;
    }
}
