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

import java.util.ArrayList;
import java.util.List;

public class UnaryLogicalExpression {

    private Expression left;
    private String op;
    private List<Expression> right = new ArrayList<>();

    private String parent;
    private Exists exists;
    private LogicalExpression node;
    private String placeholder;


    public Expression getLeft() {
        return left;
    }

    public void setLeft(Expression left) {
        this.left = left;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public List<Expression> getRight() {
        return right;
    }

    public void setRight(List<Expression> right) {
        this.right = right;
    }

    public String getParent() {
        return parent;
    }

    public void setParent(String parent) {
        this.parent = parent;
    }

    public Exists getExists() {
        return exists;
    }

    public void setExists(Exists exists) {
        this.exists = exists;
    }

    public LogicalExpression getNode() {
        return node;
    }

    public void setNode(LogicalExpression node) {
        this.node = node;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }
}
