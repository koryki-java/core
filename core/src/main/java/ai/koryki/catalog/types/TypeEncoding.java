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

public interface TypeEncoding {

    String name();

    /**
     * The single logical {@link TypeFamily} whose physical storage this encoding
     * describes — e.g. {@code TIME_FROM_STRING} is a {@code TIME}, {@code SCALED}
     * a {@code DECIMAL}. Every encoding binds to exactly one family (one-to-many:
     * a family has many encodings, each encoding one family), which lets the
     * {@code (family, encoding)} pair be validated or the family be derived.
     */
    TypeFamily family();
}
