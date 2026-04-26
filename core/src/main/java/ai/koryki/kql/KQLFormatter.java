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

import ai.koryki.iql.BlockLeadingSourceCollector;
import ai.koryki.iql.Identifier;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.Walker;
import ai.koryki.iql.rules.Math;
import ai.koryki.antlr.KorykiaiException;
import ai.koryki.iql.query.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// TODO BlockID dürfen nie übersetzt werden !!!
// TODO wenn das Ausgabefeld keinen Header hat, dann ist es eine Spalte die übersetzt werden muss !!!
public class KQLFormatter {

    private KQLParser.QueryContext script;
    private String description;
    private Translator translator;
    private Query query;
    private KQLVisibilityContext visibilityContext;

    public KQLFormatter(KQLParser.QueryContext script, String description) {
        this(script, description, null, new Translator() {});
    }

    public KQLFormatter(KQLParser.QueryContext script, String description, LinkResolver resolver, Translator translator) {
        this.script = script;
        this.description = description;
        this.translator = translator;

        Map<String, Source> blockIdToLeadingSourceMap = Collections.emptyMap();
        if (resolver != null) {
            query = new KQLQueryMapper(resolver, script, description).toBean();
            blockIdToLeadingSourceMap = Walker.apply(query, new BlockLeadingSourceCollector());
        }

        visibilityContext = new KQLVisibilityContext(blockIdToLeadingSourceMap, select2Alias());
    }

    private Map<Object, Map<String, KQLParser.SourceContext>> select2Alias() {
        ParseTreeWalker ptw = new ParseTreeWalker();
        SelectAliasListener v = new SelectAliasListener();
        ptw.walk(v, script);
        return v.collect();
    }

    public String format() {


        StringBuilder b = new StringBuilder();
        if (description != null) {
                b.append("//" + description.replace(System.lineSeparator(), System.lineSeparator() + "//"));
                b.append(System.lineSeparator());
                b.append(System.lineSeparator());
        }
        if (!script.block().isEmpty()) {
            b.append(indent(0) + "WITH ");
            b.append(toMap(script.block(), 0));
        }
        b.append(toSet(script.set(), 0));
        return b.toString();
    }

    private String toMap(List<KQLParser.BlockContext> cte, int indent) {
        StringBuilder b = new StringBuilder();

        b.append( cte.stream().map(block -> toBlock(indent, block)).collect(Collectors.joining("," + System.lineSeparator())));
        if (b.length() > 0) {
            b.append(System.lineSeparator());
        }
        return b.toString();
    }

    private String toBlock(int indent, KQLParser.BlockContext block) {

        if (block.PLACEHOLDER() != null) {
            return block.ID().getText() + " " + block.PLACEHOLDER().getText();
        } else {

            return block.ID().getText() + " AS ("
                    + System.lineSeparator() +
                    toSet(block.set(), indent + 1) + indent(indent) + ")";
        }
    }

    private String toSet(KQLParser.SetContext set, int indent) {
        StringBuilder b = new StringBuilder();
        String op = set.SET_INTERSECT() != null ? set.SET_INTERSECT().getText() :
                set.SET_MINUS() != null ? set.SET_MINUS().getText() :
                set.SET_UNION() != null ? set.SET_UNION().getText() :
                set.SET_UNIONALL() != null ? set.SET_UNIONALL().getText() : null;


        if (set.LEFT_PAREN() != null) {
            return "(" + toSet(set.set(0), indent) + ")";
        } else if (op != null) {
            b.append(toSet(set.set(0), indent));
            b.append(indent(indent) + op + System.lineSeparator());
            b.append(toSet(set.set(1), indent));
        } else if (set.select() != null) {
            b.append(toSelect(set.select(), indent));
        } else {
            throw new KorykiaiException();
        }

        return b.toString();
    }

    private String toSelect(KQLParser.SelectContext select, int indent) {
        StringBuilder b = new StringBuilder();

        SelectFormatter x = new SelectFormatter(translator, visibilityContext.child(select));
        b.append(x.toSubSelect(select, indent));
        return b.toString();
    }

    private class SelectFormatter {

        private Translator translator;
        private KQLVisibilityContext visibilityContext;

        public SelectFormatter(Translator translator, KQLVisibilityContext visibilityContext) {
            this.translator = translator;
            this.visibilityContext = visibilityContext;
        }

