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


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ListResult<C extends ColumnInfo> implements ResultProcessor<C> {

    private List<C> infos;
    private ValueFormat format;
    private final List<List<Object>> rows = new ArrayList<>();

    public ListResult() {

    }

    @Override
    public boolean append(List<Object> row) {
        return rows.add(row);
    }

    @Override
    public void close() {

    }

    public List<List<Object>> getRows() {
        return rows;
    }

    public String toCSV() {
        StringBuilder b = new StringBuilder();

        if (getInfos() != null) {
            b.append(CSVFileResult.toCSV(formatHeader(getInfos(), getInfos())));
        }

        rows.forEach(l -> {

            b.append(CSVFileResult.toCSV(formatRow(l, getInfos())));
        });

        return b.toString();
    }

    public String toSortedCSV() {
        StringBuilder b = new StringBuilder();

        if (getInfos() != null) {
            b.append(CSVFileResult.toCSV(formatHeader(getInfos(), getInfos())));
        }

        List<String> rl = new ArrayList<>(rows.stream().map(r -> CSVFileResult.toCSV(formatRow(r, getInfos()))).toList());
        Collections.sort(rl);

        rl.forEach(b::append);

        return b.toString();
    }

    @Override
    public List<C> getInfos() {
        return infos;
    }

    @Override
    public void setInfos(List<C> infos) {
        this.infos = infos;
    }

    @Override
    public ValueFormat getValueFormat() {
        return format;
    }

    @Override
    public void setValueFormat(ValueFormat format) {
        this.format = format;
    }
}
