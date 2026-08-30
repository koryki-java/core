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
import ai.koryki.catalog.types.Families;
import ai.koryki.catalog.types.TypeFamily;
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.ReturnTypeInference;
import ai.koryki.iql.functions.ReturnTypes;

import static ai.koryki.iql.functions.FunctionArg.arg;

/**
 * Type-cast functions: one name per target type ({@code to_date}, {@code to_integer}, …) rather than
 * a single {@code cast(value, type)}. The type is part of the name because it is not a value — it
 * cannot come from a column or a parameter — and naming it makes every call resolvable by the
 * catalog alone, without the grammar needing a notion of type literals.
 *
 * <p>Most entries render as a plain {@code CAST(value AS type)} built by {@link #cast}; a dialect
 * whose physical type is spelled differently overrides the template. Two entries take an explicit
 * width instead — {@code to_decimal(value, precision, scale)} and {@code to_varchar(value, length)} —
 * because a decimal without a scale and a varchar without a length mean different things on
 * different databases, and guessing would be worse than asking.
 *
 * <p>{@code to_text} is the exception in shape: it is registered once per source type-family, so the
 * documentation can say what is being converted rather than listing one entry that accepts anything.
 *
 * <p><b>The integer conversions round.</b> They do not truncate — {@code 1.7} becomes {@code 2} and
 * {@code 2.5} becomes {@code 3}, away from zero. Two dialects need a collar to get there: SQLite's
 * and SQL Server's bare {@code CAST} truncates, so both wrap the value in {@code ROUND} (see
 * {@code SqliteDialect}, {@code MssqlDialect}). Without it the same query answered differently
 * depending on the database, and both answers looked plausible.
 */
public final class ConversionFunctions {

    private ConversionFunctions() {
    }

    public static void register(FunctionRegistry r) {
        r.register(cast("to_date", ReturnTypes.DATE, "DATE", Families.TEMPORAL_OR_TEXT)
                .doc("Converts a timestamp or date-compatible value to a DATE, discarding any time "
                        + "component. Text input must be ISO 8601 (`YYYY-MM-DD`); other formats are "
                        + "dialect-dependent — use `parse_date` for an explicit format mask."));
        r.register(cast("to_time", ReturnTypes.TIME, "TIME", Families.TEMPORAL_OR_TEXT)
                .doc("Converts a timestamp or time-compatible value to a TIME, discarding the date part. "
                        + "Text input must be in `HH:MM:SS` format."));
        r.register(cast("to_timestamp", ReturnTypes.TIMESTAMP, "TIMESTAMP", Families.TEMPORAL_OR_TEXT)
                .doc("Converts a date or text value to a TIMESTAMP. Text input must be ISO 8601 "
                        + "(`YYYY-MM-DD HH:MM:SS`); a DATE is extended with midnight."));
        r.register(cast("to_boolean", ReturnTypes.BOOLEAN, "BOOLEAN", Families.NUMBER_OR_TEXT)
                .doc("Converts a numeric value to BOOLEAN: `0` is false, anything else is true.")
                .paragraph("**What comes back is not spelled the same everywhere.** Databases with a "
                        + "native boolean type return `true`/`false`; MariaDB and SQLite have none and "
                        + "return `1`/`0` instead. The truth value is the same, the notation is not — "
                        + "so compare the result rather than matching it against the text `'true'`."));
        registerToText(r);
        r.register(cast("to_float", ReturnTypes.FLOAT, "FLOAT", Families.NUMBER_OR_TEXT)
                .doc("Converts a numeric or text value to single-precision float. Fractional precision "
                        + "may be lost relative to the source."));
        r.register(cast("to_double", ReturnTypes.DOUBLE, "DOUBLE", Families.NUMBER_OR_TEXT)
                .doc("Converts a numeric or text value to double-precision float."));
        r.register(cast("to_integer", ReturnTypes.INTEGER, "INTEGER", Families.NUMBER_OR_TEXT)
                .doc("Converts a numeric or text value to a 32-bit integer. A fractional part is "
                        + "**rounded**, not truncated, and a half-way value rounds away from zero — "
                        + "`1.7` becomes `2`, `-1.7` becomes `-2`, `2.5` becomes `3`. Use `trunc` "
                        + "first if the value must never be inflated. Overflow behaviour is "
                        + "dialect-defined."));
        r.register(cast("to_bigint", ReturnTypes.BIGINT, "BIGINT", Families.NUMBER_OR_TEXT)
                .doc("Converts a numeric or text value to a 64-bit integer. Rounds like `to_integer`; use "
                        + "it instead when values may exceed ±2 147 483 647."));
        r.register(cast("to_smallint", ReturnTypes.SMALLINT, "SMALLINT", Families.NUMBER_OR_TEXT)
                .doc("Converts a numeric or text value to a 16-bit integer (range −32 768 to 32 767). "
                        + "Rounds like `to_integer`. Overflow behaviour is dialect-defined."));

        r.register(new FunctionDefinition("to_decimal", ReturnTypes.DECIMAL_CAST)
                .category(FunctionCategory.CONVERSION)
                .args(
                        arg("value", Families.NUMBER_OR_TEXT, "the value to convert to a decimal"),
                        arg("precision", CoreTypeFamily.INTEGER, "total number of significant digits"),
                        arg("scale", CoreTypeFamily.INTEGER, "number of digits after the decimal point"))
                .template("CAST({0} AS DECIMAL({1}, {2}))")
                .doc("Converts *value* to a fixed-point decimal. *precision* is the total number of "
                        + "significant digits, *scale* the digits after the point — "
                        + "`to_decimal(value, 10, 2)` holds up to 99 999 999.99."));
        r.register(new FunctionDefinition("to_varchar", ReturnTypes.TEXT)
                .category(FunctionCategory.CONVERSION)
                .args(
                        arg("value", Families.ANY, "the value to convert to text"),
                        arg("length", CoreTypeFamily.INTEGER, "maximum length of the resulting string"))
                .template("CAST({0} AS VARCHAR({1}))")
                .doc("Converts *value* to a variable-length string with an explicit maximum length — "
                        + "useful when the target column has a defined width."));
    }

