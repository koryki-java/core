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
import ai.koryki.iql.query.*;

import java.text.DecimalFormat;
import java.util.List;
import java.util.stream.Collectors;

public class IQLSerializer {

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
            b.append("//" + query.getDescription().replace(System.lineSeparator(), System.lineSeparator() + "//"));
            b.append(System.lineSeparator());
            b.append(System.lineSeparator());
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

            b.append(indent(indent) + toString(set.getLeft(), indent));
            //b.append(System.lineSeparator());
            b.append(indent(indent) + set.getOperator());
            b.append(System.lineSeparator());
            b.append(indent(indent) + toString(set.getRight(), indent));

            return b.toString();
        }
    }

    private String toString(Select select, int indent) {
        StringBuilder b = new StringBuilder();
        b.append(indent(indent) + "SELECT");
        if (select.isDistinct()) {
            b.append(" DISTINCT");
        }
        b.append(System.lineSeparator());
        b.append(toString(select.getStart(), indent + 1, false));
        b.append(toJoin(select.getStart(), select.getJoin(), indent));

        if (select.getFilter() != null || select.getHaving() != null || !select.getOut().isEmpty() || !select.getGroup().isEmpty() || !select.getOrder().isEmpty()) {
            b.append(indent(indent) + "ALL" + System.lineSeparator());
            b.append(toFilter(select.getFilter(), "FILTER", indent + 1));
            b.append(toHaving(select.getHaving(), "HAVING", indent + 1));

            b.append(toOut(select.getOut(), indent + 1));
            b.append(toGroup(select.getGroup(), indent + 1));
            b.append(toOrder(select.getOrder(), indent + 1));


        }

        if (select.isRollup()) {
            b.append(indent(indent) + "ROLLUP");
            b.append(System.lineSeparator());
        }

        if (select.getLimit() > 0) {

            b.append(indent(indent) + "LIMIT " + select.getLimit());
            b.append(System.lineSeparator());
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
//        if (join.isInvers()) {
//            b.append("INVERS ");
//        }
        b.append(join.getCrit());

        if (join.getSource() != null) {
            b.append(toString(join.getSource(), indent + 1, true));
        } else {
            b.append(" REF " + join.getRef() + System.lineSeparator());
        }

        b.append(toJoin(join.getSource(), join.getJoin(), indent + 1));
        b.append(indent(indent) + "OWNER");
        b.append(System.lineSeparator());

        return b.toString();
    }

    private String toString(Source table, int indent, boolean inline) {
        StringBuilder b = new StringBuilder();
        b.append(indent(inline ? 1 : indent) + table.getName());
        if (table.getAlias() != null) {
            b.append(" " + table.getAlias());
        }
        b.append(System.lineSeparator());
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
        b.append(out.stream().map(o -> toOut(o, indent + 1)).collect(Collectors.joining( System.lineSeparator() + indent(indent))));
        b.append(System.lineSeparator());
        return b.toString();
    }

    private String toGroup(List<Group> group, int indent) {
        if (group.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder();

        b.append(indent(indent));
        b.append(group.stream().map(o -> toGroup(o, indent + 1)).collect(Collectors.joining(System.lineSeparator() + indent(indent))));
        b.append(System.lineSeparator());

        return b.toString();
    }

    private String toOrder(List<Order> order, int indent) {
        if (order.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder();

        b.append(indent(indent));
        b.append(order.stream().map(o -> toOrder(o, indent + 1)).collect(Collectors.joining(System.lineSeparator() + indent(indent))));
        b.append(System.lineSeparator());

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
        b.append(System.lineSeparator());
        b.append(toString(expression, indent, true));
        b.append(System.lineSeparator());

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
        b.append(System.lineSeparator());
        b.append(toString(expression, indent + 1, true));
        b.append(System.lineSeparator());
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
            return prefix + "NOT " + toString(expression.getChildren().get(0), indent, false);
        } else if (expression.isValue()) {
            return prefix + toString( expression.getUnaryRelationalExpression(), indent);
        } else {
            StringBuilder b = new StringBuilder();

            String delim = System.lineSeparator() + indent(indent) +  expression.getType().name() + System.lineSeparator() + indent(indent + 1);

            b.append(expression.getChildren().stream().map(e -> toString(e, indent, false)).collect(Collectors.joining(delim)));

            return prefix + b.toString();
        }
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
        } else {

            b.append(toString(expression.getLeft(), indent));
            b.append(" " + expression.getOp());
            if (!expression.getRight().isEmpty()) {
                b.append(" ");
            }
            if (SqlQueryRenderer.isSet(expression.getOp())) {
                b.append("(");
                b.append(toString(expression.getRight(), indent));
                b.append(")");
            } else if (SqlQueryRenderer.isInterval(expression.getOp())) {
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
        StringBuilder b = new StringBuilder();

        if (expression.getField() != null) {
            b.append(toString(expression.getField(), indent));
        } else if (expression.getText() != null) {
            return expression.getText();
        } else if (expression.getNumber() != null) {
            DecimalFormat df = new DecimalFormat("#.######");  // Up to 2 decimals
            return df.format(expression.getNumber());
        } else if (expression.getLocalDateTime() != null) {
            return "TIMESTAMP '" + expression.getLocalDateTime() + "'";
        } else if (expression.getLocalDate() != null) {
            return "DATE '" + expression.getLocalDate() + "'";
        } else if (expression.getLocalTime() != null) {
            return "TIME '" + expression.getLocalTime() + "'";
        } else if (expression.getFunction() != null) {
            return toString(expression.getFunction(), indent);
        } else if (expression.getSelect() != null) {
            b.append("(" + System.lineSeparator() + toString(expression.getSelect(), indent + 1) + indent(indent) + ")");
        } else if (expression.getIdentity() != null) {
            b.append(expression.getIdentity());
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
        return (limit.getNum() > 0 ? limit.getNum() + " " : "") + limit.getName();
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

//        if (exists.isInvers()) {
//            b.append("INVERS ");
//        }

        b.append(exists.getParent() + " " + exists.getCrit() + toString(exists.getSource(), indent, true));
        b.append(toJoin(exists.getSource(), exists.getJoin(), indent + 1));

        b.append(indent(indent));
        b.append(")");
        return b.toString();
    }

    private String toOrder(Order order, int indent) {
        StringBuilder b = new StringBuilder();
        b.append("ORDER ");
        if (order.getExpression() != null) {
            b.append(toString(order.getExpression(), indent));
        } else if (order.getHeader() != null) {
            b.append(" " + order.getHeader());
        }
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
            b.append( list.stream().map(block -> toBlock(indent, block)).collect(Collectors.joining("," + System.lineSeparator())));
            if (b.length() > 0) {
                b.append(System.lineSeparator());
            }

        return b.toString();
    }

    private String toBlock(int indent, Block block) {

        if (block.getPlaceholder() != null) {
            return block.getId() + " " + block.getPlaceholder();
        } else {
            return block.getId() + " AS ("
                    + System.lineSeparator() +
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

        return Identifier.normal(Identifier.quoted, text);
    }
}
