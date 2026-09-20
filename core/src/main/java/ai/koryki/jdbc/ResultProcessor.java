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

import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public interface ResultProcessor<C extends ColumnInfo> extends ResultConsumer<C> {

    default void metadata(ResultSetMetaData metaData) {}

    boolean append(List<Object> row);

    default <O> List<String> formatHeader(List<O> row, List<C> infos) {
        return formatRow(row, infos, null);
    }

    default <O> List<String> formatRow(List<O> row, List<C> infos) {
        return formatRow(row, infos, getValueFormat());
    }

    default <O> List<String> formatRow(List<O> row, List<C> infos, ValueFormat format) {

        if (infos != null && row.size() == infos.size()) {

            // Prefer the single result-set ValueFormat when set; fall back to the
            // legacy per-column ColumnInfo.toString when no ValueFormat is wired.
            BiFunction<Object, C, String> consumer =
                    format != null ? (o, i) -> format.formatColumn(o, i) : (o, i) -> i.toString(o);
            List<String> result = new ArrayList<>();
            for (int i = 0; i < row.size(); i++) {
                result.add(consumer.apply(row.get(i), infos.get(i)));
            }
            return result;
        } else {

            return row.stream()
                    .map(c -> c != null ? c.toString() : "")
                    .collect(Collectors.toList());
        }
    }
}
