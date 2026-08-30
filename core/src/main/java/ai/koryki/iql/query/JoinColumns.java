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
package ai.koryki.iql.query;

import java.util.List;

/**
 * The columns an explicitly written join compares, as two ordered lists of equal length:
 * {@code left.get(i)} is matched against {@code right.get(i)}.
 *
 * <p>This is the alternative to a named criterion ({@code VIA name}), which the catalog resolves to
 * exactly such a pair of lists — {@code Relation.startColumns} and {@code endColumns}. A join
 * therefore carries either a criterion or these columns, never both, and everything downstream sees
 * the same shape either way.
 *
 * <p>KQL writes {@code [a, b]} when both sides use the same names and {@code [a=x, b=y]} otherwise.
 * The first is only a shorthand for the second, so both arrive here as two filled lists; keeping
 * the shorthand as a separate case would mean every consumer had to know about it.
 *
 * <p>The order is part of the meaning and is preserved: {@code [a, b]} and {@code [b, a]} pair the
 * columns differently as soon as the two sides are not symmetric.
 */
public record JoinColumns(List<String> left, List<String> right) {

    public JoinColumns {
        if (left == null || right == null) {
            throw new IllegalArgumentException("join columns: both sides are required");
        }
        if (left.size() != right.size()) {
            throw new IllegalArgumentException("join columns: " + left.size() + " on the left but "
                    + right.size() + " on the right — every column needs its counterpart");
        }
        if (left.isEmpty()) {
            throw new IllegalArgumentException("join columns: at least one column is required");
        }
        left = List.copyOf(left);
        right = List.copyOf(right);
    }

    /** Both sides written with the same names — the {@code [a, b]} shorthand. */
    public static JoinColumns shared(List<String> columns) {
        return new JoinColumns(columns, columns);
    }
}
