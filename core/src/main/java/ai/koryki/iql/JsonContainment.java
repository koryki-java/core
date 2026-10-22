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

import ai.koryki.catalog.schema.Index;
import ai.koryki.catalog.schema.IndexKey;
import ai.koryki.catalog.schema.Table;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Field;
import ai.koryki.iql.query.LogicalExpression;
import ai.koryki.iql.query.Selection;
import ai.koryki.iql.query.Selector;
import ai.koryki.iql.query.Source;
import ai.koryki.iql.query.UnaryLogicalExpression;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A comparison of one key of a map with a text, asked of the database as containment when an index
 * can answer it that way: {@code t.attrs@color = 'red'} as {@code attrs @> '{"color":"red"}'}
 * instead of {@code attrs ->> 'color' = 'red'}.
 *
 * <p>The two are the same question only under conditions, and the rewrite is made only where all of
 * them hold; where one is in doubt the comparison stays as it was.
 *
 * <ul>
 *   <li><b>An unknown counts as false.</b> A missing key makes the comparison NULL and the
 *       containment false. Under a filter they drop the same rows; under a negation, in an
 *       expression that is a value, they do not. The caller therefore asks only for a position that
 *       no negation is above and that is a filter.
 *   <li><b>The values are text.</b> The comparison reads the value as text, so a JSON number 1
 *       equals '1'; containment of the string "1" does not match the number. The catalog declares
 *       the map's value as text, and that declaration is the contract: data that breaks it is data
 *       the catalog describes wrongly.
 *   <li><b>One key.</b> A path is rewritten only when it is one key of the map itself. A deeper
 *       path, a position, a slice and a key the catalog types otherwise stay as they were.
 *   <li><b>An index asks for it.</b> The table must have an index over all rows of the column that
 *       the dialect says answers containment, and none over this very key: a btree on the key
 *       answers the comparison better, and nothing is rewritten against it.
 * </ul>
 *
 * <p>Without `indexes` in the catalog nothing is rewritten, so a catalog that says nothing gets the
 * SQL it always got.
 */
final class JsonContainment {

    private JsonContainment() {}

    /** A comparison that can be asked as containment. */
    record Candidate(String column, String columnSql, String key, String value) {}

    /**
     * The comparison as a candidate, or null when any condition fails.
     *
     * @param indent the indentation the column is rendered with
     */
    static Candidate candidate(SqlSelectRenderer renderer, UnaryLogicalExpression u, int indent) {
        if (u.getExists() != null
                || u.getNode() != null
                || u.getPlaceholder() != null
                || !"=".equals(u.getOp())
                || u.getLeft() == null
                || u.getRight().size() != 1) {
            return null;
        }
        Expression left = u.getLeft();
        Selection selection = left.getSelection();
        if (selection == null || selection.getSelectors().size() != 1) {
            return null;
        }
        Selector selector = selection.getSelectors().get(0);
        if (selector.getIndex() != null
                || selector.getFrom() != null
                || selector.getTo() != null
                || selector.key() == null) {
            return null;
        }
        Field field = selection.getBase().getField();
        if (field == null || field.getAlias() == null) {
            return null;
        }
        String value = textLiteral(u.getRight().get(0));
        if (value == null) {
            return null;
        }

        TypeDescriptor baseType;
        TypeDescriptor type;
        try {
            baseType = renderer.resolveType(selection.getBase());
            type = renderer.resolveType(left);
        } catch (RuntimeException unresolved) {
            return null;
        }
        // a map stored as JSON is a JSON column whose declared shape is the map
        TypeDescriptor shape = baseType == null ? null : baseType.shape();
        if (shape == null
                || type == null
                || !CoreTypeFamily.JSON.equals(baseType.getTypeFamily())
                || !CoreTypeFamily.MAP.equals(shape.getTypeFamily())
                || !CoreTypeFamily.TEXT.equals(shape.key().getTypeFamily())
                || !CoreTypeFamily.TEXT.equals(shape.value().getTypeFamily())
                || !CoreTypeFamily.TEXT.equals(type.getTypeFamily())) {
            return null;
        }

        IQLVisibilityContext visibility = renderer.getVisibilityContext();
        Source source = visibility.getSource(field.getAlias());
        if (source == null || source.getCall() != null) {
            return null;
        }
        Source leading = visibility.getLeadingSource(source.getName());
        String entity = leading != null ? leading.getName() : source.getName();
        LinkResolver resolver = renderer.getResolver();
        String column = resolver.getDialectColumn(entity, field.getName()).orElse(field.getName());
        Optional<Table> table = table(resolver, entity);
        if (table.isEmpty() || table.get().getIndexes() == null) {
            return null;
        }

        SqlDialect dialect = renderer.getDialect();
        boolean answers = false;
        for (Index index : table.get().indexesOn(column)) {
            if (!index.coversAllRows()) {
                continue;
            }
            for (IndexKey k : index.getKeys()) {
                if (!column.equals(k.getColumn())) {
                    continue;
                }
                if (k.hasPath()) {
                    if (k.getPath().equals(List.of(selector.key()))) {
                        return null; // the key has an index of its own: keep the comparison
                    }
                } else if (dialect.servesJsonContainment(index)) {
                    answers = true;
                }
            }
        }
        if (!answers) {
            return null;
        }
        return new Candidate(
                column, renderer.toSql(selection.getBase(), indent), selector.key(), value);
    }

