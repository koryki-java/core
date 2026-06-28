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

import ai.koryki.iql.functions.FunctionRenderer;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Out;
import ai.koryki.iql.query.Query;
import ai.koryki.iql.types.ExpressionTypeResolver;
import ai.koryki.catalog.schema.types.TypeDescriptor;
import org.antlr.v4.runtime.RuleContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface SqlRenderer {

    String toSql(LinkResolver resolver, IQLVisibilityContext visibilityContext, Query query, Map<Object, RuleContext> iqlToContext);

     FunctionRenderer getFunctionRenderer();

    /**
     * Resolved output schema of the most recent {@link #toSql} call — the
     * single source of truth shared with the read layer ({@code ColumnInfo}).
     * Empty for renderers that don't resolve outputs.
     */
     List<OutputColumn> outputSchema() ;

    /**
     * Resolves the type of every top-level output column once, with this
     * renderer's catalog and visibility scope. Exceptions propagate (a query
     * whose output type can't be resolved is not renderable).
     */
    default List<OutputColumn> resolveOutputs(LinkResolver resolver, IQLVisibilityContext visibility, Query query) {
        ExpressionTypeResolver types = new ExpressionTypeResolver(
                resolver, visibility.child(SqlQueryRenderer.select(query)), getFunctionRenderer());
        List<OutputColumn> columns = new ArrayList<>();
        for (Out o : SqlQueryRenderer.collectOut(query)) {
            Expression e = o.getExpression();
            columns.add(new OutputColumn(o, types.resolve(e)));
        }
        return columns;
    }
}
