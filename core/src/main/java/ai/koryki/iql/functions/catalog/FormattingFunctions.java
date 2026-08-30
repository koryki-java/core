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
package ai.koryki.iql.functions.catalog;

import ai.koryki.antlr.Text;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.Families;
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.ReturnTypeInference;
import ai.koryki.iql.functions.ReturnTypes;

import static ai.koryki.iql.functions.FunctionArg.arg;

/**
 * Data type formatting functions, mirroring the PostgreSQL "Data Type Formatting" chapter.
 *
 * <p><b>The datetime mask is KQL's own vocabulary, and it is translated.</b> A mask is written once
 * — {@code YYYY YY MM DD HH24 HH12 HH MI SS AM PM}, the PostgreSQL template patterns,
 * which PostgreSQL inherits from Oracle — and {@link ai.koryki.iql.functions.FormatMask} rewrites it
 * into each dialect's own token language: strftime {@code %}-codes for DuckDB and SQLite, DATE_FORMAT
 * codes for MariaDB and Trino, date-part expressions for SQL Server. PostgreSQL and Oracle need no
 * rewriting because the canonical form is already theirs.
 *
 * <p>This javadoc previously claimed the opposite — that masks pass through untranslated. That was
 * true before {@code FormatMask} existed and was never revisited; the wording reached users through
 * the generated {@code docs/functions/formatting.md}, where it told them to write dialect-native
 * codes that the transpiler does not expect.
 *
 * <p>{@code to_number} is the exception and does <em>not</em> share that vocabulary: its mask is a
 * numeric template ({@code 9 0 . , D G}), for which there is no translation table, and five of the
 * eight dialects declare the function unsupported outright.
 */
public final class FormattingFunctions {

    private FormattingFunctions() {
    }

    public static void register(FunctionRegistry r) {
        r.register(def("to_char", ReturnTypes.TEXT).args(
                        arg("value", Families.ANY, "the date or timestamp to format"),
                        arg("format", CoreTypeFamily.TEXT, "the format mask, written once in KQL's own vocabulary"))
                .doc("Formats a date or timestamp as text using the *format* mask.")
                .paragraph("The mask is written **once**, in KQL's vocabulary, and translated for each "
                        + "database — you do not write the target database's codes:"
                        + Text.NL
                        + Text.NL
                        + "| | |" + Text.NL
                        + "|---|---|" + Text.NL
                        + "| `YYYY` `YY` | year |" + Text.NL
                        + "| `MM` | month |" + Text.NL
                        + "| `DD` | day |" + Text.NL
                        + "| `HH24` `HH12` `HH` | hour, 24- or 12-clock (`HH` is the 12-clock) |" + Text.NL
                        + "| `MI` `SS` | minute, second |" + Text.NL
                        + "| `AM` `PM` | meridiem indicator |"
                        + Text.NL
                        + Text.NL
                        + "There are deliberately **no name tokens** for months or weekdays. Measured, they gave "
                        + "five different answers for the same day — `July`, `JULY` padded to nine "
                        + "characters, `JULI`, `Juli` — and an empty column on SQLite, whose "
                        + "strftime has no such code at all. A mask containing one is rejected rather "
                        + "than answered five ways; render the name in the application, which knows "
                        + "the language it writes in."
                        + Text.NL
                        + Text.NL
                        + "Text to keep verbatim goes in double quotes: `'YYYY \"week\" WW'`. Tokens are matched "
                        + "**exactly as written** — `YYYY`, not `yyyy` — and anything unrecognised is "
                        + "passed through untouched, so a mistyped token becomes literal text instead "
                        + "of raising an error."));
        r.register(def("to_number", ReturnTypes.DECIMAL).args(
                        arg("value", CoreTypeFamily.TEXT, "the text to parse into a number"),
                        arg("format", CoreTypeFamily.TEXT, "a numeric template such as `999D99`"))
                .doc("Parses a string into a number using a numeric *format* mask.")
                .paragraph("This is **not** the datetime vocabulary above and is not translated: the "
                        + "mask goes to the database as written. Only PostgreSQL, Oracle and Snowflake offer "
                        + "the function at all — DuckDB, MariaDB, SQL Server, SQLite and Trino declare "
                        + "it unsupported, so a query using it is not portable."));
    }

    private static FunctionDefinition def(String name, ReturnTypeInference type) {
        return new FunctionDefinition(name, type).category(FunctionCategory.FORMATTING);
    }
}