        protected SelectFormatter subSelect(Object child) {

            SelectFormatter s2s = new SelectFormatter(translator, visibilityContext.child(child));
            return s2s;
        }


        private String toSubSelect(KQLParser.SelectContext select, int indent) {

            //visibilityContext.child(select);

            StringBuilder b = new StringBuilder();


            b.append(indent(indent)).append("FIND ");

            b.append(toSource(select.source(), indent));

            StringBuilder l = new StringBuilder();
            l.append(
                    select.link().stream().map(j -> toLink(j)).collect(Collectors.joining(", ")));
            if (!l.isEmpty()) {
                b.append(", ").append(l.toString());
            }
            b.append(System.lineSeparator());
            if (select.filterClause() != null) {
                String where = toLogicalNode(select.filterClause().logical_expression(), indent);
                if (!where.isEmpty()) {
                    b.append(indent(indent)).append("FILTER ").append(where);
                    b.append(System.lineSeparator());
                }
            }
            if (select.fetchClause() != null) {
                String f = select.fetchClause().fetchItem().stream().map(r -> toOut(r, indent)).collect(Collectors.joining(", "));
                if (!f.isEmpty()) {
                    b.append(indent(indent)).append("FETCH ");
                    if (select.fetchClause().DISTINCT() != null) {
                        b.append(" DISTINCT ");
                    }
                    b.append(f);
                    if (select.fetchClause().ROLLUP() != null) {
                        b.append(" ROLLUP");
                    }
                    b.append(System.lineSeparator());
                }
            }

            if (select.limitClause() != null && select.limitClause().INT() != null) {
                int limit = Integer.parseInt(select.limitClause().INT().getText());
                if (limit > 0) {
                    b.append(indent(indent)).append("LIMIT ").append(limit);
                    b.append(System.lineSeparator());
                }
            }
            return b.toString();
        }

//        private Boolean asc(ParseTree t) {
//            if (t instanceof TerminalNode) {
//                TerminalNode n = (TerminalNode) t;
//                if (n.getSymbol().getType() == KQLParser.ASC || n.getSymbol().getType() == KQLParser.DESC) {
//                    return n.getSymbol().getType() == KQLParser.ASC;
//                }
//            }
//            return null;
//        }

        private String toLogicalNode(KQLParser.Logical_expressionContext logicalExpression, int indent) {

            if (logicalExpression.unary_logical_expression() != null) {
                return toUnaryLogicalExpression(logicalExpression.unary_logical_expression(), indent);
            } else if (logicalExpression.NOT() != null) {
                return "NOT " + toLogicalNode(logicalExpression.negate, indent);

            } else {
                String left = toLogicalNode(logicalExpression.left, indent);
                String right = toLogicalNode(logicalExpression.right, indent);
                String op = logicalExpression.AND() != null ? "AND" : "OR";
                return left + " " + op + " " + right;
            }
        }

        private String toUnaryLogicalExpression(KQLParser.Unary_logical_expressionContext unaryLogicalExpressionContext, int indent) {

            String op = unaryLogicalExpressionContext.operator() != null ? unaryLogicalExpressionContext.operator().getText() : null;

            if (unaryLogicalExpressionContext.PLACEHOLDER() != null) {
                return toExpression(unaryLogicalExpressionContext.expression(0), indent) + (op != null ? " " + op : "") + " " + unaryLogicalExpressionContext.PLACEHOLDER().getText();
            } else if (unaryLogicalExpressionContext.logical_expression() != null) {
                return "(" + toLogicalNode(unaryLogicalExpressionContext.logical_expression(), indent) + ")";
            } else if (unaryLogicalExpressionContext.operator() != null) {
                String left = toExpression(unaryLogicalExpressionContext.expression().get(0), indent);

                StringBuilder right = new StringBuilder();

                if (unaryLogicalExpressionContext.expression().size() > 1) {
                    List<KQLParser.ExpressionContext> rl = unaryLogicalExpressionContext.expression().subList(1, unaryLogicalExpressionContext.expression().size());
                    if (isInterval(op)) {
                        right.append(toInterval(rl.get(0), rl.get(1), indent));
                    } else if (isSet(op)) {


                        boolean subselect = !rl.isEmpty() && rl.get(0).select() != null;
                        String intro = subselect ? System.lineSeparator() : "";
                        String extro = subselect ? indent(indent) : "";

                        right.append("(" + intro + rl.stream().map(e -> toExpression(e, indent + 1))
                                .collect(Collectors.joining(", ")) + extro + ")");
                    } else if (!unaryLogicalExpressionContext.expression().isEmpty()) {
                        right.append(rl.stream().map(e -> toExpression(e, indent))
                                .collect(Collectors.joining(", ")));
                    }
                }

                if (op == null) {
                    return left;
                } else if (right.length() == 0) {
                    return left + " " + op;
                } else {
                    return left + " " + op + " " + right;
                }
            } else if (unaryLogicalExpressionContext.exists() != null) {
                return toExists(unaryLogicalExpressionContext.exists(), indent);
            } else {
                throw new KorykiaiException();
            }
        }

