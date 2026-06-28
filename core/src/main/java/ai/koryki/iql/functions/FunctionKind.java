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

/**
 * Intrinsic classification of a function — the single source of truth for "is this an aggregate".
 *
 * <p>Note: there is deliberately no WINDOW value. Whether a call is a window function is a property
 * of the <em>call</em> (does it have an OVER clause — {@code Function.getWindow() != null}), not of
 * the function name. A dedicated value would only make sense for window-<em>only</em> functions
 * (row_number, rank, lead, lag, …), which aren't modeled yet.
 */
public enum FunctionKind {
    SCALAR,
    AGGREGATE
}
