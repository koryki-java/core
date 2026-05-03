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

import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;

import java.util.stream.Collectors;

public interface FunctionRenderer {


    default String function(String function) {
        return function;
    }

    default String function(SqlSelectRenderer renderer, Function function, int indent) {

        StringBuilder b = new StringBuilder();

        switch (function.getFunc()) {
            case "to_decimal" : {
                return toDecimal(renderer, function, indent);
            }
            case "to_text" : {
                return cast(renderer, function, indent, "TEXT");
            }
            case "to_int" : {
                return cast(renderer, function, indent, "INTEGER");
            }
            case "list" : {
                return list(renderer, function, indent);
            }
        }

        b.append(function(function.getFunc()));
        b.append("(");
        b.append(function.getArguments().stream().map(
                a -> renderer.toSql(a, indent)).collect(Collectors.joining(", ")));
        b.append(")");
        return b.toString();
    }

    private static String toDecimal(SqlSelectRenderer renderer, Function function, int indent) {
        if (function.getArguments().isEmpty()) {
            throw new IllegalArgumentException("to_decimal function must have at least one argument");
        }

        Expression a = function.getArguments().get(0);
        if (function.getArguments().size() == 3) {
            return "CAST(" + renderer.toSql(a, indent) + " AS DECIMAL(" + renderer.toSql(function.getArguments().get(1), indent) + ", " +
                    renderer.toSql(function.getArguments().get(2), indent) + "))";
        } else {
            throw new IllegalArgumentException("to_decimal function must have three arguments");
        }
    }

    private static String cast(SqlSelectRenderer renderer, Function function, int indent, String type) {
        if (function.getArguments().isEmpty()) {
            throw new IllegalArgumentException(function.getFunc() + " must have at least one argument");
        }

        Expression a = function.getArguments().getFirst();
        if (function.getArguments().size() == 1) {
            return "CAST(" + renderer.toSql(a, indent) + " AS " + type + ")";
        } else {
            throw new IllegalArgumentException(function.getFunc() + " function must have one arguments");
        }
    }

    private static String list(SqlSelectRenderer renderer, Function function, int indent) {
        if (function.getArguments().isEmpty()) {
            throw new IllegalArgumentException(function.getFunc() + " must have at least one argument");
        }

        Expression a = function.getArguments().getFirst();
        if (function.getArguments().size() == 1) {
            return "[" + renderer.toSql(a, indent) + "]";
        } else {
            throw new IllegalArgumentException("list-constructor must have one arguments");
        }
    }

}
