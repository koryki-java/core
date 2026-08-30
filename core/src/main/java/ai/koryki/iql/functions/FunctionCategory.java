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
 *
 * <p>{@code order} is the docs-site nav position written into each page's
 * {@code order:} frontmatter, kept in step with the index in {@code docs/FUNCTIONS.md}. It is
 * declared rather than derived from {@link #ordinal()} so that adding or moving a constant cannot
 * silently renumber every published page. The per-dialect pages occupy 21–28
 * ({@code DocDialects.ALL}), so the two ranges do not collide.
 */
public enum FunctionCategory {
    LOGICAL("Logical Operators", 1),
    COMPARISON("Comparison Operators", 2),
    ARITHMETIC("Arithmetic Operators", 3),
    MATH("Mathematical Functions", 4),
    STRING("String Functions", 5),
    PATTERN_MATCHING("Pattern Matching", 6),
    FORMATTING("Data Type Formatting Functions", 7),
    DATETIME("Date/Time Functions", 8),
    CONDITIONAL("Conditional Expressions", 9),
    AGGREGATE("Aggregate Functions", 10),
    CONVERSION("Type Conversion", 11),
    WINDOW("Window Functions", 12),
    OTHER("Other Functions", 13);

    private final String title;
    private final int order;

    FunctionCategory(String title, int order) {
        this.title = title;
        this.order = order;
    }

    public String getTitle() {
        return title;
    }

    /** Position of this category's page in the docs-site nav. */
    public int getOrder() {
        return order;
    }
}
