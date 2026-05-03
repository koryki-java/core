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
import ai.koryki.antlr.Range;
import ai.koryki.iql.logic.Normalizer;
import ai.koryki.iql.query.*;
import ai.koryki.iql.rules.Math;
import ai.koryki.scaffold.schema.Relation;
import org.antlr.v4.runtime.RuleContext;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public class SqlSelectRenderer {

    public static final String HAVING = "HAVING";
    public static final String WHERE = "WHERE";
    public static final String GROUP_BY = "GROUP BY";
    public static final String ORDER_BY = "ORDER BY";
    public static final String WITH = "WITH";
    public static final String ASC = "ASC";
    public static final String DESC = "DESC";
    public static final String SELECT = "SELECT";

    private final Identifier identifier;

    protected LinkResolver resolver;
    private final FunctionRenderer functionRenderer;
    protected IQLVisibilityContext visibilityContext;
    private final Map<Object, RuleContext> iqlToContext;

    public SqlSelectRenderer(Identifier identifier, Map<Object, RuleContext> iqlToContext,
                             LinkResolver resolver,
                             IQLVisibilityContext visibilityContext,
                             FunctionRenderer functionRenderer) {
        this.identifier = identifier;
        this.iqlToContext = iqlToContext;
        this.resolver = resolver;
        this.visibilityContext = visibilityContext;
        this.functionRenderer = functionRenderer;
    }

    protected String toSql(Select select, int indent) {
        StringBuilder b = new StringBuilder();

        b.append(selectClause(select, indent));
        b.append(fromClause(select, indent));
        b.append(filterClause(select, indent));

        b.append(groupbyClause(select, indent));
        b.append(havingClause(select, indent));
        b.append(orderbyClause(select, indent));

        if (select.getLimit() > 0) {
            b.append(indent(indent));
            b.append("FETCH FIRST ").append(select.getLimit()).append(" ROWS ONLY");
            b.append(System.lineSeparator());
        }
        return b.toString();
    }

    private String selectClause(Select select, int indent) {
        StringBuilder b = new StringBuilder();
        b.append(indent(indent)).append(SELECT);
        b.append(System.lineSeparator());

        b.append(indent(indent + 2));

        if (select.isDistinct()) {
            b.append(indent(indent)).append("DISTINCT ");
        }

        List<Out> out = collectOut(select);

        if (out.isEmpty()) {
            b.append("1").append(System.lineSeparator());
        } else {
            b.append(selectClause(out, indent));
        }
        return b.toString();
    }

    public static List<Out> collectOut(Select select) {
        List<Out> out = new ArrayList<>();
        out.addAll(select.getStart().getOut());
        out.addAll(collectOut(select.getJoin()));
        out.addAll(select.getOut());

        Comparator<Out> c = (o1, o2) -> {

            int p1 = o1.getIdx() == 0 ? Integer.MAX_VALUE : o1.getIdx();
            int p2 = o2.getIdx() == 0 ? Integer.MAX_VALUE : o2.getIdx();
            return p1 - p2;
        };

        out.sort(c);
        return out;
    }

    private String fromClause(Select select, int indent) {
        return fromClause(select.getStart(), select.getJoin(), indent);
    }

    private String fromClause(Source start, List<Join> join, int indent) {
        StringBuilder b = new StringBuilder();
        b.append(indent(indent)).append("FROM");
        b.append(System.lineSeparator());
        b.append(indent(indent + 1)).append(toSql(start, indent + 1));
        b.append(System.lineSeparator());
        b.append(toSql(start, join, indent + 1));
        return b.toString();
    }

    private String selectClause(List<Out> out, int indent) {
        StringBuilder b = new StringBuilder();
        String s = out.stream().map(o -> toSql(o, indent + 1)).collect(Collectors.joining(System.lineSeparator() + indent(indent) + ", "));
        b.append(s);
        b.append(System.lineSeparator());
        return b.toString();
    }

    public static List<Out> collectOut(List<Join> join) {
        List<Out> l = new ArrayList<>();
        for (Join j : join) {
            if (j.getSource() != null) {
                l.addAll(j.getSource().getOut());
            }
            l.addAll(collectOut(j.getJoin()));
        }
        return l;
    }

    protected String toSql(Source source, int indent) {
        StringBuilder b = new StringBuilder();

        b.append(normal(toSql(source)));
        if (source.getAlias() != null) {
            b.append(" ").append(normal(source.getAlias()));
        }
        return b.toString();
    }

    protected String toSql(Source source) {
        return resolver.getDialectTable(source.getName()).orElse(source.getName());
    }

    protected String toSql(Out out, int indent) {
        StringBuilder b = new StringBuilder();

        b.append(toSql(out.getExpression(), indent));
        if (out.getHeader() != null) {
            b.append(" AS ").append(normal(out.getHeader()));
        }
        return b.toString();
    }


    private String filterClause(Select select, int indent) {
        String w = filterClause(select.getStart(), select.getJoin(), select.getFilter(), indent);

        if (!w.isEmpty()) {
            String r = indent(indent) + WHERE + System.lineSeparator();
            return r + w;
        } else {
            return "";
        }
    }

    private String filterClause(Source start, List<Join> join, LogicalExpression filter, int indent) {
        StringBuilder b = new StringBuilder();

        List<LogicalExpression> filters = new ArrayList<>();
        if (start.getFilter() != null) {
            filters.add(start.getFilter());
        }
        filters.addAll(collectInnerFilter(join));
        if (filter != null) {
            filters.add(filter);
        }

        // create one unique and-expression and normalize it.
        LogicalExpression all = LogicalExpression.and(filters);
        all = Normalizer.normalize(all);
        b.append(toSql(start, all, indent, true));

        if (!b.isEmpty()) {
            b.append(System.lineSeparator());
        }

        return b.toString();
    }

    protected List<LogicalExpression> collectInnerFilter(List<Join> join) {
        List<LogicalExpression> l = new ArrayList<>();
        for (Join j : join) {
            if (!j.isOptional()) {
                if (j.getSource() != null && j.getSource().getFilter() != null) {

                    l.add(j.getSource().getFilter());
                }
                l.addAll(collectInnerFilter(j.getJoin()));
            }
        }
        return l;
    }

    protected List<LogicalExpression> collectHaving(List<Join> join) {
        List<LogicalExpression> l = new ArrayList<>();
        for (Join j : join) {
            if (j.getSource() != null && j.getSource().getHaving() != null) {

                l.add(j.getSource().getHaving());
            }
            l.addAll(collectHaving(j.getJoin()));
        }
        return l;
    }

    protected List<Group> collectGroup(List<Join> join) {
        List<Group> l = new ArrayList<>();
        for (Join j : join) {
            if (j.getSource() != null) {
                l.addAll(j.getSource().getGroup());
                l.addAll(collectGroup(j.getJoin()));
            }
        }
        return l;
    }

    protected List<Order> collectOrder(List<Join> join) {
        List<Order> l = new ArrayList<>();
        for (Join j : join) {
            if (j.getSource() != null) {
                l.addAll(j.getSource().getOrder());
                l.addAll(collectOrder(j.getJoin()));
            }
        }
        return l;
    }

    private String groupbyClause(Select select, int indent) {

        List<Group> list = new ArrayList<>();
        if (select.getGroup() != null) {
            list.addAll(select.getGroup());
        }
        list.addAll(select.getStart().getGroup());
        list.addAll(collectGroup(select.getJoin()));

        return groupbyClause(select.isRollup(), list, select.getJoin(), indent);
    }

    private String groupbyClause(boolean rollup, Source start, List<Join> join, int indent) {

        List<Group> list = new ArrayList<>();
        if (start.getGroup() != null) {
            list.addAll(start.getGroup());
        }
        list.addAll(collectGroup(join));

        Comparator<Group> c = new Comparator<Group>() {
            @Override
            public int compare(Group o1, Group o2) {

                int p1 = o1.getIdx() == 0 ? Integer.MAX_VALUE : o1.getIdx();
                int p2 = o2.getIdx() == 0 ? Integer.MAX_VALUE : o2.getIdx();
                return p1 - p2;
            }
        };
        list.sort(c);

        return groupbyClause(rollup, list, join, indent);
    }

    private String groupbyClause(boolean rollup, List<Group> list, List<Join> join, int indent) {

        String group = list.stream().map(o -> toSql(o, indent + 1)).collect(Collectors.joining(System.lineSeparator() + indent(indent) + ", "));

        if (group.length() > 0) {
            StringBuilder b = new StringBuilder();
            b.append(indent(indent) + GROUP_BY);
            if (rollup) {
                b.append(" ROLLUP (");
            }
            b.append(System.lineSeparator());
            b.append(indent(indent + 2) + group);
            if (rollup) {
                b.append(")");
            }
            b.append(System.lineSeparator());
            return b.toString();
        }
        return "";
    }

    protected String toSql(Group group, int indent) {
        StringBuilder b = new StringBuilder();

        b.append(toSql(group.getExpression(), indent));
        return b.toString();
    }

    private String havingClause(Select select, int indent) {
        return havingClause(select.getStart(), select.getJoin(), select.getHaving(), indent);
    }

    private String havingClause(Source start, List<Join> join, LogicalExpression having, int indent) {
        StringBuilder b = new StringBuilder();

        List<LogicalExpression> havings = new ArrayList<>();
        if (start.getHaving() != null) {
            havings.add(start.getHaving());
        }
        havings.addAll(collectHaving(join));
        if (having != null) {
            havings.add(having);
        }

        // create one unique and-expression and normalize it.
        LogicalExpression all = LogicalExpression.and(havings);
        all = Normalizer.normalize(all);
        b.append(toSql(start, all, indent, true));

        if (b.length() > 0) {
            b.append(System.lineSeparator());
        }

        String w = b.toString();
        if (!w.isEmpty()) {
            String r = indent(indent) + HAVING + System.lineSeparator();
            return r + w;
        } else {
            return "";
        }
    }

    private String orderbyClause(Select select, int indent) {

        List<Order> list = new ArrayList<>();
        if (select.getStart().getOrder() != null) {
            list.addAll(select.getStart().getOrder());
        }
        list.addAll(collectOrder(select.getJoin()));
        list.addAll(select.getOrder());

        Comparator<Order> c = new Comparator<Order>() {
            @Override
            public int compare(Order o1, Order o2) {

                int p1 = o1.getIdx() == 0 ? Integer.MAX_VALUE : o1.getIdx();
                int p2 = o2.getIdx() == 0 ? Integer.MAX_VALUE : o2.getIdx();
                return p1 - p2;
            }
        };
        list.sort(c);


        return orderbyClause(list, select.getJoin(), indent);
    }

    private String orderbyClause(List<Order> list, List<Join> join, int indent) {

        String order = list.stream().map(o -> toSql(o, indent + 1)).collect(Collectors.joining(System.lineSeparator() + indent(indent) + ", "));

        if (order.length() > 0) {
            StringBuilder b = new StringBuilder();
            b.append(indent(indent) + ORDER_BY);
            b.append(System.lineSeparator());
            b.append(indent(indent + 2) + order);
            b.append(System.lineSeparator());
            return b.toString();
        } else {
            return "";
        }
    }

    protected String toSql(Order order, int indent) {
        StringBuilder b = new StringBuilder();

        if (order.getExpression() != null) {
            b.append(toSql(order.getExpression(), indent));
        } else {
            b.append(order.getHeader());
        }
        if (order.getSort() != null) {
            b.append(" " + (order.getSort().equals(Order.SORT.ASC) ? ASC : DESC));
        }
        return b.toString();
    }

    protected String toSql(Source left, List<Join> join, int indent) {
        StringBuilder b = new StringBuilder();

        for (Join j : join) {
            b.append(toSql(left, j, indent + 1));
            b.append(toSql(j.getSource(), j.getJoin(), indent + 2));
        }
        return b.toString();
    }

    protected String toSql(Source left, Join join, int indent) {
        StringBuilder b = new StringBuilder();

        b.append(indent(indent));
        if (join.getSource() != null) {
            if (join.isOptional()) {
                b.append("LEFT OUTER ");
            } else {
                b.append("INNER ");
            }
            b.append("JOIN ");
            b.append(toSql(join.getSource(), indent));
            b.append(" ON");
            b.append(System.lineSeparator());

            b.append(joinColumns(left, join, indent + 1));
            b.append(System.lineSeparator());

            if (join.isOptional() && join.getSource().getFilter() != null) {
                // compare on outer join go here

                String e = toSql(join.getSource(), join.getSource().getFilter(), indent, true);

                if (!e.isEmpty()) {
                    b.append(indent(indent) + "AND" + System.lineSeparator() + e);
                    b.append(System.lineSeparator());
                }
            }
        } else {
            b.append("AND");
            b.append(System.lineSeparator());
            b.append(joinColumns(left, join, indent - 1));
            b.append(System.lineSeparator());
        }
        return b.toString();
    }

    private String toSql(Source parent, LogicalExpression expression, int indent, boolean leading) {

        String prefix = "";
        if (leading) {
            prefix = indent(indent + 1);
        }

        if (expression.isNot()) {
            return prefix + "NOT (" + toSql(parent, expression.getChildren().get(0), indent + 1, false) + ")";
        } else if (expression.isValue()) {
            return prefix + toSql(parent, expression.getUnaryRelationalExpression(), indent + 1);
        } else {
            StringBuilder b = new StringBuilder();

            String delim = System.lineSeparator() + indent(indent + 1) + expression.getType().name() + System.lineSeparator();
            b.append(expression.getChildren().stream().map(e -> toSql(parent, e, indent + 1, true)).collect(Collectors.joining(delim)));
            return b.toString();
        }
    }

    private String toSql(Source parent, UnaryLogicalExpression unaryLogicalExpression, int indent) {

        if (unaryLogicalExpression.getExists() != null) {
            return System.lineSeparator() + toSql(parent, unaryLogicalExpression.getExists(), indent);
        } else if (unaryLogicalExpression.getNode() != null) {
            return "(" + System.lineSeparator() + toSql(parent, unaryLogicalExpression.getNode(), indent, false) + System.lineSeparator() + indent(indent) + ")";
        } else {
            StringBuilder b = new StringBuilder();

            b.append(toSql(unaryLogicalExpression.getLeft(), indent));
            b.append(" " + toOp(unaryLogicalExpression.getOp()));

            if (isInterval(unaryLogicalExpression.getOp())) {
                b.append(" " + toInterval(unaryLogicalExpression.getRight().get(0), unaryLogicalExpression.getRight().get(1), indent));
            } else if (isSet(unaryLogicalExpression.getOp())) {
                b.append(" (" + unaryLogicalExpression.getRight().stream().map(e -> toSql(e, indent)).collect(Collectors.joining(", ")) + ")");
            } else {
                if (!unaryLogicalExpression.getRight().isEmpty()) {
                    b.append(" " + unaryLogicalExpression.getRight().stream().map(e -> toSql(e, indent)).collect(Collectors.joining(" ")));
                }
            }
            return b.toString();
        }
    }

    public static boolean isSet(String op) {
        return "IN".equalsIgnoreCase(op);
    }

    public static boolean isInterval(String op) {
        return "BETWEEN".equalsIgnoreCase(op);
    }

    protected String toInterval(Expression left, Expression right, int indent) {
        return toSql(left, indent) + " AND " + toSql(right, indent);
    }

    protected String toOp(String op) {
        if ("ISNULL".equalsIgnoreCase(op)) {
            return "IS NULL";
        }
        return op;
    }

    private String toSql(Source left, Exists exists, int indent) {

        StringBuilder b = new StringBuilder();

        b.append(indent(indent) + "EXISTS (");
        b.append(System.lineSeparator());

        SqlSelectRenderer s2s = subSelect(iqlToContext, exists);
        b.append(s2s.existsSubselect(left, exists, indent));

        b.append(indent(indent) + ")");

        return b.toString();
    }

    protected SqlSelectRenderer subSelect(Map<Object, RuleContext> iqlToContext, Object child) {


        SqlSelectRenderer s2s = new SqlSelectRenderer(identifier, iqlToContext, resolver, visibilityContext.child(child), functionRenderer);
        return s2s;
    }

    private String existsSubselect(Source left, Exists exists, int indent) {
        StringBuilder b = new StringBuilder();
        b.append(indent(indent + 1)).append(SELECT);
        b.append(System.lineSeparator());
        b.append(indent(indent + 2)).append("1");
        b.append(System.lineSeparator());

        b.append(fromClause(exists.getSource(), exists.getJoin(), indent));

        String w = filterClause(exists.getSource(), exists.getJoin(), null, indent);

        String j = joinCols(left, exists, indent + 1);

        if (!w.isEmpty()) {
            b.append(indent(indent)).append(WHERE);
            b.append(System.lineSeparator());
            b.append(j);
            b.append(System.lineSeparator());
            b.append(indent(indent)).append("AND");
            b.append(System.lineSeparator());
            b.append(w);
        } else {
            b.append(indent(indent)).append(WHERE);
            b.append(System.lineSeparator());
            b.append(j);
            b.append(System.lineSeparator());
        }

        b.append(groupbyClause(false, exists.getSource(), exists.getJoin(), indent));
        b.append(havingClause(exists.getSource(), exists.getJoin(), null, indent));
        return b.toString();
    }

    private String joinCols(Source left, Exists exists, int indent) {
        String msg = left.getName() + (left.getAlias() != null ? " " + left.getAlias() : "");
        String crit = exists.getCrit();
        Source right = exists.getSource();

        boolean invers = resolver.isInverse(exists.getCrit());

        Source first = invers ? right : left;
        Source second = invers ? left : right;
        return joinColumns(indent, first, second, crit, msg, right);
    }

    private String joinColumns(Source left, Join join, int indent) {

        String msg = left.getName() + (left.getAlias() != null ? " " + left.getAlias() : "");
        String crit = join.getCrit();
        String leftName = left.getName();
        String leftAlias = left.getAlias();

        Source rightSource = null;
        if (join.getRef() != null) {
            rightSource = visibilityContext.getSource(join.getRef());
        } else {
            rightSource = join.getSource();
        }

        String rightName = rightSource.getName();
        String rightAlias = join.getRef() != null ? join.getRef() : join.getSource().getAlias();

        boolean inverse = resolver.isInverse(join.getCrit());

        String startName = inverse ? rightName : leftName;
        String startAlias = inverse ? rightAlias : leftAlias;
        String endName = inverse ? leftName : rightName;
        String endAlias = inverse ? leftAlias : rightAlias;

        return joinColumns(Range.range(iqlToContext.get(join)), indent, startName, startAlias, endName, endAlias, crit, msg, rightSource);
    }

    private String joinColumns(int indent, Source start, Source end, String crit, String msg, Source right) {
        return joinColumns(Range.range(iqlToContext.get(start)), indent, start.getName(), start.getAlias(), end.getName(), end.getAlias(), crit, msg, right);
    }

    private String joinColumns(Range range, int indent, String startName, String startAlias, String endName, String endAlias, String crit, String msg, Source right) {

        String firstQualifier = startAlias != null ? strip(startAlias) : strip(startName);
        String secondQualifier = endAlias != null ? strip(endAlias) : strip(endName);

        StringBuilder b = new StringBuilder();
        b.append(indent(indent));

        Source startBlock = visibilityContext.getLeadingSource(startName);
        Source endBlock = visibilityContext.getLeadingSource(endName);

        List<String> lines = new ArrayList<>();

        Relation r = getRelation(range , startName, endName, crit, msg, right.getName());

        for (int i = 0; i < r.getStartColumns().size(); i++) {

            String startCol = joinColumn(startBlock, getRelationStartColumn(r, i));
            String endCol = joinColumn(endBlock, getRelationEndColumn(r, i));

            lines.add(
                    (firstQualifier != null ? firstQualifier + "." : "") +
                            startCol + " = " +
                            (secondQualifier != null ? secondQualifier + "." : "")
                            + endCol);
        }
        b.append(
                lines.stream().collect(Collectors.joining(System.lineSeparator() + Identifier.indent(indent))));
        return b.toString();
    }


    private String getRelationEndColumn(Relation r, int i) {
        // Do not translate, Relation already has target language
        return r.getEndColumns().get(i);
    }

    private String getRelationStartColumn(Relation r, int i) {
        // Do not translate, Relation already has target language
        return r.getStartColumns().get(i);
    }

    private String joinColumn(Source blockSource, String translatedJoinCol) {
        if (blockSource == null) {
            return translatedJoinCol;
        }
        for (Out o : blockSource.getOut()) {
            Field field = o.getExpression().getField();
            if (field != null && toSql(blockSource, field).equals(translatedJoinCol)) {
                return o.getHeader() != null ? o.getHeader() : toSql(blockSource, field);
            }
        }
        throw new KorykiaiException("missing joinColumn: " + translatedJoinCol + " " + blockSource.getAlias());
    }

    private String toSql(Source source, Field field) {

        Source b = visibilityContext.getLeadingSource(source.getName());

        String sourcename = b != null ? b.getName() : source.getName();
        String f = toSql(sourcename, field);
        if (f == null) {
            throw new KorykiaiException("unknow field " + source.getAlias() + " " + source.getName() + "." + field.getName());
        }
        return f;
    }

    private String toSql(String sourcename, Field field) {
        return resolver.getDialectColumn(sourcename, field.getName()).orElse(field.getName());
    }

    protected String toSql(List<Expression> expression, int indent) {
        return expression.stream().map(e -> toSql(e, 0)).collect(Collectors.joining(", "));
    }

    protected String toSql(Expression expression, int indent) {
        if (expression.getSelect() != null) {

            StringBuilder b = new StringBuilder();
            b.append("(" + System.lineSeparator());

            SqlSelectRenderer s2s = subSelect(iqlToContext, expression.getSelect());

            b.append(s2s.toSql(expression.getSelect(), indent + 2));
            b.append(indent(indent + 1) + ")");

            return b.toString();
        } else if (expression.getFunction() != null) {
            return toSql(expression.getFunction(), indent);
        } else if (expression.getText() != null) {
            String text = expression.getText();
            text = text.replace("\\'", "''");
            return text;
        } else if (expression.getNumber() != null) {
            DecimalFormat df = new DecimalFormat("#.######");  // Up to 2 decimals
            return df.format(expression.getNumber());
        } else if (expression.getLocalDate() != null) {
            return dateExpression(expression);
        } else if (expression.getLocalDateTime() != null) {
            return timestampExpression(expression);
        } else if (expression.getLocalTime() != null) {
            return timeExpression(expression);
        } else if (expression.getField() != null) {
            return toSql(expression.getField(), indent);
        } else if (expression.isNull()) {
            return "NULL";
        } else if (expression.getIdentity() != null) {
            throw new KorykiaiException("identity is not allowed here");
        } else {
            throw new KorykiaiException();
        }
    }

    protected String timeExpression(Expression expression) {
        return "TIME '" + expression.getLocalTime() + "'";
    }

    protected String timestampExpression(Expression expression) {
        return "TIMESTAMP '" + expression.getLocalDateTime() + "'";
    }

    protected String dateExpression(Expression expression) {
        return "DATE '" + expression.getLocalDate() + "'";
    }

    protected String toSql(Field field, int indent) {
        StringBuilder b = new StringBuilder();
        if (field.getAlias() != null) {
            b.append(normal(field.getAlias())).append(".");
        }

        Source source = visibilityContext.getSource(field.getAlias());
        if (source == null) {
            throw new RuntimeException(field.getAlias());
        }

        b.append(normal(toSql(source, field)));
        return b.toString();
    }

    protected String toSql(Function function, int indent) {
        StringBuilder b = new StringBuilder();

        String operator = Math.operator(function.getFunc());
        if (operator != null) {
            b.append(function.getArguments().stream().map(
                    a -> toSql(a, indent)).collect(Collectors.joining(" " + operator + " ")));
        } else {
            b.append(functionRenderer.function(this, function, indent));
        }
        b.append(toSql(function.getWindow(), indent));

        return b.toString();
    }

    protected String toSql(Window window, int indent) {
        if (window == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();

        b.append(" OVER (");

        if (!window.getPartition().isEmpty()) {
            b.append("PARTITION BY " + toSql(window.getPartition(), indent));
        }

        if (!window.getOrder().isEmpty()) {
            b.append(" ORDER BY ");
            b.append(toSql(window.getOrder(), indent));
        }

        if (window.getLower() != null) {
            b.append(" ROWS BETWEEN ");
            b.append(toSql(window.getLower()));
            b.append(" AND ");
            b.append(toSql(window.getUpper()));
        }

        b.append(")");
        return b.toString();
    }

    protected String toSql(Limit limit) {
        return (limit.getNum() > 0 ? limit.getNum() + " " : "") + limit.getName();
    }

    private String normal(String text) {
        return Identifier.normal(identifier, text);
    }

    private String normal(int l, String text) {
        return indent(l) + normal(text);
    }

    private String indent(int l) {
        return Identifier.indent(l);
    }

    public static String strip(String text) {

        if (text == null) {
            return null;
        }

        String n = text.toLowerCase();

        if (n.startsWith("\"")) {
            n = n.substring(1);
        }
        if (n.endsWith("\"")) {
            n = n.substring(0, n.length() - 1);
        }
        return n;
    }


    protected String getSource(String source) {
        if (resolver.isEntity(source)) {
            return source;
        }

        Source b = visibilityContext.getLeadingSource(source);
        if (b != null) {
            return b.getName();
        }
        throw new KorykiaiException("can't find source: " + source);
    }


    protected Relation getRelation(Range range, String startName, String endName, String crit, String msg, String right) {
        String startSource = getSource(startName);
        String endSource = getSource(endName);


        Optional<Relation> o = resolver.findRelation(range, Identifier.normal(Identifier.lowercase, startSource), Identifier.normal(Identifier.lowercase, endSource), crit);

        if (o.isEmpty()) {
            throw new KorykiaiException(msg + " " + crit + " " + right);
        }
        Relation r = o.get();
        return r;
    }

    protected FunctionRenderer getFunctionTranslator() {
        return functionRenderer;
    }

    public Identifier getIdentifier() {
        return identifier;
    }
}
