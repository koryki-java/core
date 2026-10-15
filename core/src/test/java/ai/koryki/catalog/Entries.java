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

import ai.koryki.catalog.schema.Entry;
import java.util.LinkedHashMap;
import java.util.Map;

/** The entries of a concept in a test, from names and spellings: an ordered map of references. */
public final class Entries {

    private Entries() {}

    public static Map<String, Entry> of(Map<String, String> references) {
        Map<String, Entry> entries = new LinkedHashMap<>();
        references.forEach(
                (key, value) -> entries.put(key, value == null ? null : Entry.of(value)));
        return entries;
    }
}
