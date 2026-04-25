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
import org.antlr.v4.runtime.RuleContext;

import java.util.Map;

public class JdbcSelectRenderer extends SqlSelectRenderer {

    public JdbcSelectRenderer(Identifier identifier, Map<Object, RuleContext> iqlToContext, LinkResolver resolver,
                              IQLVisibilityContext visibilityContext,
                              FunctionRenderer functionRenderer) {
        super(identifier, iqlToContext, resolver, visibilityContext,

                functionRenderer);
    }

    @Override
    protected SqlSelectRenderer subSelect(Map<Object, RuleContext> iqlToContext, Object child) {

        JdbcSelectRenderer s2s = new JdbcSelectRenderer(
                getIdentifier(),
                iqlToContext,
                resolver,
                visibilityContext.child(child),
                getFunctionTranslator());
        return s2s;
    }

    @Override
    protected String timeExpression(Expression expression) {
        return "{t '" + expression.getLocalTime() + "'}";
    }

    @Override
    protected String timestampExpression(Expression expression) {
        return "{ts '" + expression.getLocalDateTime() + "'}";
    }

    @Override
    protected String dateExpression(Expression expression) {

        return "{d '" + expression.getLocalDate() + "'}";
    }
}
