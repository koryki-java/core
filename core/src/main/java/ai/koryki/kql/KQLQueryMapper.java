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
import ai.koryki.antlr.PositionException;
import ai.koryki.antlr.Range;
import ai.koryki.antlr.RangeException;
import ai.koryki.iql.Identifier;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SelectScopeCollector;
import ai.koryki.iql.query.*;
import ai.koryki.iql.query.Set;
import ai.koryki.iql.logic.Normalizer;
import ai.koryki.iql.time.Time;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class KQLQueryMapper {

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS]").withLocale(Locale.ROOT);

    private KQLParser.QueryContext script;
    private String description;
    private final LinkResolver resolver;

    private final Map<String, Source> blockIdToSourceMap = new HashMap<>();
    /**
     * Node → parse context, keyed by <em>identity</em>.
     *
     * <p>An {@code IdentityHashMap} rather than a {@code HashMap}: the key is the model node itself,
     * and two structurally equal nodes are still two different places in the query. It works today
     * because no bean overrides {@code equals}/{@code hashCode} — this states that assumption instead
     * of relying on it, so adding one later cannot silently make two nodes share a position.
     */
    private final Map<Object, RuleContext> iqlToContext = new java.util.IdentityHashMap<>();

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
            // Only an unwritten join is inferred. Explicit columns ARE the criterion; looking one up
            // would put a name next to them that the IQL form then writes instead.
            if (join.getCrit() == null && join.getColumns() == null) {
                // anonymous criteria must be resolved here
                Source fromTable = blockIdToSourceMap.get(f);

                if (fromTable == null) {
                    fromTable = aliasToTable.get(f);
                }
                if (fromTable == null) {
                    throw aliasError(link, "unknown alias '" + f
                            + "': no source with that alias is in scope here");
                }
                String t = link.source().alias.getText();

                // first test for blockId, as visibilityContext may already contain alias, but this is the table itself
                Source toTable = blockIdToSourceMap.get(link.source().name.getText());
                if (toTable == null) {
                    toTable = aliasToTable.get(t);
                }
                if (toTable == null) {
                    throw aliasError(link, "unknown alias '" + t
                            + "': no source with that alias is in scope here");
                }

                Optional<String> l = resolver.findLink(Range.of(iqlToContext, toTable) , fromTable.getName(),
                        toTable.getName(), null);

                if (l.isEmpty()) {
                    throw new KorykiaiException(fromTable.getAlias() + " " + fromTable.getName() + " " + toTable.getName() + " " + toTable.getAlias() + " " + Range.of(iqlToContext, join));
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
        } else if (unaryLogicalExpressionContext.BETWEEN() != null) {
            // BETWEEN has its own production and is no longer part of `operator`, so it needs its
            // own branch — and it must come before the bare-predicate one below, or the range would
            // fall through there and lose both operator and bounds.
            //
            // The operator text comes from the token rather than a literal "BETWEEN" so that the
            // grammar stays the single source of it. IQLSerializer emits getOp() verbatim, so a
            // second spelling here would surface in the serialized IQL rather than in a compiler
            // error.
            UnaryLogicalExpression bean = build(unaryLogicalExpressionContext, UnaryLogicalExpression::new);
            bean.setOp(unaryLogicalExpressionContext.BETWEEN().getText());
            bean.setLeft(toExpression(unaryLogicalExpressionContext.expression().get(0)));
            bean.getRight().add(toExpression(unaryLogicalExpressionContext.expression().get(1)));
            bean.getRight().add(toExpression(unaryLogicalExpressionContext.expression().get(2)));
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
        } else if (!unaryLogicalExpressionContext.expression().isEmpty()) {
            // A boolean-valued expression standing alone as a predicate — no operator, no right
            // operand. FunctionValidator checks it really is BOOLEAN; the renderer routes it
            // through the dialect's predicate form.
            UnaryLogicalExpression bean = build(unaryLogicalExpressionContext, UnaryLogicalExpression::new);
            bean.setLeft(toExpression(unaryLogicalExpressionContext.expression().getFirst()));
            return bean;
        } else {
            throw new KorykiaiException();
        }
    }

    private Exists toExists(Map<String, Source> aliasToTable, KQLParser.ExistsContext exists) {

        Exists e = build(exists, Exists::new);

        KQLParser.ExistslinkContext first = exists.existslink();

        String parentAlias = first.from.getText();
        e.setParent(parentAlias);

        Source fromTable = aliasToTable.get(parentAlias);

        String toTableName = first.source().name.getText();
        String toAlias = first.source().alias.getText();
        Source toTable = build(first.source(), Source::new);
        toTable.setName(toTableName);
        toTable.setAlias(toAlias);

        ParsedJoin parsed = parseJoin(first.join());
        e.setCrit(parsed.crit());
        e.setColumns(parsed.columns());

        // Only an unwritten join is inferred. Explicit columns ARE the criterion, so looking one up
        // would overwrite what the author wrote.
        if (e.getCrit() == null && e.getColumns() == null) {
            Optional<String> l = resolver.findLink(Range.of(iqlToContext, toTable), fromTable.getName(),
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
        joins.put(e.getStart().getAlias(), e.getJoin());
        String from = e.getStart().getAlias();
        iterateLinks(
                exists.link(),
                from,
                aToT,
                joins
        );

        if (exists.filterClause() != null) {
            LogicalExpression node = toLogicalNode(aToT, exists.filterClause().logical_expression());
            node = Normalizer.normalize(node);
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
            o.setLabel(unquoteDouble(fetch.label.getText()));
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
            Expression inner = toExpression(expression.expression(0));
            inner.setParenthesized(true);
            return inner;
        } else if (expression.MULT() != null) {
            return mathFunction(expression, ai.koryki.iql.functions.MathOp.multiply.name());
        } else if (expression.DIV() != null) {
            return mathFunction(expression, ai.koryki.iql.functions.MathOp.divide.name());
        } else if (expression.PLUS() != null) {
            if (expression.left == null) {
                return toExpression(expression.expression(0));
            }
            return mathFunction(expression, ai.koryki.iql.functions.MathOp.add.name());
        } else if (expression.BAR() != null) {
            if (expression.left == null) {
                Expression inner = toExpression(expression.expression(0));
                // A negated duration literal becomes a duration with negative components right
                // away, instead of wrapping a negate() call around it.
                //
                // Measured: duckdb and postgresql render a duration as ONE interval literal, where
                // -(...) was valid. oracle, mariadb and trino render a chain a + b -- they reject a
                // unary minus on that, and without parentheses it bound to the first part only:
                // -2d4h became -2d + 4h, eight hours off without an error. The negate() detour
                // also bypassed the DATE-to-TIMESTAMP promotion TEMPORAL.md promises for a
                // duration with a clock part (visible on trino). This way a negated duration takes
                // the same path as a positive one.
                if (inner.getDuration() != null && !inner.isParenthesized()) {
                    Expression bean = build(expression, Expression::new);
                    bean.setDuration(new ai.koryki.iql.query.Duration(
                            inner.getDuration().getComponents().stream()
                                    .map(c -> new ai.koryki.iql.query.Duration.Component(-c.value(), c.unit()))
                                    .toList()));
                    return bean;
                }
                Expression bean = build(expression, Expression::new);
                Function f = build(expression, Function::new);
                f.setFunc(ai.koryki.iql.functions.MathOp.negate.name());
                f.setArguments(List.of(inner));
                bean.setFunction(f);
                return bean;
            }
            return mathFunction(expression, ai.koryki.iql.functions.MathOp.minus.name());
        } else if (expression.temporal_literal() != null) {
            return toExpression(expression.temporal_literal());
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
            bean.setNumber(new BigInteger(expression.INT().getText()));
            return bean;
        } else if (expression.NUMBER() != null) {
            Expression bean = build(expression, Expression::new);

            BigDecimal bigDecimal = new BigDecimal(expression.NUMBER().getText());

            bean.setNumber(bigDecimal);
            return bean;
        } else if (expression.SQ_STRING() != null) {
            Expression bean = build(expression, Expression::new);
            bean.setText(expression.SQ_STRING().getText());
            return bean;
        } else if (expression.NULL() != null) {
            Expression bean = build(expression, Expression::new);
            bean.setNull(true);
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

    private Expression toExpression(KQLParser.Temporal_literalContext date) {
        Expression bean = build(date, Expression::new);

        if (date.TIME_STRING() != null) {
            bean.setLocalTime(LocalTime.parse(unquoteDouble(date.TIME_STRING().getText())));
        } else if (date.TIMESTAMP_STRING() != null) {
            bean.setLocalDateTime(LocalDateTime.parse(unquoteDouble(date.TIMESTAMP_STRING().getText()), TIMESTAMP_FMT));
        } else if (date.DATE_STRING() != null) {
            bean.setLocalDate(LocalDate.parse(unquoteDouble(date.DATE_STRING().getText())));
        } else if (date.DURATION() != null) {
            bean.setDuration(toDuration(date.DURATION()));
        }
        return bean;
    }

    public Duration toDuration(TerminalNode duration) {
        return Time.duration(duration.getText());
    }

    /**
     * The text of a double-quoted literal, as the value it stands for.
     *
     * <p>Unescapes as well as unquotes. {@code STRING : '"' ('\\"' | .)*? '"'} accepts an escaped
     * quote inside a label, and stopping at the outer pair keeps the backslash in the value - so a
     * FETCH label written {@code "The \"best\" seller"} reached the reader's column heading with
     * its escapes showing.
     *
     * <p>The serialiser puts them back ({@code IQLSerializer.quoted}), so what is held here is the
     * label as it was typed and what is written out is valid source again. A no-op for the date and
     * time literals that also come through here: their tokens cannot contain a quote.
     */
    private static String unquoteDouble(String s) {
        return s.substring(1, s.length() - 1).replace("\\\"", "\"");
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
            if (a.logical_expression() != null) {
                Expression e = build(a, Expression::new);
                e.setLogical(toLogicalNode(java.util.Map.of(), a.logical_expression()));
                f.getArguments().add(e);
            } else if (a.expression() != null) {
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
            if (window.DESC() != null) bean.setOrderDesc(Order.SORT.DESC);
            else if (window.ASC() != null) bean.setOrderDesc(Order.SORT.ASC);
        }
        if (window.frame() != null) {
            bean.setLower(toFrameBound(window.frame().lower));
            bean.setUpper(toFrameBound(window.frame().upper));
        }

        return bean;
    }

    public Limit toFrameBound(KQLParser.Frame_boundContext limit) {

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

    /**
     * Either half of a {@code join} clause: the criterion it names, or the columns it writes out.
     * Never both, and never neither once a clause is present.
     */
    private record ParsedJoin(String crit, JoinColumns columns) { }

    /**
     * Reads a {@code join} clause. Shared by the link and the exists path deliberately: both accept
     * the same three forms, and when they each read the grammar themselves they drift apart — one
     * gains a form the other does not, and only a fixture that happens to use exists finds out.
     *
     * <p>{@code [a, b]} yields the same list on both sides, so the shorthand disappears here and no
     * consumer downstream has to know it existed.
     */
    private static ParsedJoin parseJoin(KQLParser.JoinContext join) {
        if (join == null) {
            return new ParsedJoin(null, null);
        }
        if (join.crit != null) {
            return new ParsedJoin(join.crit.getText(), null);
        }
        if (!join.col.isEmpty()) {
            return new ParsedJoin(null, JoinColumns.shared(text(join.col)));
        }
        return new ParsedJoin(null, new JoinColumns(text(join.left), text(join.right)));
    }

    private static List<String> text(List<org.antlr.v4.runtime.Token> tokens) {
        return tokens.stream().map(org.antlr.v4.runtime.Token::getText).toList();
    }

    private Join toJoin(KQLParser.LinkContext link, Map<String, Source> aliasToTable) {

        String toTableName = link.source().name.getText();
        String toAlias = link.source().alias.getText();
        Join bean = build(link, Join::new);
        ParsedJoin parsed = parseJoin(link.join());
        bean.setCrit(parsed.crit());
        bean.setColumns(parsed.columns());


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
            // Re-using an alias is the back-reference syntax — that is how a cycle is written
            // (see the cycle_1 validator fixture) — but only when it names the same table.
            // Rebinding it to a different one would silently resolve to the first source.
            if (!toTableName.equals(t.getName())) {
                throw aliasError(link, "ambiguous alias '" + toAlias + "': already bound to "
                        + t.getName() + ", cannot rebind it to " + toTableName);
            }
            bean.setRef(toAlias);
        }

        return bean;
    }

    /**
     * A positioned alias error. These are raised here rather than collected as violations because the
     * mapper cannot build a usable tree without a resolved alias — everything downstream would trip
     * over the same gap. {@link PositionException} carries line/column, so the caller can point at it.
     */
    private static RangeException aliasError(KQLParser.LinkContext link, String message) {
        Range r = Range.range(link);
        return new RangeException(r, message);
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
