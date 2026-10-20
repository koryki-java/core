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
package ai.koryki.catalog.schema;

import java.util.List;

/**
 * One key of an {@link Index}: a column, or one value inside it.
 *
 * <p>An index on {@code attrs} has a key with a column only. An index on {@code ((attrs ->>
 * 'color'))} has the same column and the path {@code ["color"]}: it serves a comparison of that key
 * and nothing else. The path is what the query says ({@code t.attrs@color}), not a piece of SQL, so
 * a query can be compared with the key without parsing anything.
 */
public class IndexKey {

    private String column;
    private List<String> path;
    private String as;

    public IndexKey() {}

    public IndexKey(String column, List<String> path, String as) {
        this.column = column;
        this.path = path;
        this.as = as;
    }

    /** The column, by its physical name. */
    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    /** The keys that lead from the column to the indexed value; null for the column itself. */
    public List<String> getPath() {
        return path;
    }

    public void setPath(List<String> path) {
        this.path = path;
    }

    /**
     * The type the indexed value is cast to, as the database names it ({@code TEXT}, {@code
     * INTEGER}); null when it is not cast. An index on the integer of {@code doc ->> 'n'} serves a
     * comparison of that integer, and not one of its text.
     */
    public String getAs() {
        return as;
    }

    public void setAs(String as) {
        this.as = as;
    }

    /**
     * True when the key is one value inside the column and not the column itself. Not an {@code
     * is...} getter, which a mapper would write out as a property of its own.
     */
    public boolean hasPath() {
        return path != null && !path.isEmpty();
    }
}