        public String toExists(KQLParser.ExistsContext exists, int indent) {

            StringBuilder b = new StringBuilder();
            b.append("EXISTS (");

            SelectFormatter f = new SelectFormatter(translator, visibilityContext.child(exists));
            b.append(f.existsSelect(exists, indent));
            b.append(")");
            return b.toString();
        }

        private String existsSelect(KQLParser.ExistsContext exists, int indent) {
            StringBuilder b = new StringBuilder();
            List<KQLParser.LinkContext> links = exists.link();
            b.append(links.stream().map(l -> toLink(l)).collect(Collectors.joining(", ")));

            if (exists.filterClause() != null) {
                String where = toLogicalNode(exists.filterClause().logical_expression(), indent);
                if (!where.isEmpty()) {
                    b.append(indent(indent)).append("FILTER ").append(where);
                    b.append(System.lineSeparator());
                }
            }
            return b.toString();
        }

        protected String toInterval(KQLParser.ExpressionContext left, KQLParser.ExpressionContext right, int indent) {
            return toExpression(left, indent) + " AND " + toExpression(right, indent);
        }


        private String toOut(KQLParser.FetchItemContext ret, int indent) {
            StringBuilder b = new StringBuilder();

            b.append(toExpression(ret.expression(), indent));
            if (ret.h != null) {
                b.append(" " + ret.h.getText());
            }

            if (ret.ASC() != null) {
                b.append(" " + ret.ASC().getText());
            } else if (ret.DESC() != null) {
                b.append(" " + ret.DESC().getText());
            }
            if (ret.idx != null) {
                b.append(" " + ret.idx.getText());
            }

            return b.toString();
        }


        private String toExpression(List<KQLParser.ExpressionContext> expression, int indent) {

            return expression.stream().map(e -> toExpression(e, 0)).collect(Collectors.joining(", "));
        }


        private String toExpression(KQLParser.ExpressionContext expression, int indent) {

            if (expression.select() != null) {
                return toSelect(expression.select(), indent);
            } else if (expression.LEFT_PAREN() != null) {
                return "(" + toExpression(expression.expression(0), indent) + " )";
            } else if (expression.MULT() != null) {
                return mathFunction(expression, Math.multiply, indent);
            } else if (expression.DIV() != null) {
                return mathFunction(expression, Math.divide, indent);
            } else if (expression.PLUS() != null) {
                return mathFunction(expression, Math.add, indent);
            } else if (expression.BAR() != null) {
                return mathFunction(expression, Math.minus, indent);
            } else if (expression.date_literal() != null) {
                return toExpression(expression.date_literal());
            } else if (expression.field() != null) {
                return toColumn(expression.field(), indent);
            } else if (expression.function() != null) {
                return toFunction(expression.function(), indent);
            } else if (expression.INT() != null) {
                return expression.INT().getText();
            } else if (expression.NUMBER() != null) {
                return expression.NUMBER().getText();
            } else if (expression.SQ_STRING() != null) {
                return expression.SQ_STRING().getText();
            } else {
                throw new KorykiaiException();
            }
        }

        private String mathFunction(KQLParser.ExpressionContext expression, ai.koryki.iql.rules.Math name, int indent) {

            String op = name.getOperator();
            String left = toExpression(expression.left, indent);
            String right = toExpression(expression.right, indent);
            return left + " " + op + " " + right;
        }

