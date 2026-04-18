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
