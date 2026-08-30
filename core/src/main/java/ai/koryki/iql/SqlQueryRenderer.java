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

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class SqlQueryRenderer implements SqlRenderer {

    public static final String WITH = "WITH";

    private Query query;
    private final Identifier identifier;
    protected SqlDialect dialect;


    /** The model zone (docs/TEMPORAL.md): set by the caller, never silently defaulted here. */
    private final ZoneId modelZone;
    protected IQLVisibilityContext visibilityContext;
    protected Map<Object, RuleContext> iqlToContext;

    // Per-render output schema: resolved once in toSql, exposed for the read layer (ColumnInfo).
    private ai.koryki.iql.functions.FunctionRenderer functionRenderer;

    @Override
    public ai.koryki.iql.functions.FunctionRenderer getFunctionRenderer() {
        if (functionRenderer == null) {
            functionRenderer = dialect.getFunctionRenderer();
        }
        return functionRenderer;
    }

    /**
     * The usual renderer: names as the catalog holds them, quoted only where the dialect needs it.
     *
     * <p>{@link Identifier#lowercase} for every engine, and that is not a simplification. Unquoted,
     * PostgreSQL folds a name down and Oracle folds it up, so one lowercase catalog resolves on
     * both; and what happens to a name that cannot go unquoted is decided by
     * {@link SqlDialect#renderIdentifier}, not by this constant. The other constructor forces
     * quoting on everything, which is a different question and one only {@code OraviewTest} asks.
     */
    public SqlQueryRenderer(SqlDialect dialect, ZoneId modelZone) {
        this(Identifier.lowercase, dialect, modelZone);
    }

    public SqlQueryRenderer(Identifier identifier, SqlDialect dialect, ZoneId modelZone) {
        this.identifier = identifier;
        this.dialect = dialect;
        this.modelZone = Objects.requireNonNull(modelZone, "modelZone");
    }

    /** The model zone this renderer transpiles for (docs/TEMPORAL.md), threaded to the select renderers. */
    public ZoneId getModelZone() {
        return modelZone;
    }

    @Override
    public SqlDialect getDialect() {
        return dialect;
    }

    @Override
    public Rendered toSql(LinkResolver resolver, IQLVisibilityContext visibilityContext, Query query, Map<Object, RuleContext> iqlToContext) {
        this.iqlToContext = iqlToContext;
        this.visibilityContext = visibilityContext;
        this.query = query;

        // resolve the output schema once — consumed by the read layer's ColumnInfo (decode).
        // Output columns render as plain toSql; encodings are decoded at the read boundary,
        // never converted in SQL.
        List<OutputColumn> outputs = resolveOutputs(resolver, visibilityContext, query);

        return new Rendered(toSql(resolver), outputs);
    }

    private String toSql(LinkResolver resolver) {

        return toSql( resolver, -1);
    }


    private String toSql(LinkResolver resolver, int indent) {
        StringBuilder b = new StringBuilder();

        if (query.getDescription() != null) {
            b.append("--").append(query.getDescription().replace(NL, NL + "--"));
            b.append(NL);
        }

        b.append(toSql(resolver, query.getBlock(), indent));
        b.append(toOutermostSql(resolver, query.getSet(), indent));
        return b.toString();
    }

    protected String toSql(LinkResolver resolver, List<Block> block, int indent) {

        if (block.isEmpty()) {
            return "";
        }

        StringBuilder b = toRecursive(resolver, block, indent);

        b.append(block.stream().map(
                e -> toSql(resolver, e.getId(), e, indent)).collect(Collectors.joining(NL + ", ")));
        b.append(NL);

        return b.toString();
    }

    protected StringBuilder toRecursive(LinkResolver resolver, List<Block> block, int indent) {
        StringBuilder b = new StringBuilder();
        b.append(Identifier.indent(indent)).append(WITH).append(" ");

        boolean recursive = block.stream().anyMatch(x -> Walker.apply(x, new BlockRecursionDetector(resolver)));

        b.append(dialect.recursive(recursive));

        return b;
    }

    /**
     * One name of a block's column list -- {@code WITH b (this, that) AS (...)}.
     *
     * <p>Quoted here, and it was not before: this is the only identifier position that never
     * reached {@link #normal}, and both of its branches can produce a name SQL will not take bare.
     * The first is an alias, so a reserved word reaches it -- {@code WITH b (order) AS} does not
     * parse. The second is a <em>physical</em> column straight out of the catalog, so anything the
     * database allows reaches it. The same names inside the block body came out correctly quoted
     * the whole time, which is what made the mismatch hard to see rather than obvious.
     *
     * <p>Quoting inside this method rather than at the join that calls it, so that a second caller
     * inherits it instead of having to remember.
     */
    private String toHeader(LinkResolver resolver, IQLVisibilityContext v, Out out) {

        if (out.getHeader() != null) {
            return normal(out.getHeader());
        } else if (out.getExpression().getField() != null) {

            Field f = out.getExpression().getField();
            Source s = v.getSource(f.getAlias());
            Source b = v.getLeadingSource(s.getName());
            String sourcename = b != null ? b.getName() : s.getName();
            String header = resolver.getDialectColumn(sourcename, f.getName()).orElse(f.getName());
            return normal(header);
        } else {
            throw new KorykiaiException("missing column-header");
        }
    }

    protected String toSql(LinkResolver resolver, String alias, Block block, int indent) {

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
        b.append(NL);

        if (set.getOperator() != null) {
            b.append(toSql(resolver, set.getLeft(), indent + 1));
            b.append(indent(indent)).append(mapOperator(set)).append(NL);
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
        return dialect.mapSetOperator(set.getOperator());
    }

    protected String toSql(LinkResolver resolver, Set set, int indent) {

        if (set.getSelect() != null) {
            return toSql(resolver, set.getSelect(), indent);
        } else {
            StringBuilder b = new StringBuilder();

            b.append(setOperand(toSql(resolver, set.getLeft(), indent + 1), set, set.getLeft(), false));
            b.append(mapOperator(set));
            b.append(NL);
            b.append(setOperand(toSql(resolver, set.getRight(), indent + 1), set, set.getRight(), true));

            return b.toString();
        }
    }

    private String setOperand(String sql, Set parent, Set child, boolean rightSide) {
        boolean parens = dialect.uniformSetOperatorPrecedence()
                // one precedence level, left-assoc: left children are flat by definition and
                // parenthesized compound operands are a syntax error (SQLite)
                ? child.getSelect() == null && rightSide
                : setOperandNeedsParens(parent, child, rightSide);
        if (parens) {
            return "(" + NL + sql + ")" + NL;
        }
        return sql;
    }

    /**
     * SQL gives INTERSECT higher precedence than UNION/EXCEPT and associates same-level
     * operators left. A nested set-op child needs parens exactly when flat rendering would
     * re-associate it: a lower-precedence child anywhere (UNION under INTERSECT), or a
     * same-precedence child on the right ({@code A MINUS (B UNION C)}). Left same-level
     * children stay flat — that is what left-associativity already encodes.
     */
    static boolean setOperandNeedsParens(Set parent, Set child, boolean rightSide) {
        if (child.getSelect() != null) {
            return false;
        }
        int parentPrec = setPrecedence(parent);
        int childPrec = setPrecedence(child);
        return childPrec < parentPrec || (rightSide && childPrec == parentPrec);
    }

    private static int setPrecedence(Set set) {
        return "INTERSECT".equals(set.getOperator()) ? 2 : 1;
    }

    protected SqlSelectRenderer createSelectRenderer(LinkResolver resolver, IQLVisibilityContext ctx) {
        return new SqlSelectRenderer(identifier, iqlToContext, resolver, ctx, dialect, modelZone);
    }

    protected SqlSelectRenderer createOutermostSelectRenderer(LinkResolver resolver, IQLVisibilityContext ctx) {
        return createSelectRenderer(resolver, ctx);
    }

    protected String toSql(LinkResolver resolver, Select select, int indent) {
        SqlSelectRenderer s2s = createSelectRenderer(resolver, visibilityContext.child(select));
        return s2s.toSql(select, indent + 1);
    }

    protected String toOutermostSql(LinkResolver resolver, Set set, int indent) {
        if (set.getSelect() != null) {
            return toOutermostSql(resolver, set.getSelect(), indent);
        }
        StringBuilder b = new StringBuilder();
        b.append(setOperand(toOutermostSql(resolver, set.getLeft(), indent + 1), set, set.getLeft(), false));
        b.append(mapOperator(set)).append(NL);
        b.append(setOperand(toOutermostSql(resolver, set.getRight(), indent + 1), set, set.getRight(), true));
        return b.toString();
    }

    protected String toOutermostSql(LinkResolver resolver, Select select, int indent) {
        SqlSelectRenderer s2s = createOutermostSelectRenderer(resolver, visibilityContext.child(select));
        return s2s.toSql(select, indent + 1);
    }

    /** An identifier, rendered as the dialect needs it -- see {@link SqlDialect#renderIdentifier}. */
    private String normal(String text) {
        return dialect.renderIdentifier(identifier, text);
    }

    private String indent(int l) {
        return Identifier.indent(l);
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

        sortByFetchPosition(out, Out::getIdx);
        return out;
    }

    /**
     * Puts a collected clause back into the order the author wrote it in FETCH.
     *
     * <p>Three rewrite rules move entries off the select and onto the source they belong to —
     * {@code PushOutRule}, {@code PushGroupRule}, {@code PushOrderRule}, all three with the same
     * {@code removeIf} / {@code add} shape. Collection afterwards runs over the query tree: the
     * leading source, then the join tree, then whatever stayed behind. That is an order nobody
     * chose, so each rule records the FETCH position as {@code idx} and it is restored here. A 0
     * means the entry never had one and belongs at the end.
     *
     * <p>Shared because it was written four times and forgotten once. GROUP BY on the main query was
     * the omission: {@code FETCH c.category_name, p.product_name} over {@code o → od → p → c}
     * grouped by product before category, and reversing the FETCH order changed nothing at all — the
     * position was being ignored rather than used. Written out per clause, a fifth path can repeat
     * that by simply not mentioning it; here it has to remove a call.
     */
    static <T> void sortByFetchPosition(List<T> list, java.util.function.ToIntFunction<T> idx) {
        list.sort(Comparator.comparingInt(t -> {
            int i = idx.applyAsInt(t);
            return i == 0 ? Integer.MAX_VALUE : i;
        }));
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

    public Identifier getIdentifier() {
        return identifier;
    }
}
