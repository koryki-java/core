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
 * Surface-syntax shape of a catalog entry. Functions are {@link #PREFIX}
 * (a call: {@code name(args)}); operators carry one of the infix/affix shapes.
 *
 * <p>This is the closed half of unifying functions and operators: new operators
 * are open data in the catalog, but they must pick one of these fixed rendering
 * shapes. New shapes are rare and the only thing that touches the renderer.
 */
public enum Fixity {

    /** {@code name(a, b)} — a function call (default). */
    PREFIX,

    /** {@code a OP b} — binary operator (e.g. {@code =}, {@code AND}). */
    INFIX,

    /** {@code a OP b AND c} — bounded range (e.g. {@code BETWEEN}). */
    RANGE,

    /** {@code a OP (b, c, …)} — membership over a set (e.g. {@code IN}). */
    SET,

    /** {@code a OP} — postfix unary (e.g. {@code IS NULL}). */
    POSTFIX,

    /** {@code OP a} — prefix unary (e.g. {@code NOT}, unary minus). */
    PREFIX_UNARY
}