    /** The text of a literal that is nothing but a text; null for every other expression. */
    private static String textLiteral(Expression e) {
        if (e.getText() == null
                || e.getField() != null
                || e.getFunction() != null
                || e.getSelect() != null
                || e.getSelection() != null
                || e.getNumber() != null
                || e.getLocalDate() != null
                || e.getLocalDateTime() != null
                || e.getLocalTime() != null
                || e.getDuration() != null
                || e.getLogical() != null
                || e.getLambda() != null
                || e.getParameter() != null
                || e.getIdentity() != null
                || e.isNull()) {
            return null;
        }
        return Literals.value(e.getText());
    }

    private static Optional<Table> table(LinkResolver resolver, String entity) {
        Optional<Table> table =
                resolver.getDialectTable(entity).flatMap(n -> resolver.getSchema().getTable(n));
        return table.isPresent() ? table : resolver.getSchema().getTable(entity);
    }

    /** The containment of a single comparison, or null when it is not a candidate. */
    static String render(SqlSelectRenderer renderer, UnaryLogicalExpression u, int indent) {
        Candidate c = candidate(renderer, u, indent);
        if (c == null) {
            return null;
        }
        return renderer.getDialect().jsonContainment(c.columnSql(), Map.of(c.key(), c.value()));
    }

    /**
     * The conjuncts of an AND that several comparisons of the same column make together, as one
     * containment: {@code attrs@a = 'x' AND attrs@b = 'y'} asks the index once.
     *
     * @return for the position of the first comparison of a group its containment, and for the
     *     others the empty string, which means dropped; empty when nothing is merged
     */
    static Map<Integer, String> merge(
            SqlSelectRenderer renderer, List<LogicalExpression> conjuncts, int indent) {
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        Map<Integer, Candidate> candidates = new HashMap<>();
        for (int i = 0; i < conjuncts.size(); i++) {
            LogicalExpression c = conjuncts.get(i);
            if (!c.isValue()) {
                continue;
            }
            Candidate candidate = candidate(renderer, c.getUnaryRelationalExpression(), indent);
            if (candidate != null) {
                candidates.put(i, candidate);
                groups.computeIfAbsent(candidate.columnSql(), k -> new ArrayList<>()).add(i);
            }
        }
        Map<Integer, String> merged = new LinkedHashMap<>();
        for (List<Integer> group : groups.values()) {
            if (group.size() < 2) {
                continue;
            }
            Map<String, String> entries = new LinkedHashMap<>();
            boolean conflict = false;
            for (int i : group) {
                Candidate c = candidates.get(i);
                String before = entries.put(c.key(), c.value());
                // the same key twice with two values is a contradiction: it stays as written
                conflict |= before != null && !before.equals(c.value());
            }
            if (conflict) {
                continue;
            }
            String sql =
                    renderer.getDialect()
                            .jsonContainment(candidates.get(group.get(0)).columnSql(), entries);
            if (sql == null) {
                continue;
            }
            merged.put(group.get(0), sql);
            for (int i : group.subList(1, group.size())) {
                merged.put(i, "");
            }
        }
        return merged;
    }
}
