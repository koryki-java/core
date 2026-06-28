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
package ai.koryki.iql.functions;

/** Arithmetic operators (named {@code MathOp} to avoid shadowing {@link java.lang.Math}). */
public enum MathOp {

    add("+"),
    minus("-"),
    multiply("*"),
    divide("/"),
    negate("-");

    private final String operator;

    MathOp(String operator) {
        this.operator = operator;
    }

    public String getOperator() {
        return operator;
    }

    public static String operator(String name) {
        if (add.name().equals(name)) {
            return add.getOperator();
        }
        if (minus.name().equals(name)) {
            return minus.getOperator();
        }
        if (multiply.name().equals(name)) {
            return multiply.getOperator();
        }
        if (divide.name().equals(name)) {
            return divide.getOperator();
        }
        return null;
    }
}