        private String toExpression(KQLParser.Date_literalContext date) {

            if (date.TIME_FORMAT() != null) {
                return "TIME " + date.TIME_FORMAT().getText();
            } else if (date.TIMESTAMP_FORMAT() != null) {
                return "TIMESTAMP " + date.TIMESTAMP_FORMAT().getText();
            } else if (date.DATE_FORMAT() != null) {
                return "DATE " + date.DATE_FORMAT().getText();
            } else {
                throw new KorykiaiException();
            }
        }

        private String toColumn(KQLParser.FieldContext column, int indent) {

            StringBuilder b = new StringBuilder();
            String alias = column.alias.getText();
            b.append(alias + ".");
            KQLParser.SourceContext source = visibilityContext.getSource(alias);

            Source block = visibilityContext.getLeadingSource(source.name.getText());
            String sourcename = block != null ? block.getName() : source.name.getText();
            String c = translator.field(sourcename, column.name.getText());
            b.append(c);

            return b.toString();
        }

        private String toFunction(KQLParser.FunctionContext function, int indent) {
            StringBuilder b = new StringBuilder();

            b.append(function.ID().getText());
            b.append("(");
            b.append(function.argument().stream().map(a -> toArgument(a, indent))
                    .collect(Collectors.joining(", ")));
            b.append(")");

            if (function.window() != null) {
                b.append(toWindow(function.window(), indent));
            }
            return b.toString();
        }


        protected String toWindow(KQLParser.WindowContext window, int indent) {
            if (window == null) {
                return "";
            }
            StringBuilder b = new StringBuilder();

            b.append(" OVER (");

            if (!window.partitionex.isEmpty()) {
                b.append("PARTITION " + toExpression(window.partitionex, indent));
            }

            if (!window.orderex.isEmpty()) {
                b.append(" ORDER ");
                b.append(toExpression(window.orderex, indent));
            }

            if (window.frame() != null) {
                b.append(" ROWS BETWEEN ");
                b.append(toLimit(window.frame().lower));
                b.append(" AND ");
                b.append(toLimit(window.frame().upper));
            }

            b.append(")");
            return b.toString();
        }

        protected String toLimit(KQLParser.LimitContext limit) {
            StringBuilder b = new StringBuilder();
            if (limit.INT() != null) {
                b.append(" " + limit.INT().getText());
                if (limit.PRECEDING() != null) {
                    b.append(" " + limit.PRECEDING().getText());
                } else if (limit.FOLLOWING() != null) {
                    b.append(" " + limit.FOLLOWING().getText());
                }
            } else if (limit.UNBOUNDED() != null) {
                b.append(" " + limit.UNBOUNDED().getText());
                if (limit.PRECEDING() != null) {
                    b.append(" " + limit.PRECEDING().getText());
                } else if (limit.FOLLOWING() != null) {
                    b.append(" " + limit.FOLLOWING().getText());
                }
            } else if (limit.CURRENT() != null) {
                b.append(" " + limit.CURRENT().getText());
                b.append(" " + limit.ROW().getText());
            }
            return b.toString();
        }

        private String toArgument(KQLParser.ArgumentContext argument, int indent) {

            if (argument.expression() != null) {
                return toExpression(argument.expression(), indent);
            } else if (argument.identity != null) {
                return argument.identity.getText();
            } else {
                throw new KorykiaiException();
            }
        }

        private String toLink(KQLParser.LinkContext link) {

            StringBuilder b = new StringBuilder();

            if (link.from != null) {
                b.append(link.from.getText());
            }
            String crit = link.crit != null ? link.crit.getText() : null;
            if (crit != null) {
                b.append("-");
                b.append(translator.crit(crit));
            }
            b.append(link.PLUS() != null ? "+" : " ");
            b.append(translator.source(link.source().name.getText()));
            b.append(" ");
            b.append(link.source().alias.getText());

            return b.toString();
        }

        private String toSource(KQLParser.SourceContext source, int indent) {

            StringBuilder b = new StringBuilder();
            b.append(translator.source(source.name.getText()));
            b.append(" ");
            b.append(source.alias.getText());
            return b.toString();
        }
    }

    private String indent(int l) {
        return Identifier.indent(l);
    }

    public static boolean isSet(String op) {
        return "IN".equalsIgnoreCase(op);
    }

    public static boolean isInterval(String op) {
        return "BETWEEN".equalsIgnoreCase(op);
    }


}
