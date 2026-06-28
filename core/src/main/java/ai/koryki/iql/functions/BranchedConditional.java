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

import java.util.List;

/**
 * A conditional function definition that owns which of its operands are the value/result branches —
 * the ones that must reconcile to a common output type ({@link ConditionalReconciler}). This is the
 * single source of truth shared by return-type inference, SQL rendering, and validation, so none of
 * them re-derive the branch layout.
 */
public interface BranchedConditional {

    /** Operand indices of the value/result branches for a call of {@code argCount} arity, in branch order. */
    List<Integer> branchIndices(int argCount);
}
