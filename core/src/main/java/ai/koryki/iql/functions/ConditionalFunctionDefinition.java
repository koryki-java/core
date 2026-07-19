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

import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The COALESCE-family conditional: <em>every</em> operand is a value branch, all reconciled to one
 * output type ({@link ConditionalReconciler}) and rendered wrapped in its reconciliation conversion.
 * Renders via the definition's template ({@code COALESCE({*})}) or the default {@code name(args)} form.
 */
public class ConditionalFunctionDefinition extends FunctionDefinition implements BranchedConditional {

    public ConditionalFunctionDefinition(String name) {
        super(name, ReturnTypes.RECONCILE);
    }

    @Override
    public List<Integer> branchIndices(int argCount) {
        return IntStream.range(0, argCount).boxed().toList();
    }

    @Override
    public String render(SqlSelectRenderer renderer, Function function, int indent) {
        if (isUnsupported()) {
            throw new UnsupportedOperationException("function '" + getName() + "' is not supported by this dialect");
        }
        checkArity(function);

        List<Expression> args = function.getArguments();
        List<TypeDescriptor> branches = new ArrayList<>(args.size());
        for (Expression arg : args) {
            branches.add(renderer.resolveType(arg));
        }
        ConditionalReconciler.Result result = ConditionalReconciler.reconcile(branches);

        List<String> rendered = new ArrayList<>(args.size());
        for (int i = 0; i < args.size(); i++) {
            rendered.add(result.convert(i, renderer.toSql(args.get(i), indent)));
        }

        return getTemplate() != null
                ? getTemplate().fill(rendered)
                : getName() + "(" + String.join(", ", rendered) + ")";
    }
}
