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

import ai.koryki.catalog.MemberNames;
import ai.koryki.catalog.types.TypeDescriptor;

/**
 * What the rows of a list made into rows are: the type of an element, and what the model says of
 * the parts of it. Found when the query is analysed, from the type of the list a source's call is
 * given, and read by whoever needs a field of the alias: it is not part of the query and not
 * written to IQL.
 *
 * @param element the type of an element of the list: a struct whose fields are the columns of the
 *     alias, or any other type, which is the one column {@code value}
 * @param level the words of the model for the parts of the element, or null where it says nothing
 */
public record RowType(TypeDescriptor element, MemberNames.Level level) {}
