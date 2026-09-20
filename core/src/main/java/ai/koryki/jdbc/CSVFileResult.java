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

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.Util;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class CSVFileResult<C extends ColumnInfo> implements ResultProcessor<C> {

    private File file;
    private PrintWriter writer;
    private List<C> infos;

    public CSVFileResult(File file) {
        this(file, StandardCharsets.UTF_8);
    }

    public CSVFileResult(File file, Charset cs) {
        this.file = file;

        try {
            writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), cs));
        } catch (FileNotFoundException e) {
            throw new KorykiaiException(e);
        }
    }

    @Override
    public boolean append(List<Object> row) {

        // print, not println: the line break comes from toCSV. println appended a second one —
        // every
        // data row was followed by a blank line. It would also be the platform's break, while toCSV
        // uses Text.NL, so one file would contain both forms.
        writer.print(toCSV(formatRow(row, getInfos())));
        return true;
    }

    public static String toCSV(List<String> row) {
        return row.stream()
                        .map(c -> c != null ? mask(c.toString()) : "")
                        .collect(Collectors.joining(", "))
                + Util.NL;
    }

    private static String mask(String text) {
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    @Override
    public void close() {
        if (writer != null) {
            writer.close();
        }
    }

    public File getFile() {
        return file;
    }

    public List<C> getInfos() {
        return infos;
    }

    @Override
    public void setInfos(List<C> infos) {
        this.infos = infos;
    }
}
