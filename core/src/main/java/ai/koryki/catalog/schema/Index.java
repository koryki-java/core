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

import java.util.ArrayList;
import java.util.List;

/**
 * An access path of a table: what the database keeps besides the rows to find some of them without
 * reading all.
 *
 * <p>The catalog states <em>facts</em> -- which method, over which keys. What a method can serve
 * ({@code =}, a range, containment) differs by database and belongs to its dialect, so it is not
 * stored here. Nothing in the transpiler may depend on an index being there for the result to be
 * right: an index only decides between spellings of the same question, so a catalog that is out of
 * date makes a query slower and never wrong.
 *
 * <p>A table without {@code indexes} behaves exactly as before.
 */
public class Index {

    private String name;
    private String method;
    private String opclass;
    private Boolean unique;
    private List<IndexKey> keys = new ArrayList<>();
    private String where;

    public Index() {}

    public Index(String name, String method, List<IndexKey> keys) {
        this.name = name;
        this.method = method;
        this.keys = new ArrayList<>(keys);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** The access method as the database names it: {@code btree}, {@code gin}, {@code hash}, ... */
    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    /**
     * The operator class of the key, where it matters ({@code jsonb_path_ops}); null for the
     * default.
     */
    public String getOpclass() {
        return opclass;
    }

    public void setOpclass(String opclass) {
        this.opclass = opclass;
    }

    public Boolean getUnique() {
        return unique;
    }

    public void setUnique(Boolean unique) {
        this.unique = unique;
    }

    public List<IndexKey> getKeys() {
        return keys;
    }

    public void setKeys(List<IndexKey> keys) {
        this.keys = new ArrayList<>(keys);
    }

    /**
     * The predicate of a partial index, as the database prints it; null for an index over all rows.
     * A partial index serves only queries that imply it, so nothing is rewritten for one.
     */
    public String getWhere() {
        return where;
    }

    public void setWhere(String where) {
        this.where = where;
    }

    /** True when the index covers all rows. */
    public boolean coversAllRows() {
        return where == null || where.isBlank();
    }
}