    /**
     * {@code to_text} is type-overloaded: every type-family converts to TEXT, registered as one
     * overload per source family. They all render identically ({@code CAST(value AS TEXT)});
     * a dialect needing a different physical type re-renders them all via
     * {@link FunctionRegistry#overrideAll} (see {@code OracleDialect}).
     * All render identically; the BOOLEAN overload additionally tags its TEXT result with
     * TEXT_FROM_BOOLEAN so the read layer can present 0/1 as true/false.
     */
    private static void registerToText(FunctionRegistry r) {
        for (CoreTypeFamily family : CoreTypeFamily.values()) {

            ReturnTypeInference returns =
                    family == CoreTypeFamily.BOOLEAN ? ReturnTypes.TEXT_FROM_BOOLEAN : ReturnTypes.TEXT;

            // User-facing KQL type name in the doc: INTERVAL's KQL name is DURATION.
            String kqlType = family == CoreTypeFamily.INTERVAL ? "DURATION" : family.name();

            r.register(new FunctionDefinition("to_text", returns)
                    .category(FunctionCategory.CONVERSION)
                    .args(arg("value", family, "the value to convert to text"))
                    .template("CAST({0} AS TEXT)")
                    .doc("Converts a " + kqlType + " *value* to TEXT."));
        }
    }

    /** Plain {@code CAST(value AS type)} mapping; reused by dialects with their own physical type names. */
    public static FunctionDefinition cast(String name, ReturnTypeInference type, String sqlType, TypeFamily input) {
        return new FunctionDefinition(name, type)
                .category(FunctionCategory.CONVERSION)
                .args(arg("value", input, "the value to convert"))
                .template("CAST({0} AS " + sqlType + ")")
                .doc("Converts *value* to " + sqlType + ".");
    }
}
