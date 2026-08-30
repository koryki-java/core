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

import ai.koryki.iql.IQLVisibilityContext;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.functions.FunctionCatalog;
import org.antlr.v4.runtime.RuleContext;

import java.util.Map;

/**
 * What a validator needs to judge a query — handed to {@code SqlDialect#validators} so a dialect's
 * own checks stand on the same footing as the core ones.
 *
 * <p>Only {@code iqlToContext} used to be passed, which is enough to <em>position</em> a violation
 * but not to <em>find</em> one: any rule about a column's type could not be written at all. That is
 * why SQLite's inability to read a wall-clock(zone) column stayed a render-time
 * {@code KorykiaiException} with no position while the comparable function-level cases
 * ({@code at_zone}, {@code to_utc}) became declared violations.
 *
 * @param iqlToContext model node → parser context, so a violation can point at the source
 * @param resolver     schema and model, for resolving a field to its column and type
 * @param visibility   alias scopes, so a field resolves in the scope that binds it
 * @param functions    the dialect's catalog, which type inference consults for call results
 */
public record ValidationContext(Map<Object, RuleContext> iqlToContext,
                                LinkResolver resolver,
                                IQLVisibilityContext visibility,
                                FunctionCatalog functions) {
}
