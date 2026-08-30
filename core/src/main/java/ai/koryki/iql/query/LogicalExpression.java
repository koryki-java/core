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
package ai.koryki.iql.query;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.iql.logic.NodeType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class LogicalExpression {

    private NodeType type;
    private List<LogicalExpression> children;
    private UnaryLogicalExpression unary; // only for VAR nodes

    public static LogicalExpression value(UnaryLogicalExpression unary) {
        return new LogicalExpression(unary);
    }

    public static LogicalExpression not(LogicalExpression child) {
        return new LogicalExpression(NodeType.NOT, child);
    }

    public static LogicalExpression and(LogicalExpression left, LogicalExpression right) {

        if (right == null) {
            return left;
        }
        return new LogicalExpression(NodeType.AND, new ArrayList<>(Arrays.asList(left, right)));
    }

    public static LogicalExpression andor(NodeType type, List<LogicalExpression> children) {
        if (!(type.equals(NodeType.AND) || type.equals(NodeType.OR))) {
            throw new KorykiaiException("invalid type " + type.name());
        }
        return new LogicalExpression(type, children);
    }

    public static LogicalExpression and(List<LogicalExpression> children) {
        return new LogicalExpression(NodeType.AND, children);
    }

    public static LogicalExpression or(LogicalExpression left, LogicalExpression right) {
        return new LogicalExpression(NodeType.OR, new ArrayList<>(Arrays.asList(left, right)));
    }

    public static LogicalExpression or(List<LogicalExpression> children) {
        return new LogicalExpression(NodeType.OR, children);
    }

    // Constructor for VAR node
    private LogicalExpression(UnaryLogicalExpression unary) {
        this.type = NodeType.VAR;
        this.unary = unary;
        this.children = Collections.emptyList();
    }

    // Constructor for NOT node
    private LogicalExpression(NodeType type, LogicalExpression child) {
        this.type = type;
        this.children = new ArrayList<>(Arrays.asList(child));
    }

    // Constructor for AND/OR node
    private LogicalExpression(NodeType type, List<LogicalExpression> children) {
        this.type = type;
        this.children = children;
    }

    @Override
    public String toString() {
        if (type == NodeType.VAR) {
            return unary.toString();
        }
        if (type == NodeType.NOT){
            return "NOT(" + children.get(0) + ")";
        }
        String op = type.toString();
        return op + "(" + String.join(", ", children.stream().map(LogicalExpression::toString).collect(Collectors.toList())) + ")";
    }

    public void add(NodeType type, LogicalExpression child) {
        if (!this.type.equals(type)) {
            throw new IllegalArgumentException("invalid type, expected: " + this.type + " actual: " + type);
        }
        children.add(child);
    }

    public NodeType getType() {
        return type;
    }

    public List<LogicalExpression> getChildren() {
        return children;
    }

    public UnaryLogicalExpression getUnaryRelationalExpression() {
        return unary;
    }

    /**
     * The operator that will actually render. A normalized AND/OR carrying a single child
     * is a transparent wrapper — it emits no keyword — so every precedence decision
     * (whether a child needs grouping parens) has to look through it rather than at
     * {@link #getType()}.
     */
    public NodeType effectiveType() {
        LogicalExpression e = this;
        while (e.isBinary() && e.children.size() == 1) {
            e = e.children.get(0);
        }
        return e.type;
    }

    public boolean isBinary() {
        return type.isBinary();
    }
    public boolean isValue() {
        return type.isValue();
    }
    public boolean isNot() {
        return type.isNot();
    }
}
