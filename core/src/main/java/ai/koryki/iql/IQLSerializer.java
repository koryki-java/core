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
import ai.koryki.iql.logic.NodeType;
import ai.koryki.iql.query.*;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.List;
import java.util.stream.Collectors;

public class IQLSerializer {

    private static final DateTimeFormatter TIMESTAMP_FMT     = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withLocale(Locale.ROOT);
    private static final DateTimeFormatter TIMESTAMP_FMT_MS  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withLocale(Locale.ROOT);
    private static final DateTimeFormatter TIME_FMT           = DateTimeFormatter.ofPattern("HH:mm:ss").withLocale(Locale.ROOT);
    private static final DateTimeFormatter TIME_FMT_MS        = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withLocale(Locale.ROOT);

    private Query query;

    public IQLSerializer(Query query) {

        this.query = query;
    }

    @Override
    public String toString() {
        return toString(query, 0);
    }

    private String toString(Query query, int indent) {

        StringBuilder b = new StringBuilder();

        if (query.getDescription() != null) {
            b.append("//" + query.getDescription().replace(SqlRenderer.NL, SqlRenderer.NL + "//"));
            b.append(SqlRenderer.NL);
            b.append(SqlRenderer.NL);
        }

        if (!query.getBlock().isEmpty()) {
            b.append(indent(indent) + "WITH ");
            b.append(toBlock(query.getBlock(), indent));
        }
        b.append(toString(query.getSet(), indent));
        return b.toString();
    }

    private String toString(Set set, int indent) {

        if (set.getSelect() != null) {
            return toString(set.getSelect(), indent);
        } else {
            StringBuilder b = new StringBuilder();

            b.append(indent(indent) + setOperand(set, set.getLeft(), false, indent));
            //b.append(SqlRenderer.NL);
            b.append(indent(indent) + set.getOperator());
            b.append(SqlRenderer.NL);
            b.append(indent(indent) + setOperand(set, set.getRight(), true, indent));

            return b.toString();
        }
    }

    // mirror of SqlQueryRenderer.setOperandNeedsParens: grouping that SQL precedence
    // would re-associate must survive the IQL text round-trip as explicit parens
    private String setOperand(Set parent, Set child, boolean rightSide, int indent) {
        String s = toString(child, indent);
        if (SqlQueryRenderer.setOperandNeedsParens(parent, child, rightSide)) {
            return "(" + SqlRenderer.NL + s + indent(indent) + ")" + SqlRenderer.NL;
        }
        return s;
    }

    private String toString(Select select, int indent) {
        StringBuilder b = new StringBuilder();
        b.append(indent(indent) + "SELECT");
        if (select.isDistinct()) {
            b.append(" DISTINCT");
        }
        b.append(SqlRenderer.NL);
        b.append(toString(select.getStart(), indent + 1, false));
        b.append(toJoin(select.getStart(), select.getJoin(), indent));

        if (select.getFilter() != null || select.getHaving() != null || !select.getOut().isEmpty() || !select.getGroup().isEmpty() || !select.getOrder().isEmpty()) {
            b.append(indent(indent) + "ALL" + SqlRenderer.NL);
            b.append(toFilter(select.getFilter(), "FILTER", indent + 1));
            b.append(toHaving(select.getHaving(), "HAVING", indent + 1));

            b.append(toOut(select.getOut(), indent + 1));
            b.append(toGroup(select.getGroup(), indent + 1));
            b.append(toOrder(select.getOrder(), indent + 1));


        }

        if (select.isRollup()) {
            b.append(indent(indent) + "ROLLUP");
            b.append(SqlRenderer.NL);
        }

        if (select.getLimit() > 0) {

            b.append(indent(indent) + "LIMIT " + select.getLimit());
            b.append(SqlRenderer.NL);
        }
        return b.toString();
    }

    private String toJoin(Source left, List<Join> join, int indent) {

        StringBuilder b = new StringBuilder();
        b.append(join.stream().map(j -> toString(left, j, indent + 2)).collect(Collectors.joining()));
        return b.toString();
    }

    private String toString(Source left, Join join, int indent) {
        StringBuilder b = new StringBuilder();
        b.append(indent(indent) + "JOIN ");
        if (join.isOptional()) {
            b.append("OPTIONAL ");
        }
        b.append(critOrColumns(join.getCrit(), join.getColumns()));

        if (join.getSource() != null) {
            b.append(toString(join.getSource(), indent + 1, true));
        } else {
            b.append(" REF " + join.getRef() + SqlRenderer.NL);
        }

        b.append(toJoin(join.getSource(), join.getJoin(), indent + 1));
        b.append(indent(indent) + "OWNER");
        b.append(SqlRenderer.NL);

        return b.toString();
    }

