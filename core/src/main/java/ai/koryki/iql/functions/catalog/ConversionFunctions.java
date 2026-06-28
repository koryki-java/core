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

import ai.koryki.catalog.schema.types.CoreTypeFamily;
import ai.koryki.catalog.schema.types.Families;
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.ReturnTypeInference;
import ai.koryki.iql.functions.ReturnTypes;

import static ai.koryki.iql.functions.FunctionArg.arg;

/** Type-cast functions (to_date, to_integer, …). */
public final class ConversionFunctions {

    private ConversionFunctions() {
    }

    public static void register(FunctionRegistry r) {
        r.register(cast("to_date", ReturnTypes.DATE, "DATE")
                .doc("Converts a timestamp or date-compatible value to a DATE, discarding any time "
                        + "component. Text input must be ISO 8601 (`YYYY-MM-DD`); other formats are "
                        + "dialect-dependent — use `parse_date` for an explicit format mask.")
                .example("to_date(c.signup_text)"));
        r.register(cast("to_time", ReturnTypes.TIME, "TIME")
                .doc("Converts a timestamp or time-compatible value to a TIME, discarding the date part. "
                        + "Text input must be in `HH:MM:SS` format.")
                .example("to_time(o.pickup_text)"));
        r.register(cast("to_timestamp", ReturnTypes.TIMESTAMP, "TIMESTAMP")
                .doc("Converts a date or text value to a TIMESTAMP. Text input must be ISO 8601 "
                        + "(`YYYY-MM-DD HH:MM:SS`); a DATE is extended with midnight.")
                .example("to_timestamp(o.created_text)"));
        r.register(cast("to_boolean", ReturnTypes.BOOLEAN, "BOOLEAN")
                .doc("Converts a numeric value to BOOLEAN (`0` = false, non-zero = true).")
                .example("to_boolean(c.active_flag)"));
        registerToText(r);
        r.register(cast("to_float", ReturnTypes.FLOAT, "FLOAT")
                .doc("Converts a numeric or text value to single-precision float. Fractional precision "
                        + "may be lost relative to the source.")
                .example("to_float(c.rating_text)"));
        r.register(cast("to_double", ReturnTypes.DOUBLE, "DOUBLE")
                .doc("Converts a numeric or text value to double-precision float.")
                .example("to_double(m.reading_text)"));
        r.register(cast("to_integer", ReturnTypes.INTEGER, "INTEGER")
                .doc("Converts a numeric or text value to a 32-bit integer. The fractional part is "
                        + "truncated, not rounded; overflow behaviour is dialect-defined.")
                .example("to_integer(o.quantity_text)"));
        r.register(cast("to_bigint", ReturnTypes.BIGINT, "BIGINT")
                .doc("Converts a numeric or text value to a 64-bit integer. Use instead of `to_integer` "
                        + "when values may exceed ±2 147 483 647.")
                .example("to_bigint(e.event_id_text)"));
        r.register(cast("to_smallint", ReturnTypes.SMALLINT, "SMALLINT")
                .doc("Converts a numeric or text value to a 16-bit integer (range −32 768 to 32 767). "
                        + "Overflow behaviour is dialect-defined.")
                .example("to_smallint(c.age_text)"));

        r.register(new FunctionDefinition("to_decimal", ReturnTypes.DECIMAL_CAST)
                .category(FunctionCategory.CONVERSION)
                .args(arg("value", Families.ANY), arg("precision", CoreTypeFamily.INTEGER), arg("scale", CoreTypeFamily.INTEGER))
                .template("CAST({0} AS DECIMAL({1}, {2}))")
                .doc("Converts *value* to a fixed-point decimal. *precision* is the total number of "
                        + "significant digits, *scale* the digits after the point — "
                        + "`to_decimal(value, 10, 2)` holds up to 99 999 999.99.")
                .example("to_decimal(o.unit_price, 10, 2)"));
        r.register(new FunctionDefinition("to_varchar", ReturnTypes.TEXT)
                .category(FunctionCategory.CONVERSION)
                .args(arg("value", Families.ANY), arg("length", CoreTypeFamily.INTEGER))
                .template("CAST({0} AS VARCHAR({1}))")
                .doc("Converts *value* to a variable-length string with an explicit maximum length — "
                        + "useful when the target column has a defined width.")
                .example("to_varchar(c.company_name, 40)"));
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
                    .args(arg("value", family))
                    .template("CAST({0} AS TEXT)")
                    .doc("Converts a " + kqlType + " *value* to TEXT."));
        }
    }

    /** Plain {@code CAST(value AS type)} mapping; reused by dialects with their own physical type names. */
    public static FunctionDefinition cast(String name, ReturnTypeInference type, String sqlType) {
        return new FunctionDefinition(name, type)
                .category(FunctionCategory.CONVERSION)
                .args(arg("value", Families.ANY))
                .template("CAST({0} AS " + sqlType + ")")
                .doc("Converts *value* to " + sqlType + ".");
    }
}
