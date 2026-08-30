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
import ai.koryki.iql.typing.ExpressionTypeResolver;
import org.antlr.v4.runtime.RuleContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface SqlRenderer {

    /**
     * The line break in generated SQL — a fixed {@code \n}, not the platform's.
     *
     * <p>{@code System.lineSeparator()} would have an unpleasant property here: the same model and
     * the same query would yield different SQL depending on the operating system. Every SQL golden
     * would then depend on the machine that produced it — compared on Windows, all of them would
     * differ without anything having changed in substance.
     *
     * <p>Generated SQL is a data format, not console text. It goes to a database driver and into
     * golden files, and neither cares about the platform.
     *
     * <p>The SQL-side name for {@link ai.koryki.catalog.Util#NL}; the value lives there because the
     * CSV output needs it too and {@code jdbc} must not point at {@code iql}.
     */
    String NL = ai.koryki.antlr.Text.NL;

    /**
     * A render's SQL text together with its resolved output schema, returned as one value so
     * the schema can never be read before — or out of sync with — the {@code toSql} that
     * produced it. (It previously lived in a field set by {@code toSql} and read by a separate
     * {@code outputSchema()} call.) Outputs are empty for renderers that don't resolve them.
     */
    record Rendered(String sql, List<OutputColumn> outputs) {}

    Rendered toSql(LinkResolver resolver, IQLVisibilityContext visibilityContext, Query query, Map<Object, RuleContext> iqlToContext);

     FunctionRenderer getFunctionRenderer();

    /**
     * The dialect this renderer emits for, or {@code null} if it has none (renderers that are not
     * bound to a target). The analysis stage asks for it to run the dialect's own validators, so a
     * construct the target cannot express is rejected before the SQL is ever sent.
     */
    default SqlDialect getDialect() {
        return null;
    }

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