    private String toString(Source table, int indent, boolean inline) {
        StringBuilder b = new StringBuilder();
        b.append(indent(inline ? 1 : indent) + table.getName());
        if (table.getAlias() != null) {
            b.append(" " + table.getAlias());
        }
        b.append(SqlRenderer.NL);
        b.append(toOut(table.getOut(), indent + 1));

        b.append(toFilter(table.getFilter(), indent + 1));
        b.append(toGroup(table.getGroup(), indent + 1));
        b.append(toHaving(table.getHaving(), indent + 1));

        b.append(toOrder(table.getOrder(), indent + 1));
        return b.toString();
    }

    private String toOut(List<Out> out, int indent) {

        if (out.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder();

        b.append(indent(indent));
        b.append(out.stream().map(o -> toOut(o, indent + 1)).collect(Collectors.joining( SqlRenderer.NL + indent(indent))));
        b.append(SqlRenderer.NL);
        return b.toString();
    }

    private String toGroup(List<Group> group, int indent) {
        if (group.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder();

        b.append(indent(indent));
        b.append(group.stream().map(o -> toGroup(o, indent + 1)).collect(Collectors.joining(SqlRenderer.NL + indent(indent))));
        b.append(SqlRenderer.NL);

        return b.toString();
    }

    private String toOrder(List<Order> order, int indent) {
        if (order.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder();

        b.append(indent(indent));
        b.append(order.stream().map(o -> toOrder(o, indent + 1)).collect(Collectors.joining(SqlRenderer.NL + indent(indent))));
        b.append(SqlRenderer.NL);

        return b.toString();
    }

    private String toOut(Out out, int indent) {
        StringBuilder b = new StringBuilder();
        b.append("OUT ");
        b.append(toString(out.getExpression(), indent));
        if (out.getHeader() != null) {
            b.append(" " + out.getHeader());
        }
        if (out.getLabel() != null) {
            b.append(" " + quoted(out.getLabel()));
        }
        if (out.getIdx() > 0) {
            b.append(" " + out.getIdx());
        }
        return b.toString();
    }

    private String toHaving(LogicalExpression expression, int indent) {
        return toHaving(expression, "HAVING", indent);
    }

    private String toHaving(LogicalExpression expression, String keyword, int indent) {

        if (expression == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        b.append(indent(indent));
        b.append(keyword + " " );
        b.append(SqlRenderer.NL);
        b.append(toString(expression, indent, true));
        b.append(SqlRenderer.NL);

        return b.toString();
    }

    private String toFilter(LogicalExpression expression, int indent) {
        return toFilter(expression, "FILTER", indent);
    }

    private String toFilter(LogicalExpression expression, String keyword, int indent) {

        if (expression == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        b.append(indent(indent));
        b.append(keyword);
        b.append(SqlRenderer.NL);
        b.append(toString(expression, indent + 1, true));
        b.append(SqlRenderer.NL);
        return b.toString();
    }

    private String toString(LogicalExpression expression, int indent, boolean leading) {

        if (expression == null) {
            return "";
        }

        String prefix = "";
        if (leading) {
            prefix = indent(indent + 1);
        }

        if (expression.isNot()) {
            LogicalExpression negated = expression.getChildren().get(0);
            String s = toString(negated, indent, false);
            // NOT binds tighter than AND and OR, so a negated connective must keep its grouping
            // to survive re-parsing. A normalized tree only ever negates a VAR (an explicitly
            // parenthesized group is already a VAR carrying its own parens), so this guard is a
            // safety net for models built through the API rather than parsed.
            return prefix + "NOT " + (negated.isBinary() ? "(" + s + ")" : s);
        } else if (expression.isValue()) {
            return prefix + toString( expression.getUnaryRelationalExpression(), indent);
        } else {
            StringBuilder b = new StringBuilder();

            String delim = SqlRenderer.NL + indent(indent) +  expression.getType().name() + SqlRenderer.NL + indent(indent + 1);

            b.append(expression.getChildren().stream().map(e -> toStringChild(expression, e, indent)).collect(Collectors.joining(delim)));

            return prefix + b.toString();
        }
    }

    /**
     * One child of an AND/OR node. Mirrors {@code SqlSelectRenderer.toSqlChild}: OR binds looser
     * than AND, so a bare OR child between AND siblings has to be parenthesized or the emitted IQL
     * re-parses with a different tree. Parse-built models cannot reach this (a written group is a
     * parenthesized VAR), but {@code combinedFilter} builds exactly this shape at render time.
     */
    private String toStringChild(LogicalExpression node, LogicalExpression child, int indent) {
        String s = toString(child, indent, false);
        if (node.getType() == NodeType.AND && child.effectiveType() == NodeType.OR && node.getChildren().size() > 1) {
            return "(" + s + ")";
        }
        return s;
    }

    private String toString(UnaryLogicalExpression expression, int indent) {

        if (expression == null) {
            return "";
        }

        StringBuilder b = new StringBuilder();

        if (expression.getPlaceholder() != null) {
            b.append(toString(expression.getLeft(), indent));
            if (expression.getOp() != null) {
                b.append(" " + expression.getOp());
            }
            b.append(" " + expression.getPlaceholder());
        } else if (expression.getExists() != null) {
            b.append(toString(expression.getParent(), expression.getExists(), indent));
        } else if (expression.getNode() != null) {
            return "(" + toString(expression.getNode(), indent, false) + ")";
        } else if (expression.getOp() == null && expression.getRight().isEmpty()) {
            // A bare boolean predicate — the expression alone, no operator to emit.
            b.append(toString(expression.getLeft(), indent));
        } else {

            b.append(toString(expression.getLeft(), indent));
            b.append(" " + expression.getOp());
            if (!expression.getRight().isEmpty()) {
                b.append(" ");
            }
            if (SqlSelectRenderer.isSet(expression.getOp())) {
                b.append("(");
                b.append(toString(expression.getRight(), indent));
                b.append(")");
            } else if (SqlSelectRenderer.isInterval(expression.getOp())) {
                b.append(toString(expression.getRight().get(0), expression.getRight().get(1), indent));
            } else {
                b.append(toString(expression.getRight(), indent));
            }
        }
        return b.toString();
    }

    private String toString(Expression lower, Expression upper, int indent) {
        StringBuilder b = new StringBuilder();
        b.append(toString(lower, indent));
        b.append(" AND ");
        b.append(toString(upper, indent));
        return b.toString();
    }

    private String toString(Expression expression, int indent) {
        String s = toStringBody(expression, indent);
        // explicit grouping survives the round-trip: KQL "(a + b) * c" serializes as
        // multiply((add(a, b)), c) so re-parsing restores the parenthesized flag.
        // Subselect expressions render their own parens — don't double-wrap.
        return expression.isParenthesized() && expression.getSelect() == null ? "(" + s + ")" : s;
    }

    private String toStringBody(Expression expression, int indent) {
        StringBuilder b = new StringBuilder();

        if (expression.getField() != null) {
            b.append(toString(expression.getField(), indent));
        } else if (expression.getText() != null) {
            return expression.getText();
        } else if (expression.getNumber() != null) {
            return Literals.number(expression.getNumber());
        } else if (expression.getLocalDateTime() != null) {
            LocalDateTime dt = expression.getLocalDateTime();
            DateTimeFormatter fmt = dt.getNano() != 0 ? TIMESTAMP_FMT_MS : TIMESTAMP_FMT;
            return '"' + dt.format(fmt) + '"';
        } else if (expression.getLocalDate() != null) {
            return '"' + expression.getLocalDate().toString() + '"';
        } else if (expression.getLocalTime() != null) {
            java.time.LocalTime lt = expression.getLocalTime();
            DateTimeFormatter fmt = lt.getNano() != 0 ? TIME_FMT_MS : TIME_FMT;
            return '"' + lt.format(fmt) + '"';
        } else if (expression.getFunction() != null) {
            return toString(expression.getFunction(), indent);
        } else if (expression.getLogical() != null) {
            return toString(expression.getLogical(), indent, false);
        } else if (expression.getSelect() != null) {
            b.append("(" + SqlRenderer.NL + toString(expression.getSelect(), indent + 1) + indent(indent) + ")");
        } else if (expression.getIdentity() != null) {
            b.append(expression.getIdentity());
        } else if (expression.getDuration() != null) {
            return expression.getDuration().toString();
        } else if (expression.isNull()) {
            b.append("NULL");
        } else {
            throw new KorykiaiException();
        }

         return b.toString();
    }
    private String toString(Function function, int indent) {
        StringBuilder b = new StringBuilder();
        b.append(function.getFunc());
        b.append("(");
        b.append(function.getArguments().stream().map(
                a -> toString(a, indent)).collect(Collectors.joining(", ")));
        b.append(")");

        if (function.getWindow() != null) {
            b.append(toString(function.getWindow(), indent));
        }

        return b.toString();
    }

    protected String toString(Window window, int indent) {
        if (window == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();

        b.append(" OVER (");

        if (!window.getPartition().isEmpty()) {
            b.append("PARTITION " + toString(window.getPartition(), indent));
        }

        if (!window.getOrder().isEmpty()) {
            b.append(" ORDER ");
            b.append(toString(window.getOrder(), indent));
            if (window.isOrderDesc() == Order.SORT.DESC) {
                b.append(" DESC");
            } else if (window.isOrderDesc() == Order.SORT.ASC) {
                b.append(" ASC");
            }
        }

        if (window.getLower() != null) {
            b.append(" ROWS BETWEEN ");
            b.append(toString(window.getLower()));
            b.append(" AND ");
            b.append(toString(window.getUpper()));
        }

        b.append(")");
        return b.toString();
    }

    protected String toString(Limit limit) {
        return (limit.isCounted() ? limit.getNum() + " " : "") + limit.getName();
    }


    private String toString(Field column, int indent) {

        StringBuilder b = new StringBuilder();
        if (column.getAlias() != null) {
            b.append(column.getAlias() + ".");
        }
        b.append(column.getName());

        return b.toString();
    }

    private String toString(List<Expression> expression, int indent) {
        StringBuilder b = new StringBuilder();
        b.append(expression.stream().map(e -> toString(e, indent)).collect(Collectors.joining(", ")));
        return b.toString();
    }

    private String toString(String parent, Exists exists, int indent) {
        StringBuilder b = new StringBuilder();

        b.append("EXISTS (");

        b.append(exists.getParent() + " " + critOrColumns(exists.getCrit(), exists.getColumns())
                + toString(exists.getStart(), indent, true));
        b.append(toJoin(exists.getStart(), exists.getJoin(), indent + 1));

        // residual clauses the push rules could not move onto a single source
        b.append(toFilter(exists.getFilter(), indent + 1));
        b.append(toHaving(exists.getHaving(), indent + 1));

        b.append(indent(indent));
        b.append(")");
        return b.toString();
    }

    private String toOrder(Order order, int indent) {
        StringBuilder b = new StringBuilder();
        b.append("ORDER ");
        b.append(toString(order.getExpression(), indent));
        //b.append(indent(indent) + " ");
        if (order.getSort() != null) {
            b.append(" " + (order.getSort().equals(Order.SORT.ASC) ? "ASC" : "DESC"));
        }
        if (order.getIdx() > 0) {
            b.append(" " + order.getIdx());
        }
        return b.toString();
    }

    private String toGroup(Group group, int indent) {
        StringBuilder b = new StringBuilder();
        b.append("GROUP ");
        b.append(toString(group.getExpression(), indent));
        if (group.getIdx() > 0) {
            b.append(" " + group.getIdx());
        }

        return b.toString();
    }

    private String toBlock(List<Block> list, int indent) {
        StringBuilder b = new StringBuilder();
            b.append( list.stream().map(block -> toBlock(indent, block)).collect(Collectors.joining("," + SqlRenderer.NL)));
            if (b.length() > 0) {
                b.append(SqlRenderer.NL);
            }

        return b.toString();
    }

    private String toBlock(int indent, Block block) {

        if (block.getPlaceholder() != null) {
            return block.getId() + " " + block.getPlaceholder();
        } else {
            return block.getId() + " AS ("
                    + SqlRenderer.NL +
                    toString(block.getSet(), indent + 1) + indent(indent) + ")";
        }
    }

    private String indent(int l) {
        return Identifier.indent(l);
    }

    private String quoted(String text) {

        if (text == null) {
            return "";
        }

        // Escaped on the way out, because the mapper unescapes on the way in. The bean holds the
        // label as it was typed; writing a quote in it bare would close the string early and
        // produce source that no longer parses. Only labels come through here.
        String normal = Identifier.normal(Identifier.quoted, text);
        return normal;
    }

    /**
     * A join's criterion, or its columns when it was written out. Always the pair spelling, so what
     * is read back is byte-identical to what was written.
     */
    private static String critOrColumns(String crit, JoinColumns columns) {
        if (crit != null) {
            return crit;
        }
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < columns.left().size(); i++) {
            b.append(i > 0 ? ", " : "").append(columns.left().get(i)).append("=").append(columns.right().get(i));
        }
        return b.append("]").toString();
    }
}
