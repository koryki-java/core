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
package ai.koryki.catalog.types;

public enum CoreTypeFamily implements TypeFamily {
    BLOB,
    BOOLEAN,
    DATE,
    DECIMAL,
    FLOAT,
    INTEGER,
    TIME,
    INTERVAL,
    TIMESTAMP,
    TEXT,
    JSON,
    UUID,
    /** A list or fixed-size array; the element type is a child of the {@link TypeDescriptor}. */
    ARRAY,
    /** Named fields of any type; the field types are children of the {@link TypeDescriptor}. */
    STRUCT,
    /** Keys to values; key and value are children of the {@link TypeDescriptor}. */
    MAP;
}
