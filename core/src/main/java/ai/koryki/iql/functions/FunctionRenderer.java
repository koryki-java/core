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

import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.query.Function;
import ai.koryki.iql.query.Limit;
import ai.koryki.iql.query.Order;
import ai.koryki.iql.query.Window;

import java.util.stream.Collectors;

/** The rendering role of the function catalog; {@link FunctionCatalog} is the type-facing role. */
public interface FunctionRenderer extends FunctionCatalog {

    // null = no predicate-specific rendering; caller falls back to function()
    default String predicate(SqlSelectRenderer renderer, Function function, int indent) {
        return null;
    }

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
        b.append(
        toSql(renderer, function.getWindow(), indent));
        return b.toString();
    }

    default String toSql(SqlSelectRenderer renderer, Window window, int indent) {
        if (window == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();

        b.append(" OVER (");

        if (!window.getPartition().isEmpty()) {
            b.append("PARTITION BY " + renderer.toSql(window.getPartition(), indent));
        }

        if (!window.getOrder().isEmpty()) {
            b.append(" ORDER BY ");
            b.append(renderer.toSql(window.getOrder(), indent));
            if (window.isOrderDesc() == Order.SORT.DESC) {
                b.append(" DESC");
            } else if (window.isOrderDesc() == Order.SORT.ASC) {
                b.append(" ASC");
            }
        }

        if (window.getLower() != null) {
            b.append(" ROWS BETWEEN ");
            b.append(toSql(window.getLower()));
            b.append(" AND ");
            b.append(toSql(window.getUpper()));
        }

        b.append(")");
        return b.toString();
    }

    default String toSql(Limit limit) {
        return (limit.getNum() > 0 ? limit.getNum() + " " : "") + limit.getName();
    }

}
