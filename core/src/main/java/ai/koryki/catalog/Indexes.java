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
package ai.koryki.catalog;

import ai.koryki.catalog.schema.Index;
import ai.koryki.catalog.schema.IndexKey;

/** What can be asked of an {@link Index} and its keys. */
public final class Indexes {

    private Indexes() {}

    /** True when the index covers all rows: it has no predicate. */
    public static boolean coversAllRows(Index index) {
        return index.getWhere() == null || index.getWhere().isBlank();
    }

    /** Whether the key is a value inside its column rather than the column itself. */
    public static boolean hasPath(IndexKey key) {
        return key.getPath() != null && !key.getPath().isEmpty();
    }
}
