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
package ai.koryki.kql;

import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.functions.FunctionCatalog;

/**
 * Fluent assembly of a {@link KQLTranspiler} from the KQL source and its model
 * {@link LinkResolver} (which already carries the catalog locale), plus the
 * optional validation {@link FunctionCatalog}.
 *
 * <p>This is the single place to wire transpiler components: a
 * {@link ai.koryki.iql.functions.FunctionRenderer} is itself a
 * {@code FunctionCatalog}, so {@link #functions} also covers the dialect's
 * renderer. New components (e.g. a bound dialect renderer, or building the
 * resolver from a catalog + locale) are added here as further fluent methods,
 * rather than as yet another {@code KQLTranspiler} constructor.
 *
 * <p>Obtain one via {@link KQLTranspiler#builder(String, LinkResolver)} /
 * {@link KQLTranspiler#builder(java.io.InputStream, LinkResolver)}.
 */
public final class KQLTranspilerBuilder {

    private final String kql;
    private final LinkResolver resolver;
    private FunctionCatalog functions;

    KQLTranspilerBuilder(String kql, LinkResolver resolver) {
        this.kql = kql;
        this.resolver = resolver;
    }

    /**
     * Function catalog for arity / operand-family / unsupported validation; a
     * {@code FunctionRenderer} is also a {@code FunctionCatalog}. Null (the
     * default) skips function-level validation.
     */
    public KQLTranspilerBuilder functions(FunctionCatalog functions) {
        this.functions = functions;
        return this;
    }

    public KQLTranspiler build() {
        return new KQLTranspiler(kql, resolver, functions);
    }
}
