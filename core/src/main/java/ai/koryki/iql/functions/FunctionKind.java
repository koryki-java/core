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
 * <p>Whether a call <em>is</em> windowed remains a property of the call — does it carry an OVER
 * clause ({@code Function.getWindow() != null}) — not of the function name: {@code sum} is an
 * aggregate that may or may not be windowed. {@link #WINDOW} says something narrower, and only
 * about the name: this function means nothing <em>without</em> an OVER clause.
 */
public enum FunctionKind {
    SCALAR,
    AGGREGATE,

    /**
     * Window-only: {@code row_number}, {@code rank}, {@code lag}, … These compute per row against a
     * frame and cannot be evaluated without one, so {@code FunctionValidator} requires the OVER
     * clause. They are <em>not</em> aggregates — they do not collapse rows, so they must never
     * trigger GROUP BY inference (see {@code FunctionValidator.isAggregate}).
     */
    WINDOW
}
