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
package ai.koryki.iql.validate;

import ai.koryki.iql.Collector;
import ai.koryki.iql.IQLVisibilityContext;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.Walker;
import ai.koryki.iql.query.Query;
import ai.koryki.iql.query.Source;
import ai.koryki.iql.functions.FunctionCatalog;
import org.antlr.v4.runtime.RuleContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Validator {

    private LinkResolver resolver;
    private Map<String, Source> blockIdToLeadingTableMap;
    private Map<Object, RuleContext> iqlToContext;
    private Query bean;
    private List<Violation> violations;
    private final FunctionCatalog functions;
    private final IQLVisibilityContext visibility;
    private final SqlDialect dialect;

    public Validator(Query bean, LinkResolver resolver, Map<String, Source> blockIdToLeadingTableMap, Map<Object, RuleContext> iqlToContext) {
        this(bean, resolver, blockIdToLeadingTableMap, iqlToContext, null, null);
    }

    /** @param functions dialect function catalog for arity/unsupported checks; null = skip those checks */
    public Validator(Query bean, LinkResolver resolver, Map<String, Source> blockIdToLeadingTableMap, Map<Object, RuleContext> iqlToContext,
                     FunctionCatalog functions) {
        this(bean, resolver, blockIdToLeadingTableMap, iqlToContext, functions, null);
    }

    /** @param visibility root scope for typing operator operands; null = skip operand-family checks */
    public Validator(Query bean, LinkResolver resolver, Map<String, Source> blockIdToLeadingTableMap, Map<Object, RuleContext> iqlToContext,
                     FunctionCatalog functions, IQLVisibilityContext visibility) {
        this(bean, resolver, blockIdToLeadingTableMap, iqlToContext, functions, visibility, null);
    }

    /** @param dialect contributes validators for constructs it cannot express; null = none */
    public Validator(Query bean, LinkResolver resolver, Map<String, Source> blockIdToLeadingTableMap, Map<Object, RuleContext> iqlToContext,
                     FunctionCatalog functions, IQLVisibilityContext visibility, SqlDialect dialect) {

        this.dialect = dialect;
        this.bean = bean;
        this.resolver = resolver;
        this.blockIdToLeadingTableMap = blockIdToLeadingTableMap;
        this.iqlToContext = iqlToContext;
        this.functions = functions;
        this.visibility = visibility;
    }

    public List<Violation> validate() {

        if (violations == null) {
            violations = new ArrayList<>();
            violations.addAll(Walker.apply(bean, new FunctionValidator(iqlToContext, functions, resolver, visibility)));
            violations.addAll(Walker.apply(bean, new SchemaValidator(resolver, blockIdToLeadingTableMap, iqlToContext, visibility)));
            violations.addAll(Walker.apply(bean, new DurationValidator(iqlToContext)));

            // The dialect last: what it cannot express is only interesting once the query itself
            // is in order.
            if (dialect != null) {
                ValidationContext context = new ValidationContext(iqlToContext, resolver, visibility, functions);
                // Derived from the declaration rather than wired per dialect: the dialect states
                // what it can do in intervalSupport() and contributes nothing else here.
                violations.addAll(Walker.apply(bean, new DurationValueValidator(context, dialect)));
                for (Collector<List<Violation>> v : dialect.validators(context)) {
                    violations.addAll(Walker.apply(bean, v));
                }
            }
        }
        return violations;
    }
}
