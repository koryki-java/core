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
package ai.koryki.tools.docs;

import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.iql.SqlDialect;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The table of what each dialect declares in {@link SqlDialect#nestedSupport()}, written into
 * {@code docs/NESTED.md} between two markers. It is generated so that it cannot drift from the
 * code; {@code NestedSupportMatrixTest} fails when the document is behind, and {@code ./gradlew
 * :tools:nestedMatrix} brings it up to date.
 */
public final class NestedSupportMatrix {

    private NestedSupportMatrix() {}

    public static final String BEGIN =
            "<!-- nested-support:begin (generated: ./gradlew :tools:nestedMatrix) -->";
    public static final String END = "<!-- nested-support:end -->";

    /** The table: one row per documented dialect, in the order of the docs. */
    public static String table() {
        StringBuilder b = new StringBuilder();
        b.append(
                "| | list | struct | map | JSON | JSON from the end | lambda over a JSON array |\n");
        b.append("|---|---|---|---|---|---|---|\n");
        for (DocDialects.Doc doc : DocDialects.all()) {
            SqlDialect.NestedSupport s = doc.dialect().nestedSupport();
            b.append("| ").append(doc.title());
            for (CoreTypeFamily family :
                    new CoreTypeFamily[] {
                        CoreTypeFamily.ARRAY,
                        CoreTypeFamily.STRUCT,
                        CoreTypeFamily.MAP,
                        CoreTypeFamily.JSON
                    }) {
                b.append(" | ").append(yes(s.selectable().contains(family)));
            }
            b.append(" | ")
                    .append(yes(s.selectable().contains(CoreTypeFamily.JSON) && s.jsonFromEnd()));
            b.append(" | ")
                    .append(
                            doc.dialect().getFunctionRenderer().jsonArraysAreLists()
                                    ? yes(s.lambdaOverJsonList())
                                    : "no list")
                    .append(" |\n");
        }
        return b.toString();
    }

    private static String yes(boolean value) {
        return value ? "yes" : "no";
    }

    /** {@code document} with the text between the markers replaced by the table. */
    public static String apply(String document) {
        int begin = document.indexOf(BEGIN);
        int end = document.indexOf(END);
        if (begin < 0 || end < begin) {
            throw new IllegalArgumentException("the document has no " + BEGIN + " ... " + END);
        }
        return document.substring(0, begin + BEGIN.length())
                + "\n\n"
                + table()
                + "\n"
                + document.substring(end);
    }

    /** {@code NestedSupportMatrix path/to/NESTED.md}: rewrites the table in the document. */
    public static void main(String[] args) throws IOException {
        Path file = Path.of(args.length > 0 ? args[0] : "docs/NESTED.md");
        String text = Files.readString(file);
        String updated = apply(text);
        if (!updated.equals(text)) {
            Files.writeString(file, updated);
            System.out.println("updated " + file);
        } else {
            System.out.println(file + " is current");
        }
    }
}
