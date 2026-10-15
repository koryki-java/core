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

/**
 * What a language calls the parts of a nested value, for a message that shows a type to a person
 * who writes the names of a model: {@code STRUCT(strasse:TEXT,position:STRUCT(...))} and not the
 * keys of the schema.
 *
 * <p>The keys are those of the entries of the concept: the fields of a struct, and {@code element},
 * {@code key} and {@code value} for the parts of a list and of a map. {@link #PLAIN} calls every
 * part by its key.
 */
public interface PartNames {

    /** Every part by its key: what a type shows when no model is asked. */
    PartNames PLAIN =
            new PartNames() {
                @Override
                public String of(String key) {
                    return key;
                }

                @Override
                public PartNames part(String key) {
                    return this;
                }
            };

    /** The word for the field {@code key} of the struct at this level. */
    String of(String key);

    /** The names inside the part {@code key}. */
    PartNames part(String key);
}
