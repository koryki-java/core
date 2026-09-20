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
package ai.koryki.jdbc;

import java.util.List;

public interface ResultConsumer<C extends ColumnInfo> extends AutoCloseable {

    default void setInfos(List<C> infos) {}

    default List<C> getInfos() {
        return List.of();
    }

    /**
     * One {@link ValueFormat} for the whole result set (replaces per-column {@code
     * ColumnInfo.toString}). Concrete processors store it; the default is a no-op, so a processor
     * without one keeps the legacy {@code ColumnInfo.toString(value)} path in {@code formatRow}.
     */
    default void setValueFormat(ValueFormat format) {}

    default ValueFormat getValueFormat() {
        return null;
    }

    default void setSql(String sql) {
        // empty
    }

    @Override
    void close() throws RuntimeException;
}
