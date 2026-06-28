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
import ai.koryki.catalog.schema.types.TypeFamily;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.ReturnTypeInference;
import ai.koryki.iql.functions.ReturnTypes;
import ai.koryki.iql.functions.ZoneShiftFunctionDefinition;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;

import java.util.List;

import static ai.koryki.iql.functions.FunctionArg.arg;

/** Date/time functions (now, today, date parts, parsing). */
public final class DateTimeFunctions {

    private static final CoreTypeFamily TEXT = CoreTypeFamily.TEXT;
    private static final CoreTypeFamily INT = CoreTypeFamily.INTEGER;
    private static final TypeFamily TEMPORAL = Families.TEMPORAL;
    private static final TypeFamily ANY = Families.ANY;

    private DateTimeFunctions() {
    }

    public static void register(FunctionRegistry r) {
        r.register(def("now", ReturnTypes.TIMESTAMP).args().template("CURRENT_TIMESTAMP")
                .doc("Current timestamp at statement execution."));
        r.register(def("clock_now", ReturnTypes.TIMESTAMP).args().template("CURRENT_TIMESTAMP")
                .doc("Current timestamp at the time of the call."));
        r.register(def("today", ReturnTypes.DATE).args().template("CURRENT_DATE")
                .doc("Current date."));
        r.register(def("date",      ReturnTypes.DATE).args(arg("value", ANY))
                .doc("Casts *value* to a DATE, discarding any time component — the type-named cast; "
                        + "`to_date` is the portable form with explicit ISO-8601 text rules.")
                .example("date(at_zone(o.ts, 'Europe/Berlin'))"));
        r.register(def("time",      ReturnTypes.TIME).args(arg("value", ANY))
                .doc("Casts *value* to a TIME, discarding the date part.")
                .example("time(o.order_ts)"));
        r.register(def("timestamp", ReturnTypes.TIMESTAMP).args(arg("value", ANY))
                .doc("Casts *value* to a TIMESTAMP; a DATE is extended with midnight (00:00:00).")
                .example("timestamp(o.order_date)"));
        for (String name : List.of("year", "month", "day", "hour", "minute", "second")) {
            r.register(def(name, ReturnTypes.INTEGER).args(arg("value", TEMPORAL))
                    .doc("Extracts the " + name + " part of a date or timestamp."));
        }
        r.register(def("date_trunc", ReturnTypes.ARG1).args(arg("part", TEXT), arg("value", TEMPORAL))
                .doc("Truncates *value* to the given precision, e.g. date_trunc('month', o.order_date).")
                .example("date_trunc('month', o.order_date)"));

        r.register(def("make_date", ReturnTypes.DATE)
                .args(arg("year", INT), arg("month", INT), arg("day", INT))
                .doc("Builds a date from year, month and day.")
                .example("make_date(2024, 1, 31)"));
        r.register(def("make_time", ReturnTypes.TIME)
                .args(arg("hour", INT), arg("minute", INT), arg("second", INT))
                .doc("Builds a time from hour, minute and second.")
                .example("make_time(14, 30, 0)"));
        r.register(def("make_timestamp", ReturnTypes.TIMESTAMP)
                .args(arg("year", INT), arg("month", INT), arg("day", INT), arg("hour", INT), arg("minute", INT), arg("second", INT))
                .doc("Builds a timestamp from its six components.")
                .example("make_timestamp(2024, 1, 31, 14, 30, 0)"));

        // --- Distances and Rolling (see docs/TEMPORAL.md) ---------------------
        // Reference semantics: java.time. Distances are ChronoUnit.between
        // (signed whole units completed); canonical templates are the
        // DuckDB-flavored baseline, dialects override.

        r.register(def("days_between", ReturnTypes.INTEGER)
                .args(arg("start", TEMPORAL), arg("end", TEMPORAL))
                .template("date_diff('day', {0}, {1})")
                .doc("Signed number of whole days from *start* to *end*.")
                .example("days_between(o.order_date, o.shipped_date)"));
        r.register(def("months_between", ReturnTypes.INTEGER)
                .args(arg("start", TEMPORAL), arg("end", TEMPORAL))
                .template("(CAST(EXTRACT(YEAR FROM age({1}, {0})) * 12 + EXTRACT(MONTH FROM age({1}, {0})) AS INTEGER))")
                .doc("Signed number of whole months completed from *start* to *end*; "
                        + "months_between('2023-01-31', '2023-03-30') = 1 — the second month is not complete.")
                .example("months_between(o.order_date, o.shipped_date)"));
        r.register(def("years_between", ReturnTypes.INTEGER)
                .args(arg("start", TEMPORAL), arg("end", TEMPORAL))
                .template("CAST(EXTRACT(YEAR FROM age({1}, {0})) AS INTEGER)")
                .doc("Signed number of whole years completed from *start* to *end*.")
                .example("years_between(c.member_since, today())"));

        // calendar_distance: the *variable* (civil) decomposition into months/days + clock — complements
        // the fixed elapsed-seconds difference (ts − ts). SQL only emits the two instants as epoch-seconds
        // ("start;end"); the calendar decomposition (Period.between, reference java.time semantics) happens
        // in the decoder, so the result is uniform across every dialect. Per-dialect SQL is just
        // timestampToEpochSeconds (already defined for ts − ts) + the pairText concatenation.
        r.register(new FunctionDefinition("calendar_distance", ReturnTypes.CALENDAR_DISTANCE) {
            @Override
            public String render(SqlSelectRenderer renderer, Function function, int indent) {
                SqlDialect d = renderer.getDialect();
                Expression start = function.getArguments().get(0);
                Expression end   = function.getArguments().get(1);
                String s = d.epochSeconds(renderer.toSql(start, indent), renderer.resolveType(start));
                String e = d.epochSeconds(renderer.toSql(end, indent), renderer.resolveType(end));
                return d.pairText(s, e);
            }
        }.args(arg("start", TEMPORAL), arg("end", TEMPORAL)).category(FunctionCategory.DATETIME)
                .doc("Calendar (civil) distance from *start* to *end* as a mixed interval "
                        + "(years/months/days + clock): "
                        + "calendar_distance('2025-01-01', '2026-05-15 12:00') = 1y4mo14d12h. "
                        + "Use ts − ts for a fixed elapsed span.")
                .example("calendar_distance(o.order_date, o.shipped_date)"));

        // add: duration arithmetic with a computed amount (duration literals only cover constants)
        r.register(def("day_add", ReturnTypes.ARG0)
                .args(arg("value", TEMPORAL), arg("n", INT))
                .template("({0} + INTERVAL ({1}) DAY)")
                .doc("*value* shifted by *n* days; *n* may be any expression.")
                .example("day_add(o.order_date, o.processing_days)"));
        r.register(def("month_add", ReturnTypes.ARG0)
                .args(arg("value", TEMPORAL), arg("n", INT))
                .template("({0} + INTERVAL ({1}) MONTH)")
                .doc("*value* shifted by *n* months, clamped to the end of the month: "
                        + "month_add('2023-01-31', 1) = 2023-02-28.")
                .example("month_add(o.order_date, 1)"));
        r.register(def("year_add", ReturnTypes.ARG0)
                .args(arg("value", TEMPORAL), arg("n", INT))
                .template("({0} + INTERVAL ({1}) YEAR)")
                .doc("*value* shifted by *n* years, clamped (Feb 29 + 1 year = Feb 28).")
                .example("year_add(c.contract_start, 1)"));

        // begin: start of the unit, type-preserving (DATE->DATE, TIMESTAMP->TIMESTAMP with
        // time zeroed) — the named, dialect-portable replacement for date_trunc
        r.register(def("day_begin", ReturnTypes.ARG0).args(arg("value", TEMPORAL))
                .template("date_trunc('day', {0})")
                .doc("Start of the day (time becomes 00:00:00).")
                .example("day_begin(o.ordered_at)"));
        r.register(def("month_begin", ReturnTypes.ARG0).args(arg("value", TEMPORAL))
                .template("date_trunc('month', {0})")
                .doc("First day of the month (pairs with month_end).")
                .example("month_begin(o.order_date)"));
        r.register(def("quarter_begin", ReturnTypes.ARG0).args(arg("value", TEMPORAL))
                .template("date_trunc('quarter', {0})")
                .doc("First day of the quarter.")
                .example("quarter_begin(o.order_date)"));
        r.register(def("year_begin", ReturnTypes.ARG0).args(arg("value", TEMPORAL))
                .template("date_trunc('year', {0})")
                .doc("First day of the year.")
                .example("year_begin(o.order_date)"));

        // end: the last DAY of the unit — always a DATE (deliberately not a last instant,
        // which has no precision-independent value; for timestamp ranges filter half-open
        // with begin + the next begin, see docs/TEMPORAL.md)
        r.register(def("month_end", ReturnTypes.DATE).args(arg("value", TEMPORAL))
                .template("last_day({0})")
                .doc("Last day of the month (Excel EOMONTH): month_end('2024-02-10') = 2024-02-29.")
                .example("month_end(o.order_date)"));
        r.register(def("quarter_end", ReturnTypes.DATE).args(arg("value", TEMPORAL))
                .template("last_day(date_trunc('quarter', {0}) + INTERVAL 2 MONTH)")
                .doc("Last day of the quarter.")
                .example("quarter_end(o.order_date)"));
        r.register(def("year_end", ReturnTypes.DATE).args(arg("value", TEMPORAL))
                .template("last_day(date_trunc('year', {0}) + INTERVAL 11 MONTH)")
                .doc("Last day of the year (December 31).")
                .example("year_end(o.order_date)"));

        // Explicit zone crossing — the only escape from the zone-free algebra (docs/TEMPORAL.md).
        r.register(new ZoneShiftFunctionDefinition("at_zone", true)
                .doc("Reads *value* as a model-zone wall-clock value and returns its wall-clock in the named "
                        + "zone (e.g. for day-bucketing: date(at_zone(o.ts, 'Europe/Berlin'))).")
                .example("date(at_zone(o.ts, 'Europe/Berlin'))"));
        r.register(new ZoneShiftFunctionDefinition("to_utc", false)
                .doc("Inverse of at_zone: reads *value* as a wall-clock in the named zone and returns the "
                        + "model-zone wall-clock value.")
                .example("to_utc(o.local_ts, 'Europe/Berlin')"));
    }

    /** ANSI {@code EXTRACT(part FROM x)} rendering for the date-part functions; opted into by dialects. */
    public static void registerExtractParts(FunctionRegistry r) {
        for (String[] entry : new String[][]{
                {"year", "YEAR"}, {"month", "MONTH"}, {"day", "DAY"},
                {"hour", "HOUR"}, {"minute", "MINUTE"}, {"second", "SECOND"}}) {
            r.register(def(entry[0], ReturnTypes.INTEGER)
                    .args(arg("value", TEMPORAL))
                    .template("EXTRACT(" + entry[1] + " FROM {0})")
                    .doc("Extracts the " + entry[0] + " part of a date or timestamp."));
        }
    }

    /** {@code name(value, format)} mapped onto a two-argument dialect parse function. */
    public static FunctionDefinition parseTwoArg(String name, ReturnTypeInference type, String sqlFunction) {
        return def(name, type)
                .args(arg("value", TEXT), arg("format", TEXT))
                .template(sqlFunction + "({0}, {1})")
                .doc("Parses *value* using the dialect-native *format* mask.");
    }

    private static FunctionDefinition def(String name, ReturnTypeInference type) {
        return new FunctionDefinition(name, type).category(FunctionCategory.DATETIME);
    }
}
