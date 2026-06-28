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

/**
 * Fluent assembly of an {@link IQLTranspiler} from the IQL source and its model
 * {@link LinkResolver} (which already carries the catalog locale), plus the
 * optional validation {@link FunctionRenderer}. The IQL counterpart of
 * {@code KQLTranspilerBuilder} — the single, extensible place to wire transpiler
 * components instead of adding further constructors.
 *
 * <p>Obtain one via {@link IQLTranspiler#builder(String, LinkResolver)} /
 * {@link IQLTranspiler#builder(java.io.InputStream, LinkResolver)}.
 */
public final class IQLTranspilerBuilder {

    private final String iql;
    private final LinkResolver resolver;
    private FunctionRenderer functions;

    IQLTranspilerBuilder(String iql, LinkResolver resolver) {
        this.iql = iql;
        this.resolver = resolver;
    }

    /**
     * Dialect function renderer for arity / operand-family / unsupported
     * validation; null (the default) skips function-level validation.
     */
    public IQLTranspilerBuilder functions(FunctionRenderer functions) {
        this.functions = functions;
        return this;
    }

    public IQLTranspiler build() {
        return new IQLTranspiler(iql, resolver, functions);
    }
}
