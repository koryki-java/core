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
package ai.koryki.iql.logic;

import ai.koryki.iql.query.LogicalExpression;

import java.util.ArrayList;
import java.util.List;

/**
 * Flattens a logical tree into the shape the renderers expect: nested same-operator
 * AND/OR nodes collapse into one n-ary node, double negations cancel, and NOT is pushed
 * down through AND/OR by De Morgan's laws — which hold in SQL's three-valued logic, so
 * this is semantics-preserving for NULL operands too.
 *
 * <p>The De Morgan branch is not reachable from parsed KQL or IQL: {@code NOT} binds
 * tighter than {@code AND}/{@code OR}, so negating a connective requires parentheses, and
 * {@code ( logical_expression )} maps to a {@link ai.koryki.iql.query.UnaryLogicalExpression}
 * node — a {@code VAR} at this level. Normalization therefore only ever sees
 * {@code NOT(VAR)}. The branch exists for models assembled programmatically. Making it
 * reachable by unwrapping parenthesized VARs would drop the author's grouping from the
 * generated SQL and break the byte-identical IQL round-trip, so it is deliberately left
 * as-is.
 */
public class Normalizer {
    public static LogicalExpression normalize(LogicalExpression node) {

        if (node == null) {
            return null;
        }

        switch (node.getType()) {
            case AND:
            case OR: {
                List<LogicalExpression> flat = new ArrayList<>();
                for (LogicalExpression child : node.getChildren()) {
                    LogicalExpression normChild = normalize(child);
                    if (normChild.getType() == node.getType()) {
                        flat.addAll(normChild.getChildren()); // flatten nested AND/OR
                    } else {
                        flat.add(normChild);
                    }
                }
                return  LogicalExpression.andor(node.getType(), flat);
            }

            case NOT: {
                LogicalExpression child = normalize(node.getChildren().get(0));
                if (child.getType() == NodeType.NOT) {
                    return normalize(child.getChildren().get(0)); // remove double negation
                } else if (child.getType() == NodeType.AND || child.getType() == NodeType.OR) {
                    NodeType newType = (child.getType() == NodeType.AND) ? NodeType.OR : NodeType.AND;
                    List<LogicalExpression> newChildren = new ArrayList<>();
                    for (LogicalExpression grandChild : child.getChildren()) {
                        newChildren.add(normalize(LogicalExpression.not(grandChild)));
                    }
                    return LogicalExpression.andor(newType, newChildren);
                } else {
                    return LogicalExpression.not(child);
                }
            }

            case VAR: {
                return node;
            }

            default : throw new IllegalArgumentException("Unknown node type: " + node.getType());
        }
    }
}
