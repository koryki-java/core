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

import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.antlr.Text;
import ai.koryki.catalog.types.Families;
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.ReturnTypeInference;
import ai.koryki.iql.functions.ReturnTypes;

import java.util.List;

import static ai.koryki.iql.functions.FunctionArg.arg;
import static ai.koryki.iql.functions.FunctionArg.optionalArg;

/** String functions, mirroring the PostgreSQL "String Functions" chapter. */
public final class StringFunctions {

    private static final CoreTypeFamily TEXT = CoreTypeFamily.TEXT;
    private static final CoreTypeFamily INT = CoreTypeFamily.INTEGER;

    private StringFunctions() {
    }

    public static void register(FunctionRegistry r) {
        // One name per function: character_length and length meant the same. char_length stays,
        // because with octet_length and bit_length it forms a family -- the same measurement in a
        // different unit.
        r.register(def("char_length", ReturnTypes.INTEGER).args(arg("string", TEXT, "the text to measure"))
                .doc("Number of characters in the string."));
        r.register(def("octet_length", ReturnTypes.INTEGER).args(arg("string", TEXT, "the text to measure"))
                .doc("Number of bytes in the string."));
        r.register(def("bit_length", ReturnTypes.INTEGER).args(arg("string", TEXT, "the text to measure"))
                .doc("Number of bits in the string."));

        // SQLite's upper/lower fold ASCII only — measured, upper('Ölsson-äöü') is 'ÖLSSON-äöü'
        // there and 'ÖLSSON-ÄÖÜ' everywhere else. Core SQLite has no Unicode casing (it needs the
        // ICU extension), so this is stated rather than fixed.
        String asciiOnly = "On SQLite this folds ASCII only, so accented letters are returned "
                + "unchanged; every other supported engine is Unicode-aware.";
        r.register(def("upper", ReturnTypes.TEXT).args(arg("string", TEXT, "the text to convert"))
                .doc("Converts the string to upper case.")
                .paragraph(asciiOnly));
        r.register(def("lower", ReturnTypes.TEXT).args(arg("string", TEXT, "the text to convert"))
                .doc("Converts the string to lower case.")
                .paragraph(asciiOnly));
        r.register(def("initcap", ReturnTypes.TEXT).args(arg("string", TEXT, "the text to capitalize"))
                .doc("Upper-cases the first letter of each word **and lower-cases the rest** — "
                        + "`initcap('hELLO wORLD')` is `Hello World`, not `hEllo wOrld`. A word is a run "
                        + "of letters and digits; anything else separates two words."));

        r.register(def("trim", ReturnTypes.TEXT)
                .args(arg("string", TEXT, "the text to trim"),
                      optionalArg("characters", TEXT, "the characters to strip; spaces if omitted"))
                .doc("Removes *characters* (default spaces) from both ends.")
                .paragraph("*characters* is a **set of characters**, not a substring: `trim('abhelloba', "
                        + "'ab')` gives `hello`, because it strips any `a` and any `b` from either end "
                        + "until something else appears."
                        + Text.NL
                        + Text.NL
                        + "**MariaDB has no set-based trim** and cannot express this form — a call with "
                        + "*characters* is rejected there rather than answered wrongly. Its `TRIM(BOTH x "
                        + "FROM s)` removes a whole substring, which is a different question: it would "
                        + "leave `helloba`. Without *characters* the function works everywhere."));
        r.register(def("ltrim", ReturnTypes.TEXT)
                .args(arg("string", TEXT, "the text to trim"),
                      optionalArg("characters", TEXT, "the characters to strip; spaces if omitted"))
                .doc("Removes *characters* (default spaces) from the start."));
        r.register(def("rtrim", ReturnTypes.TEXT)
                .args(arg("string", TEXT, "the text to trim"),
                      optionalArg("characters", TEXT, "the characters to strip; spaces if omitted"))
                .doc("Removes *characters* (default spaces) from the end."));

        // substr was the second name of the same function; PostgreSQL lists it as "Same as
        // substring(...)". substring stays.
        {
            r.register(def("substring", ReturnTypes.TEXT)
                    .args(arg("string", TEXT, "the source text"),
                          arg("start", INT, "1-based index of the first character to keep"),
                          optionalArg("length", INT, "number of characters to take; to the end of the string if omitted"))
                    .doc("Extracts the substring starting at *start* (1-based), optionally limited to "
                        + "*length* characters. Without *length* it runs to the end of the string.")
                .paragraph("*start* counts from **1**. A value below that is outside the definition and "
                        + "the databases disagree on it — measured, `substring('alphabet', 0, 3)` gives "
                        + "`al` on most, nothing on MariaDB and Trino, and `alp` on Snowflake. Start at "
                        + "1 or later, or clamp the value before passing it."));
        }
        r.register(def("left", ReturnTypes.TEXT)
                .args(arg("string", TEXT, "the source text"),
                      arg("n", INT, "number of leading characters to keep"))
                .doc("First *n* characters."));
        r.register(def("right", ReturnTypes.TEXT)
                .args(arg("string", TEXT, "the source text"),
                      arg("n", INT, "number of trailing characters to keep"))
                .doc("Last *n* characters — the whole string if *n* reaches or exceeds its length, "
                        + "and nothing at all if *n* is zero.")
                .paragraph("Those two edges are worth stating because three dialects express `right` "
                        + "through a negative substring offset, where they used to fall out wrong: "
                        + "`right(s, 0)` returned the **whole** string on Oracle and SQLite, and "
                        + "`right(s, 99)` returned nothing on Oracle and Trino. Both are levelled now."));
        r.register(def("reverse", ReturnTypes.TEXT).args(arg("string", TEXT, "the text to reverse"))
                .doc("Reverses the string."));
        r.register(def("repeat", ReturnTypes.TEXT)
                .args(arg("string", TEXT, "the text to repeat"),
                      arg("number", INT, "how many times to repeat the text"))
                .doc("Repeats the string *number* times."));

        r.register(def("lpad", ReturnTypes.TEXT)
                .args(arg("string", TEXT, "the text to pad"),
                      arg("length", INT, "target total length after padding"),
                      optionalArg("fill", TEXT, "the padding text; a space if omitted"))
                .doc("Pads the string on the left to *length* using *fill* (default space)."));
        r.register(def("rpad", ReturnTypes.TEXT)
                .args(arg("string", TEXT, "the text to pad"),
                      arg("length", INT, "target total length after padding"),
                      optionalArg("fill", TEXT, "the padding text; a space if omitted"))
                .doc("Pads the string on the right to *length* using *fill* (default space)."));

        r.register(def("concat", ReturnTypes.TEXT).variadic(arg("value", Families.ANY, "the values to concatenate"))
                .doc("Concatenates the text form of all arguments; null arguments are ignored."));
        r.register(def("concat_ws", ReturnTypes.TEXT).variadic(arg("separator", TEXT, "the separator placed between the joined values"))
                .doc("Concatenates all arguments after the first, separated by *separator*."));

        r.register(def("replace", ReturnTypes.TEXT)
                .args(arg("string", TEXT, "the source text"),
                      arg("from", TEXT, "the substring to search for"),
                      arg("to", TEXT, "the replacement substring"))
                .doc("Replaces all occurrences of *from* with *to*."));
        r.register(def("translate", ReturnTypes.TEXT)
                .args(arg("string", TEXT, "the source text"),
                      arg("from", TEXT, "the characters to replace"),
                      arg("to", TEXT, "the matching replacement characters"))
                .doc("Replaces each character in *from* with the corresponding character in *to*."));
        r.register(def("overlay", ReturnTypes.TEXT)
                .args(arg("string", TEXT, "the source text"),
                      arg("replacement", TEXT, "the text to insert"),
                      arg("start", INT, "1-based index where replacement begins"),
                      optionalArg("length", INT, "number of characters to overwrite; the replacement's length if omitted"))
                .doc("Writes *replacement* over *string*, beginning at *start* and covering *length* "
                        + "characters — the rest of the text stays where it is. Without *length* it "
                        + "covers as many characters as *replacement* is long, so the string keeps its "
                        + "size; a larger *length* shortens it, a smaller one lengthens it."));

        r.register(def("starts_with", ReturnTypes.BOOLEAN)
                .args(arg("string", TEXT, "the text to test"),
                      arg("prefix", TEXT, "the prefix to look for"))
                .doc("True if the string begins with *prefix*."));
        r.register(def("split_part", ReturnTypes.TEXT)
                .args(arg("string", TEXT, "the text to split"),
                      arg("delimiter", TEXT, "the delimiter to split on"),
                      arg("n", INT, "1-based index of the field to return"))
                .doc("Splits at *delimiter* and returns the *n*-th field (1-based)."));

        r.register(def("ascii", ReturnTypes.INTEGER).args(arg("character", TEXT, "the text whose first character is coded"))
                .doc("Numeric code of the first character.")
                .paragraph("**Defined for ASCII.** Beyond it the databases part company, because they do not agree what "
                        + "a \"character code\" is: measured, `ascii('Ö')` gives `214` — the Unicode code "
                        + "point — on DuckDB, PostgreSQL, SQLite, Trino and SQL Server, `195` (the first "
                        + "**byte** of the UTF-8 form) on MariaDB and Snowflake, and `50070` (the whole "
                        + "multi-byte value) on Oracle. `chr` mirrors the split. Stay inside ASCII, or map "
                        + "the characters in the application."));
        r.register(def("chr", ReturnTypes.TEXT).args(arg("code", INT, "the character code to convert"))
                .doc("Character with the given numeric code.")
                .paragraph("**Defined for ASCII.** Beyond it the databases part company, because they do not agree what "
                        + "a \"character code\" is: measured, `ascii('Ö')` gives `214` — the Unicode code "
                        + "point — on DuckDB, PostgreSQL, SQLite, Trino and SQL Server, `195` (the first "
                        + "**byte** of the UTF-8 form) on MariaDB and Snowflake, and `50070` (the whole "
                        + "multi-byte value) on Oracle. `chr` mirrors the split. Stay inside ASCII, or map "
                        + "the characters in the application."));
        r.register(def("to_hex", ReturnTypes.TEXT).args(arg("number", INT, "the number to convert to hexadecimal"))
                .doc("Hexadecimal representation of the number, in **upper case** and without a "
                        + "leading `0x`.")
                .paragraph("A negative number comes out as its two's-complement form, and how wide that "
                        + "is depends on the database's integer width — so a negative input is not "
                        + "portable. Positive values agree everywhere."));
        r.register(def("md5", ReturnTypes.TEXT).args(arg("string", TEXT, "the text to hash"))
                .doc("MD5 hash as a hexadecimal string."));

        r.register(positionFunction());
    }

    /** ANSI {@code POSITION(substr IN str)}; dialects without it override (e.g. Oracle INSTR). */
    public static FunctionDefinition positionFunction() {
        return def("position", ReturnTypes.INTEGER)
                .args(arg("substr", TEXT, "the substring to locate"),
                      arg("str", TEXT, "the text to search in"))
                .template("POSITION({0} IN {1})")
                .doc("Position of the first occurrence of *substr* in *str* (1-based, 0 if absent).");
    }

    private static FunctionDefinition def(String name, ReturnTypeInference type) {
        return new FunctionDefinition(name, type).category(FunctionCategory.STRING);
    }
}
