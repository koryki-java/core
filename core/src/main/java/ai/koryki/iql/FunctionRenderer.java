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

import ai.koryki.iql.query.Function;

import java.util.stream.Collectors;

public interface FunctionRenderer {


    default String function(String function) {
        return function;
    }

    default String function(SqlSelectRenderer renderer, Function function, int indent) {

        StringBuilder b = new StringBuilder();

        b.append(function(function.getFunc()));
        b.append("(");
        b.append(function.getArguments().stream().map(
                a -> renderer.toSql(a, indent)).collect(Collectors.joining(", ")));
        b.append(")");
        return b.toString();
    }

}
