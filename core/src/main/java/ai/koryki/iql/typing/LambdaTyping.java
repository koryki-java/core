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
package ai.koryki.iql.typing;

import ai.koryki.catalog.types.TypeDescriptor;
import java.util.List;

/**
 * What the function a lambda is passed to makes of it: the type of each parameter, and the type of
 * the body with those parameters in scope. Returned by {@link ExpressionTypeResolver#bindLambdas};
 * null where a lambda could not be typed.
 *
 * @param parameterTypes one per parameter the lambda declares
 * @param bodyType what the body returns, null if it cannot be typed
 */
public record LambdaTyping(List<TypeDescriptor> parameterTypes, TypeDescriptor bodyType) {}
