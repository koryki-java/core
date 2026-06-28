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

import ai.koryki.catalog.schema.types.TypeDescriptor;
import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;

import java.util.ArrayList;
import java.util.List;

/**
 * Searched CASE: {@code case(cond1, result1, cond2, result2, ..., [else])} — alternating
 * predicate/result pairs with an optional trailing else, rendered as
 * {@code CASE WHEN cond1 THEN result1 WHEN cond2 THEN result2 [ELSE else] END}.
 *
 * <p>Conditions are at even indices (each paired with the result at {@code i+1}); a trailing odd
 * argument is the else. The result/else branches are the value branches: they reconcile to one output
 * type ({@link ConditionalReconciler}) and each renders wrapped in its reconciliation conversion. The
 * conditions render in predicate position (a {@code WHEN}), so a {@code logical_expression} argument
 * is portable there.
 */
public class CaseFunctionDefinition extends FunctionDefinition implements BranchedConditional {

    public CaseFunctionDefinition() {
        super("case", binding -> ConditionalReconciler.reconcile(valueBranchTypes(binding)).target());
    }

    /** True if argument {@code i} is a WHEN condition (an even index paired with a result at {@code i+1}). */
    public static boolean isCondition(int i, int argCount) {
        return i % 2 == 0 && i + 1 < argCount;
    }

    /** Indices of the result/else branch arguments, in CASE order (result1, result2, ..., else). */
    public static List<Integer> valueBranchIndices(int argCount) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 1; i < argCount; i += 2) {
            indices.add(i);                        // result of each WHEN
        }
        if (argCount % 2 == 1) {
            indices.add(argCount - 1);             // trailing ELSE
        }
        return indices;
    }

    @Override
    public List<Integer> branchIndices(int argCount) {
        return valueBranchIndices(argCount);
    }

    private static List<TypeDescriptor> valueBranchTypes(FunctionBinding binding) {
        List<TypeDescriptor> types = new ArrayList<>();
        for (int i : valueBranchIndices(binding.getOperandCount())) {
            types.add(binding.getOperandType(i));
        }
        return types;
    }

    @Override
    public String render(SqlSelectRenderer renderer, Function function, int indent) {
        if (isUnsupported()) {
            throw new UnsupportedOperationException("function 'case' is not supported by this dialect");
        }
        checkArity(function);

        List<Expression> args = function.getArguments();
        int n = args.size();

        List<TypeDescriptor> branches = new ArrayList<>();
        for (int i : branchIndices(n)) {
            branches.add(renderer.resolveType(args.get(i)));
        }
        ConditionalReconciler.Result result = ConditionalReconciler.reconcile(branches);

        StringBuilder b = new StringBuilder("CASE");
        int branch = 0;
        for (int i = 0; i + 1 < n; i += 2) {
            b.append(" WHEN ").append(renderer.toSql(args.get(i), indent))
                    .append(" THEN ").append(result.convert(branch++, renderer.toSql(args.get(i + 1), indent)));
        }
        if (n % 2 == 1) {
            b.append(" ELSE ").append(result.convert(branch++, renderer.toSql(args.get(n - 1), indent)));
        }
        b.append(" END");
        return b.toString();
    }
}
