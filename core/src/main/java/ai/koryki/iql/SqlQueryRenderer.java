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
import org.antlr.v4.runtime.RuleContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SqlQueryRenderer implements SqlRenderer {

    public static final String WITH = "WITH";

    private Query query;
    private Identifier identifier = Identifier.lowercase;
    private final FunctionRenderer functionRenderer;
    protected IQLVisibilityContext visibilityContext;
    protected Map<Object, RuleContext> iqlToContext;


    public SqlQueryRenderer() {
        this(Identifier.lowercase, new FunctionRenderer() {});
    }

    public SqlQueryRenderer(FunctionRenderer functionRenderer) {
        this(Identifier.lowercase, functionRenderer);
    }

    public SqlQueryRenderer(Identifier identifier) {
        this(identifier, new FunctionRenderer() {});
    }

    public SqlQueryRenderer(Identifier identifier, FunctionRenderer functionRenderer) {
        this.identifier = identifier;
        this.functionRenderer = functionRenderer;
    }

    @Override
    public String toSql(LinkResolver resolver, IQLVisibilityContext visibilityContext, Query query, Map<Object, RuleContext> iqlToContext) {
        this.iqlToContext = iqlToContext;
        this.visibilityContext = visibilityContext;
        this.query = query;
        return toSql(resolver);
    }

    private String toSql(LinkResolver resolver) {

        return toSql( resolver, -1);
    }


    protected String toSql(LinkResolver resolver, int indent) {
        StringBuilder b = new StringBuilder();

        if (query.getDescription() != null) {
            b.append("--" + query.getDescription().replace(System.lineSeparator(), System.lineSeparator() + "--"));
            b.append(System.lineSeparator());
            b.append(System.lineSeparator());
        }

        b.append(toSql(resolver, query.getBlock(), indent));
        b.append(toSql(resolver, query.getSet(), indent));
        return b.toString();
    }

    protected String toSql(LinkResolver resolver, List<Block> block, int indent) {

        if (block.isEmpty()) {
            return "";
        }

        StringBuilder b = toRecursive(resolver, block, indent);

        b.append(block.stream().map(
                e -> toSql(resolver, e.getId(), e, indent)).collect(Collectors.joining(System.lineSeparator() + ", ")));
        b.append(System.lineSeparator());

        return b.toString();
    }

    protected StringBuilder toRecursive(LinkResolver resolver, List<Block> block, int indent) {
        StringBuilder b = new StringBuilder();
        b.append(Identifier.indent(indent) + WITH + " ");

        boolean recursive = block.stream().anyMatch(x -> Walker.apply(x, new BlockRecursionDetector(resolver)));
        if (recursive) {
            b.append("RECURSIVE ");
        }
        return b;
    }

    private String toHeader(LinkResolver resolver, IQLVisibilityContext v, Out out) {

        if (out.getHeader() != null) {
            return out.getHeader();
        } else if (out.getExpression().getField() != null) {

            Field f = out.getExpression().getField();
            Source s = v.getSource(f.getAlias());
            Source b = v.getLeadingSource(s.getName());
            String sourcename = b != null ? b.getName() : s.getName();
            String header = resolver.getDialectColumn(sourcename, f.getName()).orElse(f.getName());
            return header;
        } else {
            throw new KorykiaiException("missing column-header");
        }
    }

    protected String toSql(LinkResolver resolver, String alias, Block block, int indent) {

        Map<String, Source> recursiveAliasToTableMap = Walker.apply(block, new AliasToSourceCollector());


        return toSql(resolver, alias, block.getSet(), indent);
    }


    protected String toSql(LinkResolver resolver, String alias, Set set, int indent) {
        StringBuilder b = new StringBuilder();
        b.append(normal(alias));

        List<Out> out = SqlQueryRenderer.collectOut(set);
        IQLVisibilityContext leadingSelect = visibilityContext.child(SqlQueryRenderer.select(set));

        b.append(" (");
        b.append(out.stream().map(o -> toHeader(resolver, leadingSelect, o)).collect(Collectors.joining(", ")));
        b.append(")");

        b.append(" AS (");
        b.append(System.lineSeparator());

        if (set.getOperator() != null) {
            b.append(toSql(resolver, set.getLeft(), indent + 1));
            b.append(indent(indent)).append(mapOperator(set)).append(System.lineSeparator());
            b.append(toSql(resolver, set.getRight(), indent + 1));
        } else {
            b.append(toSql(resolver, set.getSelect(), indent));
        }
        b.append(")");
        return b.toString();
    }

    protected String mapOperator(Set set) {
        if (set.getOperator().equals("UNIONALL")) {
            return "UNION ALL";
        }
        return set.getOperator();
    }

    protected String toSql(LinkResolver resolver, Set set, int indent) {

        if (set.getSelect() != null) {
            return toSql(resolver, set.getSelect(), indent);
        } else {
            StringBuilder b = new StringBuilder();

            b.append(toSql(resolver, set.getLeft(), indent + 1));
            b.append(mapOperator(set));
            b.append(System.lineSeparator());
            b.append(toSql(resolver, set.getRight(), indent + 1));

            return b.toString();
        }
    }

    protected String toSql(LinkResolver resolver, Select select, int indent) {
        StringBuilder b = new StringBuilder();

        SqlSelectRenderer s2s = new SqlSelectRenderer(identifier, iqlToContext, resolver,
                visibilityContext.child(select),
                functionRenderer);

        b.append(s2s.toSql(select, indent + 1));
        return b.toString();
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

    @Override
    public FunctionRenderer getFunctionTranslator() {
        return functionRenderer;
    }

    public static Select select(Query query) {
        return select(query.getSet());
    }
    public static Select select(Set set) {
        if (set.getSelect() != null) {
            return set.getSelect();
        } else {
            return select(set.getLeft());
        }
    }

    public static List<Out> collectOut(Query query) {
        return collectOut(select(query));
    }

    public static List<Out> collectOut(Block block) {
        return collectOut(select(block.getSet()));
    }

    public static List<Out> collectOut(Set set) {

        if (set.getSelect() != null) {
            return collectOut(set.getSelect());
        } else {
            return collectOut(set.getLeft());
        }
    }

    public static List<Out> collectOut(Select select) {
        List<Out> out = new ArrayList<>();
        if (select == null) {
            return out;
        }
        out.addAll(select.getStart().getOut());
        out.addAll(collectOut(select.getJoin()));
        out.addAll(select.getOut());

        Comparator<Out> c = new Comparator<Out>() {
            @Override
            public int compare(Out o1, Out o2) {

                int p1 = o1.getIdx() == 0 ? Integer.MAX_VALUE : o1.getIdx();
                int p2 = o2.getIdx() == 0 ? Integer.MAX_VALUE : o2.getIdx();
                return p1 - p2;
            }
        };

        out.sort(c);
        return out;
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

    public static boolean isSet(String op) {
        return "IN".equalsIgnoreCase(op);
    }

    public static boolean isInterval(String op) {
        return "BETWEEN".equalsIgnoreCase(op);
    }

    public Identifier getIdentifier() {
        return identifier;
    }
}
