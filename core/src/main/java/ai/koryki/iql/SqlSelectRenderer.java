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
import ai.koryki.antlr.RangeException;
import ai.koryki.iql.logic.Normalizer;
import ai.koryki.iql.query.*;
import ai.koryki.iql.functions.FunctionRenderer;
import ai.koryki.iql.types.ExpressionTypeResolver;
import ai.koryki.catalog.schema.Relation;
import ai.koryki.catalog.schema.types.TypeDescriptor;
import org.antlr.v4.runtime.RuleContext;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneId;
import java.util.Locale;
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
    private final SqlDialect dialect;
    private final FunctionRenderer functionRenderer;
    protected IQLVisibilityContext visibilityContext;
    private final Map<Object, RuleContext> iqlToContext;
    private final ZoneId modelZone;


    public SqlSelectRenderer(Identifier identifier, Map<Object, RuleContext> iqlToContext,
                             LinkResolver resolver,
                             IQLVisibilityContext visibilityContext,
                             SqlDialect dialect,
                             ZoneId modelZone) {
        this.identifier = identifier;
        this.iqlToContext = iqlToContext;
        this.resolver = resolver;
        this.visibilityContext = visibilityContext;
        this.dialect = dialect;
        this.functionRenderer = dialect.getFunctionRenderer();
        this.modelZone = modelZone;
    }

    /** The model zone (docs/TEMPORAL.md), used by zone-aware literal reconciliation in the dialect. */
    public ZoneId getModelZone() {
        return modelZone;
    }

    /** Cached once per renderer — the dialect's registry is built on construction, not per node. */
    public FunctionRenderer getFunctionRenderer() {
        return functionRenderer;
    }

    protected String toSql(Select select, int indent) {
        StringBuilder b = new StringBuilder();

        b.append(selectClause(select, indent));
        b.append(fromClause(select, indent));
        b.append(filterClause(select, indent));

        b.append(groupbyClause(select, indent));
        b.append(havingClause(select, indent));

        String orderBy = orderbyClause(select, indent);
        b.append(orderBy);

        if (select.getLimit() > 0) {
            b.append(dialect.limitClause(select.getLimit(), !orderBy.isBlank(), indent));
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
        String sql = renderOut(out.getExpression(), out, indent);
        if (out.getHeader() != null) {
            sql = sql + " AS " + normal(out.getHeader());
        }
        return sql;
    }

    protected String renderOut(Expression expression, Out out, int indent) {
        return toSql(expression, indent);
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
            }
            l.addAll(collectGroup(j.getJoin()));
        }
        return l;
    }

    protected List<Order> collectOrder(List<Join> join) {
        List<Order> l = new ArrayList<>();
        for (Join j : join) {
            if (j.getSource() != null) {
                l.addAll(j.getSource().getOrder());
            }
            l.addAll(collectOrder(j.getJoin()));
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

        return groupbyClause(select.isRollup(), list, indent);
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

        return groupbyClause(rollup, list, indent);
    }

    private String groupbyClause(boolean rollup, List<Group> list, int indent) {

        String group = list.stream().map(o -> toSql(o, indent + 1)).collect(Collectors.joining(System.lineSeparator() + indent(indent) + ", "));

        if (group.length() > 0) {
            StringBuilder b = new StringBuilder();
            b.append(indent(indent) + GROUP_BY);
            if (rollup) {
                b.append(dialect.rollupPrefix());
            }
            b.append(System.lineSeparator());
            b.append(indent(indent + 2) + group);
            if (rollup) {
                b.append(dialect.rollupSuffix());
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
            Expression left = unaryLogicalExpression.getLeft();
            List<Expression> right = unaryLogicalExpression.getRight();
            String op = unaryLogicalExpression.getOp();
            if (left.getFunction() != null && right.isEmpty() && (op == null || op.isBlank())) {
                String predicateSql = toSqlPredicate(left, indent);
                if (predicateSql != null) {
                    return predicateSql;
                }
            }
            TypeDescriptor leftType = resolveType(left);
            return dialect.renderComparison(this, left, leftType, op, right, indent);
        }
    }

    public static boolean isSet(String op) {
        return "IN".equalsIgnoreCase(op);
    }

    public static boolean isInterval(String op) {
        return "BETWEEN".equalsIgnoreCase(op);
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
        return new SqlSelectRenderer(identifier, iqlToContext, resolver, visibilityContext.child(child), dialect, modelZone);
    }

    private String existsSubselect(Source left, Exists exists, int indent) {
        StringBuilder b = new StringBuilder();
        b.append(indent(indent + 1)).append(SELECT);
        b.append(System.lineSeparator());
        b.append(indent(indent + 2)).append("1");
        b.append(System.lineSeparator());

        b.append(fromClause(exists.getStart(), exists.getJoin(), indent));

        String w = filterClause(exists.getStart(), exists.getJoin(), null, indent);

        String j = joinCols(left, exists, indent + 1);

        b.append(indent(indent)).append(WHERE);
        b.append(System.lineSeparator());
        b.append(j);
        b.append(System.lineSeparator());
        if (!w.isEmpty()) {
            b.append(indent(indent)).append("AND");
            b.append(System.lineSeparator());
            b.append(w);
        }

        b.append(groupbyClause(false, exists.getStart(), exists.getJoin(), indent));
        b.append(havingClause(exists.getStart(), exists.getJoin(), null, indent));
        return b.toString();
    }

    private String joinCols(Source left, Exists exists, int indent) {
        String msg = left.getName() + (left.getAlias() != null ? " " + left.getAlias() : "");
        String crit = exists.getCrit();
        Source right = exists.getStart();

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

        String firstQualifier = startAlias != null ? startAlias : startName;
        String secondQualifier = endAlias != null ? endAlias : endName;

        StringBuilder b = new StringBuilder();
        b.append(indent(indent));

        Source startBlock = visibilityContext.getLeadingSource(startName);
        Source endBlock = visibilityContext.getLeadingSource(endName);

        List<String> lines = new ArrayList<>();

        Relation r = getRelation(range , startName, endName, crit, msg, right.getName());

        for (int i = 0; i < r.getStartColumns().size(); i++) {

            Expression leftExpr  = joinColumnExpression(startBlock, getRelationStartColumn(r, i), firstQualifier);
            Expression rightExpr = joinColumnExpression(endBlock,   getRelationEndColumn(r, i),   secondQualifier);
            TypeDescriptor leftType = resolveType(leftExpr);
            lines.add(dialect.renderComparison(this, leftExpr, leftType, "=", List.of(rightExpr), indent));
        }
        b.append(
                lines.stream().collect(Collectors.joining(
                        System.lineSeparator() + Identifier.indent( indent -1)  + "AND" +
                        System.lineSeparator() + Identifier.indent(indent)
                        )));
        return b.toString();
    }


    private String getRelationEndColumn(Relation r, int i) {
        // Do not translate, Relation already has target language
        String column = r.getEndColumns().get(i);

        return column;
    }

    private String getRelationStartColumn(Relation r, int i) {
        // Do not translate, Relation already has target language
        return r.getStartColumns().get(i);
    }

    private Expression joinColumnExpression(Source blockSource, String translatedJoinCol, String qualifier) {
        String fieldName;
        if (blockSource != null) {
            for (Out o : blockSource.getOut()) {
                Field outField = o.getExpression().getField();
                if (outField != null && toSql(blockSource, outField).equals(translatedJoinCol)) {
                    fieldName = o.getHeader() != null ? o.getHeader() : outField.getName();
                    Field f = new Field();
                    f.setAlias(qualifier);
                    f.setName(fieldName);
                    Expression e = new Expression();
                    e.setField(f);
                    return e;
                }
            }
            throw new KorykiaiException("missing joinColumn: " + translatedJoinCol + " " + blockSource.getAlias());
        } else {
            Field f = new Field();
            f.setAlias(qualifier);
            f.setName(translatedJoinCol);
            Expression e = new Expression();
            e.setField(f);
            return e;
        }
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

    public String toSql(List<Expression> expression, int indent) {
        return expression.stream().map(e -> toSql(e, indent)).collect(Collectors.joining(", "));
    }

    public String toSql(Expression expression, int indent) {
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
            if (expression.getNumber() instanceof BigInteger bigInteger) {
                return bigInteger.toString();
            } else if (expression.getNumber() instanceof BigDecimal bigDecimal) {
                // canonical form: drop trailing zeros (0.0 -> 0) but keep full precision
                return bigDecimal.stripTrailingZeros().toPlainString();
            } else {
                throw new KorykiaiException("unsupported number type: " + expression.getNumber().getClass());
            }
        } else if (expression.getLocalDate() != null) {
            return dateExpression(expression);
        } else if (expression.getLocalDateTime() != null) {
            return timestampExpression(expression);
        } else if (expression.getLocalTime() != null) {
            return timeExpression(expression);
        } else if (expression.getDuration() != null) {
            return dialect.durationLiteral(expression.getDuration());
        } else if (expression.getField() != null) {
            String col = toSql(expression.getField(), indent);
            return wallClockWrapped(col, expression);
        } else if (expression.isNull()) {
            return "NULL";
        } else if (expression.getLogical() != null) {
            return toSqlInline(expression.getLogical(), indent);
        } else if (expression.getIdentity() != null) {
            throw new KorykiaiException("identity is not allowed here");
        } else {
            throw new KorykiaiException("can't render empty expression");
        }
    }

    /** Inline (single-line) rendering of a boolean logical expression used as a function argument. */
    private String toSqlInline(LogicalExpression logical, int indent) {
        if (logical.isNot()) {
            return "NOT (" + toSqlInline(logical.getChildren().get(0), indent) + ")";
        }
        if (logical.isValue()) {
            return toSql((Source) null, logical.getUnaryRelationalExpression(), indent);
        }
        String op = " " + logical.getType().name() + " ";
        return "(" + logical.getChildren().stream()
                .map(c -> toSqlInline(c, indent))
                .collect(Collectors.joining(op)) + ")";
    }

    protected String timeExpression(Expression expression) {
        return dialect.timeLiteral(expression.getLocalTime());
    }

    protected String timestampExpression(Expression expression) {
        return dialect.timestampLiteral(expression.getLocalDateTime());
    }

    protected String dateExpression(Expression expression) {
        return dialect.dateLiteral(expression.getLocalDate());
    }

    protected String toSql(Field field, int indent) {
        StringBuilder b = new StringBuilder();
        if (field.getAlias() != null) {
            b.append(normal(field.getAlias())).append(".");
        }

        Source source = visibilityContext.getSource(field.getAlias());
        if (source == null) {
            throw new KorykiaiException("unknown source alias '" + field.getAlias() + "' for field " + field.getName());
        }

        b.append(normal(toSql(source, field)));
        return b.toString();
    }

    // One resolver per renderer: fixed (resolver, visibility, functions) scope, with its own
    // identity memo. Subselects get their own renderer (subSelect()), hence their own resolver.
    private ExpressionTypeResolver typeResolver;

    public TypeDescriptor resolveType(Expression expression) {
        if (typeResolver == null) {
            typeResolver = new ExpressionTypeResolver(resolver, visibilityContext, functionRenderer);
        }
        return typeResolver.resolve(expression);
    }

    /**
     * If {@code fieldExpr} is a wall-clock(zone) column, wrap its rendered SQL in the dialect's
     * declared-zone → model-zone conversion (docs/TEMPORAL.md). Applied wherever a column is rendered,
     * so a bare column, an arithmetic operand and a comparison operand all carry the model-zone value
     * (the conversion is SQL-side and precedes any arithmetic). Best-effort: an un-typable field is bare.
     */
    private String wallClockWrapped(String columnSql, Expression fieldExpr) {
        TypeDescriptor t;
        try {
            t = resolveType(fieldExpr);
        } catch (RuntimeException unresolved) {
            return columnSql;
        }
        if (t != null && t.getTypeEncoding() instanceof ai.koryki.catalog.schema.types.WallClockEncoding wc) {
            return dialect.wallClockToModelZone(columnSql, wc, modelZone);
        }
        return columnSql;
    }

    public String toSqlPredicate(Expression expression, int indent) {
        if (expression.getFunction() != null) {
            String sql = functionRenderer.predicate(this, expression.getFunction(), indent);
            if (sql != null) return sql;
        }
        return toSql(expression, indent);
    }

    protected String toSql(Function function, int indent) {
        String sql = functionRenderer.function(this, function, indent);
        return sql;
    }

    private String normal(String text) {
        return Identifier.normal(identifier, text);
    }

    private String indent(int l) {
        return Identifier.indent(l);
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
            throw new RangeException(range, msg + " " + crit + " " + right);
        }
        Relation r = o.get();
        return r;
    }

    public IQLVisibilityContext getVisibilityContext() {
        return visibilityContext;
    }

    public LinkResolver getResolver() {
        return resolver;
    }

    public Identifier getIdentifier() {
        return identifier;
    }

    public SqlDialect getDialect() {
        return dialect;
    }
}
