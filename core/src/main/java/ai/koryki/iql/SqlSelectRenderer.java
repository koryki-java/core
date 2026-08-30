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
import ai.koryki.catalog.types.WallClockEncoding;
import ai.koryki.iql.logic.NodeType;
import ai.koryki.iql.logic.Normalizer;
import ai.koryki.iql.query.*;
import ai.koryki.iql.functions.FunctionRenderer;
import ai.koryki.iql.typing.ExpressionTypeResolver;
import ai.koryki.catalog.schema.Relation;
import ai.koryki.catalog.types.TypeDescriptor;
import org.antlr.v4.runtime.RuleContext;

import java.time.ZoneId;
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
        b.append(SqlRenderer.NL);

        b.append(indent(indent + 2));

        if (select.isDistinct()) {
            b.append(indent(indent)).append("DISTINCT ");
        }

        List<Out> out = SqlQueryRenderer.collectOut(select);

        if (out.isEmpty()) {
            b.append("1").append(SqlRenderer.NL);
        } else {
            b.append(selectClause(out, indent));
        }
        return b.toString();
    }

    private String fromClause(Select select, int indent) {
        return fromClause(select.getStart(), select.getJoin(), indent);
    }

    private String fromClause(Source start, List<Join> join, int indent) {
        StringBuilder b = new StringBuilder();
        b.append(indent(indent)).append("FROM");
        b.append(SqlRenderer.NL);
        b.append(indent(indent + 1)).append(toSql(start, indent + 1));
        b.append(SqlRenderer.NL);
        b.append(toSql(start, join, indent + 1));
        return b.toString();
    }

    private String selectClause(List<Out> out, int indent) {
        StringBuilder b = new StringBuilder();
        String s = out.stream().map(o -> toSql(o, indent + 1)).collect(Collectors.joining(SqlRenderer.NL + indent(indent) + ", "));
        b.append(s);
        b.append(SqlRenderer.NL);
        return b.toString();
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
            String r = indent(indent) + WHERE + SqlRenderer.NL;
            return r + w;
        } else {
            return "";
        }
    }

    private String filterClause(Source start, List<Join> join, LogicalExpression filter, int indent) {
        StringBuilder b = new StringBuilder();

        b.append(toSql(start, combinedFilter(start, join, filter), indent, true));

        if (!b.isEmpty()) {
            b.append(SqlRenderer.NL);
        }

        return b.toString();
    }

    private LogicalExpression combinedFilter(Source start, List<Join> join, LogicalExpression filter) {
        List<LogicalExpression> filters = new ArrayList<>();
        if (start.getFilter() != null) {
            filters.add(start.getFilter());
        }
        filters.addAll(collectInnerFilter(join));
        if (filter != null) {
            filters.add(filter);
        }

        // create one unique and-expression and normalize it.
        return Normalizer.normalize(LogicalExpression.and(filters));
    }

    protected List<LogicalExpression> collectInnerFilter(List<Join> join) {
        // inner joins only: an optional join's filter renders in its LEFT JOIN ... ON, not the WHERE
        return collectClause(join, Source::getFilter, true);
    }

    protected List<LogicalExpression> collectHaving(List<Join> join) {
        // HAVING is collected from every join, optional ones included
        return collectClause(join, Source::getHaving, false);
    }

    /**
     * Walks the join tree collecting one clause slot (filter or having) from each source.
     * The slot is addressed by accessor so filter- and having-collection share this walk;
     * {@code innerOnly} skips optional joins and their subtree (the filter semantics).
     */
    private static List<LogicalExpression> collectClause(List<Join> join,
                                                         java.util.function.Function<Source, LogicalExpression> slot,
                                                         boolean innerOnly) {
        List<LogicalExpression> l = new ArrayList<>();
        for (Join j : join) {
            if (innerOnly && j.isOptional()) {
                continue;
            }
            Source s = j.getSource();
            if (s != null) {
                LogicalExpression e = slot.apply(s);
                if (e != null) {
                    l.add(e);
                }
            }
            l.addAll(collectClause(j.getJoin(), slot, innerOnly));
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

    /** @see SqlQueryRenderer#sortByFetchPosition — one method for all four collection paths. */
    private static <T> void sortByFetchPosition(List<T> list, java.util.function.ToIntFunction<T> idx) {
        SqlQueryRenderer.sortByFetchPosition(list, idx);
    }

    private String groupbyClause(Select select, int indent) {

        List<Group> list = new ArrayList<>();
        if (select.getGroup() != null) {
            list.addAll(select.getGroup());
        }
        list.addAll(select.getStart().getGroup());
        list.addAll(collectGroup(select.getJoin()));
        sortByFetchPosition(list, Group::getIdx);

        return groupbyClause(select.isRollup(), list, indent);
    }

    private String groupbyClause(boolean rollup, Source start, List<Join> join, int indent) {

        List<Group> list = new ArrayList<>();
        if (start.getGroup() != null) {
            list.addAll(start.getGroup());
        }
        list.addAll(collectGroup(join));
        sortByFetchPosition(list, Group::getIdx);

        return groupbyClause(rollup, list, indent);
    }

    private String groupbyClause(boolean rollup, List<Group> list, int indent) {

        String group = list.stream().map(o -> toSql(o, indent + 1)).collect(Collectors.joining(SqlRenderer.NL + indent(indent) + ", "));

        if (group.length() > 0) {
            StringBuilder b = new StringBuilder();
            b.append(indent(indent) + GROUP_BY);
            if (rollup) {
                b.append(dialect.rollupPrefix());
            }
            b.append(SqlRenderer.NL);
            b.append(indent(indent + 2) + group);
            if (rollup) {
                b.append(dialect.rollupSuffix());
            }
            b.append(SqlRenderer.NL);
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
            b.append(SqlRenderer.NL);
        }

        String w = b.toString();
        if (!w.isEmpty()) {
            String r = indent(indent) + HAVING + SqlRenderer.NL;
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
        sortByFetchPosition(list, Order::getIdx);

        return orderbyClause(list, select.getJoin(), indent);
    }

    private String orderbyClause(List<Order> list, List<Join> join, int indent) {

        String order = list.stream().map(o -> toSql(o, indent + 1)).collect(Collectors.joining(SqlRenderer.NL + indent(indent) + ", "));

        if (order.length() > 0) {
            StringBuilder b = new StringBuilder();
            b.append(indent(indent) + ORDER_BY);
            b.append(SqlRenderer.NL);
            b.append(indent(indent + 2) + order);
            b.append(SqlRenderer.NL);
            return b.toString();
        } else {
            return "";
        }
    }

    protected String toSql(Order order, int indent) {
        StringBuilder b = new StringBuilder();

        b.append(toSql(order.getExpression(), indent));
        if (order.getSort() != null) {
            b.append(" " + (order.getSort().equals(Order.SORT.ASC) ? ASC : DESC));
        }
        return b.toString();
    }

    protected String toSql(Source left, List<Join> join, int indent) {
        return toSql(left, join, indent, false);
    }

    private String toSql(Source left, List<Join> join, int indent, boolean underOptional) {
        StringBuilder b = new StringBuilder();

        for (Join j : join) {
            b.append(toSql(left, j, indent + 1, underOptional));
            b.append(toSql(j.getSource(), j.getJoin(), indent + 2, underOptional || j.isOptional()));
        }
        return b.toString();
    }

    protected String toSql(Source left, Join join, int indent) {
        return toSql(left, join, indent, false);
    }

    private String toSql(Source left, Join join, int indent, boolean underOptional) {
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
            b.append(SqlRenderer.NL);

            b.append(joinColumns(left, join, indent + 1));
            b.append(SqlRenderer.NL);

            // an optional join's filter belongs in its ON clause (WHERE would defeat LEFT
            // semantics); the same holds for every join nested below an optional one, whose
            // subtree collectInnerFilter deliberately keeps out of the WHERE clause
            if ((join.isOptional() || underOptional) && join.getSource().getFilter() != null) {

                String e = toSql(join.getSource(), join.getSource().getFilter(), indent, true);

                if (!e.isEmpty()) {
                    b.append(indent(indent) + "AND" + SqlRenderer.NL + e);
                    b.append(SqlRenderer.NL);
                }
            }
        } else {
            b.append("AND");
            b.append(SqlRenderer.NL);
            b.append(joinColumns(left, join, indent - 1));
            b.append(SqlRenderer.NL);
        }
        return b.toString();
    }

    private String toSql(Source parent, LogicalExpression expression, int indent, boolean leading) {

        String prefix = "";
        if (leading) {
            prefix = indent(indent + 1);
        }

        if (expression.isNot()) {
            String folded = foldedNegation(parent, expression.getChildren().get(0), indent);
            if (folded != null) {
                return prefix + folded;
            }
            return prefix + "NOT (" + toSql(parent, expression.getChildren().get(0), indent + 1, false) + ")";
        } else if (expression.isValue()) {
            return prefix + toSql(parent, expression.getUnaryRelationalExpression(), indent + 1);
        } else {
            StringBuilder b = new StringBuilder();

            String delim = SqlRenderer.NL + indent(indent + 1) + expression.getType().name() + SqlRenderer.NL;
            b.append(expression.getChildren().stream().map(e -> toSqlChild(parent, expression, e, indent)).collect(Collectors.joining(delim)));
            return b.toString();
        }
    }

    /**
     * A negation folded into the negated thing itself — {@code x IS NOT NULL}, {@code x NOT IN (…)},
     * {@code NOT EXISTS (…)} — rather than wrapped as {@code NOT (…)}. Null when there is no such
     * form and the structural negation stands.
     *
     * <p>Equivalent either way in three-valued logic ({@code NOT (x IN s)} and {@code x NOT IN s}
     * agree on NULL too), so this is about the SQL reading the way a person would write it, and
     * about optimisers that detect anti-joins from {@code NOT EXISTS} / {@code NOT IN} specifically.
     *
     * <p>Only a normalized {@code NOT} over a single predicate reaches here: De Morgan has already
     * pushed negation down to the leaves, so there is no {@code NOT (a AND b)} left to fold.
     */
    private String foldedNegation(Source parent, LogicalExpression child, int indent) {
        if (!child.isValue()) {
            return null;
        }
        UnaryLogicalExpression u = child.getUnaryRelationalExpression();
        if (u == null || u.getNode() != null || u.getPlaceholder() != null) {
            return null;
        }
        if (u.getExists() != null) {
            // the anti-join form: NOT EXISTS (…), not NOT (EXISTS (…))
            return "NOT " + toSql(parent, u.getExists(), indent + 1).stripLeading();
        }
        if (u.getLeft() == null || u.getOp() == null) {
            return null;
        }
        return dialect.renderComparison(this, u.getLeft(), resolveType(u.getLeft()),
                u.getOp(), u.getRight(), indent + 2, true);
    }

    private String toSqlChild(Source parent, LogicalExpression node, LogicalExpression child, int indent) {
        String s = toSql(parent, child, indent + 1, true);
        // OR binds looser than AND in SQL: a bare OR child between AND siblings must keep its grouping.
        // effectiveType, not getType: a single-child AND/OR wrapper renders as its content, so an OR
        // hiding under one still needs the parens.
        if (node.getType() == NodeType.AND && child.effectiveType() == NodeType.OR && node.getChildren().size() > 1) {
            return indent(indent + 1) + "(" + SqlRenderer.NL + s + SqlRenderer.NL + indent(indent + 1) + ")";
        }
        return s;
    }

    private String toSql(Source parent, UnaryLogicalExpression unaryLogicalExpression, int indent) {
        if (unaryLogicalExpression.getExists() != null) {
            return SqlRenderer.NL + toSql(parent, unaryLogicalExpression.getExists(), indent);
        } else if (unaryLogicalExpression.getNode() != null) {
            return "(" + SqlRenderer.NL + toSql(parent, unaryLogicalExpression.getNode(), indent, false) + SqlRenderer.NL + indent(indent) + ")";
        } else {
            Expression left = unaryLogicalExpression.getLeft();
            List<Expression> right = unaryLogicalExpression.getRight();
            String op = unaryLogicalExpression.getOp();
            boolean bare = right.isEmpty() && (op == null || op.isBlank())
                    && unaryLogicalExpression.getPlaceholder() == null;
            if (bare && left.getFunction() != null) {
                // A boolean function used as a predicate. Dialects that cannot use the value form
                // in a WHERE (SQL Server's BIT) supply the comparison here instead.
                String predicateSql = toSqlPredicate(left, indent);
                if (predicateSql != null) {
                    return predicateSql;
                }
            }
            TypeDescriptor leftType = resolveType(left);
            if (bare) {
                // A boolean column or other boolean expression standing alone as a predicate.
                return dialect.booleanPredicate(toSql(left, indent), leftType);
            }
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
        b.append(SqlRenderer.NL);

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
        b.append(SqlRenderer.NL);
        b.append(indent(indent + 2)).append("1");
        b.append(SqlRenderer.NL);

        b.append(fromClause(exists.getStart(), exists.getJoin(), indent));

        // exists.getFilter() holds what PushLogicalExpressionRule could not push into a source:
        // top-level OR/NOT and conjuncts referencing outer aliases (correlated residuals).
        String w = filterClause(exists.getStart(), exists.getJoin(), exists.getFilter(), indent);

        String j = joinCols(left, exists, indent + 1);

        b.append(indent(indent)).append(WHERE);
        b.append(SqlRenderer.NL);
        b.append(j);
        b.append(SqlRenderer.NL);
        if (!w.isEmpty()) {
            b.append(indent(indent)).append("AND");
            b.append(SqlRenderer.NL);
            LogicalExpression combined = combinedFilter(exists.getStart(), exists.getJoin(), exists.getFilter());
            if (combined.effectiveType() == NodeType.OR) {
                // textually ANDed with the join correlation above: an OR-headed
                // filter must keep its grouping against SQL operator precedence
                b.append(indent(indent + 1)).append("(").append(SqlRenderer.NL);
                b.append(w);
                b.append(indent(indent + 1)).append(")").append(SqlRenderer.NL);
            } else {
                b.append(w);
            }
        }

        b.append(groupbyClause(false, exists.getStart(), exists.getJoin(), indent));
        b.append(havingClause(exists.getStart(), exists.getJoin(), exists.getHaving(), indent));
        return b.toString();
    }

    private String joinCols(Source left, Exists exists, int indent) {
        String msg = left.getName() + (left.getAlias() != null ? " " + left.getAlias() : "");
        String crit = exists.getCrit();
        Source right = exists.getStart();

        // An explicit join carries no criterion, and its direction is fixed by what was written.
        boolean invers = exists.getColumns() == null && resolver.isInverse(exists.getCrit());

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

        // With explicit columns there is no criterion to invert, and no need: the author wrote
        // which side is which, so start stays start.
        JoinColumns explicit = join.getColumns();
        boolean inverse = explicit == null && resolver.isInverse(join.getCrit());

        String startName = inverse ? rightName : leftName;
        String startAlias = inverse ? rightAlias : leftAlias;
        String endName = inverse ? leftName : rightName;
        String endAlias = inverse ? leftAlias : rightAlias;

        return joinColumns(Range.of(iqlToContext, join), indent, startName, startAlias, endName, endAlias, crit, msg, rightSource, explicit);
    }

    private String joinColumns(int indent, Source start, Source end, String crit, String msg, Source right) {
        return joinColumns(Range.of(iqlToContext, start), indent, start.getName(), start.getAlias(), end.getName(), end.getAlias(), crit, msg, right, null);
    }

    private String joinColumns(Range range, int indent, String startName, String startAlias, String endName, String endAlias, String crit, String msg, Source right, JoinColumns explicit) {

        String firstQualifier = startAlias != null ? startAlias : startName;
        String secondQualifier = endAlias != null ? endAlias : endName;

        StringBuilder b = new StringBuilder();
        b.append(indent(indent));

        Source startBlock = visibilityContext.getLeadingSource(startName);
        Source endBlock = visibilityContext.getLeadingSource(endName);

        List<String> lines = new ArrayList<>();

        // Explicit columns ARE the criterion -- resolving one would answer a question the author
        // has already answered, and possibly answer it differently.
        Relation r = explicit != null
                ? resolver.relationFor(range, startName, endName, explicit)
                : getRelation(range , startName, endName, crit, msg, right.getName());

        for (int i = 0; i < r.getStartColumns().size(); i++) {

            Expression leftExpr  = joinColumnExpression(startBlock, getRelationStartColumn(r, i), firstQualifier);
            Expression rightExpr = joinColumnExpression(endBlock,   getRelationEndColumn(r, i),   secondQualifier);
            TypeDescriptor leftType = resolveType(leftExpr);
            lines.add(dialect.renderComparison(this, leftExpr, leftType, "=", List.of(rightExpr), indent));
        }
        b.append(
                lines.stream().collect(Collectors.joining(
                        SqlRenderer.NL + Identifier.indent( indent -1)  + "AND" +
                        SqlRenderer.NL + Identifier.indent(indent)
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

    /**
     * The {@code OVER (…)} clause of a windowed function call. Lives here with the other clause
     * rendering; {@code FunctionRenderer.function()} delegates to it when a call carries a window.
     */
    public String toSql(Window window, int indent) {
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
            if (window.isOrderDesc() == Order.SORT.DESC) {
                b.append(" DESC");
            } else if (window.isOrderDesc() == Order.SORT.ASC) {
                b.append(" ASC");
            }
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

    /** A window frame bound, e.g. {@code 5 PRECEDING}. */
    private String toSql(Limit limit) {
        return (limit.isCounted() ? limit.getNum() + " " : "") + limit.getName();
    }

    public String toSql(Expression expression, int indent) {
        if (expression.isParenthesized()) {
            return "(" + toSqlUnparenthesized(expression, indent) + ")";
        }
        return toSqlUnparenthesized(expression, indent);
    }

    private String toSqlUnparenthesized(Expression expression, int indent) {
        if (expression.getSelect() != null) {

            StringBuilder b = new StringBuilder();
            b.append("(" + SqlRenderer.NL);

            SqlSelectRenderer s2s = subSelect(iqlToContext, expression.getSelect());

            b.append(s2s.toSql(expression.getSelect(), indent + 2));
            b.append(indent(indent + 1) + ")");

            return b.toString();
        } else if (expression.getFunction() != null) {
            return toSql(expression.getFunction(), indent);
        } else if (expression.getText() != null) {
            String text = expression.getText();
            text = text.replace("\\'", "''");
            return dialect.textLiteral(text);
        } else if (expression.getNumber() != null) {
            return Literals.number(expression.getNumber());
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
        if (t != null && t.getTypeEncoding() instanceof WallClockEncoding wc) {
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

    /** An identifier, rendered as the dialect needs it -- see {@link SqlDialect#renderIdentifier}. */
    private String normal(String text) {
        return dialect.renderIdentifier(identifier, text);
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
