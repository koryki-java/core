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
 * Documentation category of a function, mirroring the chapters of the
 * PostgreSQL function reference (the catalog's coverage benchmark).
 * Drives the grouping of generated markdown docs; has no runtime semantics.
 */
public enum FunctionCategory {
    MATH("Mathematical Functions"),
    STRING("String Functions"),
    PATTERN_MATCHING("Pattern Matching"),
    FORMATTING("Data Type Formatting Functions"),
    DATETIME("Date/Time Functions"),
    CONDITIONAL("Conditional Expressions"),
    AGGREGATE("Aggregate Functions"),
    WINDOW("Window Functions"),
    CONVERSION("Type Conversion"),
    ARITHMETIC("Arithmetic Operators"),
    COMPARISON("Comparison Operators"),
    LOGICAL("Logical Operators"),
    OTHER("Other Functions");

    private final String title;

    FunctionCategory(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
