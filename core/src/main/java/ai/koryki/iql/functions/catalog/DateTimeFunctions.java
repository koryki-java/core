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

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.Families;
import ai.koryki.catalog.types.TypeFamily;
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
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

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
                .doc("Statement timestamp — fixed for the whole statement "
                        + "(PostgreSQL `statement_timestamp()`, otherwise `CURRENT_TIMESTAMP`)."));
        r.register(def("clock_now", ReturnTypes.TIMESTAMP).args().template("CURRENT_TIMESTAMP")
                .doc("Wall-clock timestamp read at the moment of the call. Unlike `now`, it may advance "
                        + "within a single statement on dialects that distinguish the two (PostgreSQL "
                        + "`clock_timestamp()`, MySQL/MariaDB `SYSDATE()`); elsewhere it equals `now`."));
        r.register(def("today", ReturnTypes.DATE).args().template("CURRENT_DATE")
                .doc("Current date."));
        for (String name : List.of("year", "month", "day", "hour", "minute", "second")) {
            r.register(def(name, ReturnTypes.INTEGER).args(arg("value", TEMPORAL, "the date or timestamp to extract from"))
                    .doc("Extracts the " + name + " part of a date or timestamp."));
        }

        // Calendar parts for grouping. quarter and dayofyear agree on every engine; week and
        // dayofweek do NOT — measured, for one Sunday, four different day numbers (0, 1 and 7) and
        // two week numbers. Both are therefore pinned to ISO-8601 and, where an engine's own answer
        // depends on a session setting (Oracle NLS_TERRITORY, SQL Server DATEFIRST, Snowflake
        // WEEK_START), the dialect computes it from a fixed anchor instead of asking the session.
        r.register(def("quarter", ReturnTypes.INTEGER)
                .args(arg("value", TEMPORAL, "the date or timestamp to extract from"))
                .doc("Quarter of the year, 1-4."));
        r.register(def("week", ReturnTypes.INTEGER)
                .args(arg("value", TEMPORAL, "the date or timestamp to extract from"))
                .doc("ISO-8601 week of the year, 1-53. Week 1 is the one containing the first "
                        + "Thursday, so early January can fall in week 52 or 53 of the previous year."));
        r.register(def("dayofweek", ReturnTypes.INTEGER)
                .args(arg("value", TEMPORAL, "the date or timestamp to extract from"))
                .doc("Day of the week the ISO way: **Monday is 1** and Sunday is 7. Engines disagree "
                        + "wildly here — some count Sunday as 0, some as 1 — so this is normalised, and "
                        + "the same query gives the same number on every database."));
        r.register(def("dayofyear", ReturnTypes.INTEGER)
                .args(arg("value", TEMPORAL, "the date or timestamp to extract from"))
                .doc("Day of the year, 1-366."));

        // The one calendar part that is not a part: a *period key*, year and month packed into one
        // sortable number the way MySQL's EXTRACT(YEAR_MONTH FROM x) does. Grouping by `year` and
        // `month` needs two columns and orders wrong as soon as one of them is left out of the
        // sort; `month_begin` answers with a date, which is what you want when the bucket really is
        // a date and not what you want when it is a label. Rendered from year/month arithmetic
        // rather than the engines' own spellings, of which there are four (EXTRACT(YEAR_MONTH),
        // TO_CHAR masks, strftime, DATEPART) and only one — MariaDB's — actually named year_month.
        r.register(def("year_month", ReturnTypes.INTEGER)
                .args(arg("value", TEMPORAL, "the date or timestamp to take the year and month of"))
                .template("(year({0}) * 100 + month({0}))")
                .doc("Year and month packed into one number, `YYYYMM`: "
                        + "year_month('2024-01-15') = 202401.")
                .paragraph("A compact bucket key for monthly grouping: it sorts chronologically on "
                        + "its own, so `GROUP year_month(o.order_date)` needs neither a second "
                        + "column nor a tie-break. Use `month_begin` where the bucket has to stay a "
                        + "date — for joining to a calendar table, or for further date arithmetic."));

        // Six dialects implement these through parseWithMask, each with its own rendering — but the
        // catalog had no entry, so they were not KQL functions at all. Consequences: no page in
        // docs/functions/datetime.md, and on the two dialects that lack them (SQLite has no
        // strptime; T-SQL's PARSE/CONVERT take style codes, not masks) a call was an *unknown*
        // function, which is only a warning and passes the name through to the engine verbatim.
        // The author learned of it from the driver. Registered here so the two can declare it,
        // exactly as to_number does for the five dialects without it.
        String maskNote = "The *format* mask is written in KQL's own vocabulary and translated per "
                + "dialect, like `to_char`'s — see the *Formatting Functions* page for the tokens.";
        r.register(def("parse_date", ReturnTypes.DATE)
                .args(arg("value", TEXT, "the text to parse"), arg("format", TEXT, "the format mask"))
                .doc("Parses *value* into a date using the *format* mask.")
                .paragraph(maskNote));
        r.register(def("parse_time", ReturnTypes.TIME)
                .args(arg("value", TEXT, "the text to parse"), arg("format", TEXT, "the format mask"))
                .doc("Parses *value* into a time using the *format* mask.")
                .paragraph(maskNote));
        r.register(def("parse_timestamp", ReturnTypes.TIMESTAMP)
                .args(arg("value", TEXT, "the text to parse"), arg("format", TEXT, "the format mask"))
                .doc("Parses *value* into a timestamp using the *format* mask.")
                .paragraph(maskNote));
        r.register(def("date_trunc", ReturnTypes.ARG1)
                .args(arg("part", TEXT, "the precision to truncate to, e.g. 'month'"),
                        arg("value", TEMPORAL, "the date or timestamp to truncate"))
                .doc("Truncates *value* to the given precision, e.g. date_trunc('month', o.order_date)."));

        r.register(def("make_date", ReturnTypes.DATE)
                .args(arg("year", INT, "the calendar year"),
                        arg("month", INT, "the month of year, 1-12"),
                        arg("day", INT, "the day of month, 1-31"))
                .doc("Builds a date from year, month and day."));
        r.register(def("make_time", ReturnTypes.TIME)
                .args(arg("hour", INT, "the hour of day, 0-23"),
                        arg("minute", INT, "the minute of hour, 0-59"),
                        arg("second", INT, "the second of minute, 0-59"))
                .doc("Builds a time from hour, minute and second."));
        r.register(def("make_timestamp", ReturnTypes.TIMESTAMP)
                .args(arg("year", INT, "the calendar year"),
                        arg("month", INT, "the month of year, 1-12"),
                        arg("day", INT, "the day of month, 1-31"),
                        arg("hour", INT, "the hour of day, 0-23"),
                        arg("minute", INT, "the minute of hour, 0-59"),
                        arg("second", INT, "the second of minute, 0-59"))
                .doc("Builds a timestamp from its six components."));

        // --- Distances and Rolling (see docs/TEMPORAL.md) ---------------------
        // Reference semantics: java.time. Distances are ChronoUnit.between
        // (signed whole units completed); canonical templates are the
        // DuckDB-flavored baseline, dialects override.

        r.register(def("days_between", ReturnTypes.INTEGER)
                .args(arg("start", TEMPORAL, "the start of the span"),
                        arg("end", TEMPORAL, "the end of the span"))
                .template("CAST(trunc(date_diff('second', {0}, {1}) / 86400.0) AS INTEGER)")
                .doc("Signed number of **complete days** from *start* to *end* — whole 24-hour "
                        + "periods that fit inside the interval, not midnights crossed.")
                .paragraph("For two dates the two readings coincide, which is why the difference "
                        + "only shows with a time of day: `days_between('2023-01-01 23:00', "
                        + "'2023-01-02 01:00')` is **0**, because two hours is not a day, even "
                        + "though a midnight lies between them."
                        + ai.koryki.antlr.Text.NL
                        + ai.koryki.antlr.Text.NL
                        + "Measured before this was settled, the engines split five to three: "
                        + "DuckDB, PostgreSQL, SQL Server, Snowflake and SQLite counted the "
                        + "boundary, MariaDB, Oracle and Trino the elapsed day. All eight now "
                        + "count the elapsed day, matching `ChronoUnit.DAYS`."));
        r.register(def("months_between", ReturnTypes.INTEGER)
                .args(arg("start", TEMPORAL, "the start of the span"),
                        arg("end", TEMPORAL, "the end of the span"))
                .template("(CAST(EXTRACT(YEAR FROM age({1}, {0})) * 12 + EXTRACT(MONTH FROM age({1}, {0})) AS INTEGER))")
                .doc("Signed number of whole months completed from *start* to *end*; "
                        + "months_between('2023-01-31', '2023-03-30') = 1 — the second month is not complete."));
        r.register(def("years_between", ReturnTypes.INTEGER)
                .args(arg("start", TEMPORAL, "the start of the span"),
                        arg("end", TEMPORAL, "the end of the span"))
                .template("CAST(EXTRACT(YEAR FROM age({1}, {0})) AS INTEGER)")
                .doc("Signed number of whole years completed from *start* to *end*."));

        // calendar_distance: the *variable* (civil) decomposition into months/days + clock — complements
        // the fixed elapsed-seconds difference (ts − ts). SQL only emits the two instants as epoch-seconds
        // ("start;end"); the calendar decomposition (Period.between, reference java.time semantics) happens
        // in the decoder, so the result is uniform across every dialect. Per-dialect SQL is just
        // timestampToEpochSeconds (already defined for ts − ts) + the pairText concatenation.
        r.register(new FunctionDefinition("calendar_distance", ReturnTypes.CALENDAR_DISTANCE) {
            @Override
            protected String renderBody(SqlSelectRenderer renderer, Function function, int indent) {
                SqlDialect d = renderer.getDialect();
                Expression start = function.getArguments().get(0);
                Expression end   = function.getArguments().get(1);
                String s = d.epochSeconds(renderer.toSql(start, indent), renderer.resolveType(start));
                String e = d.epochSeconds(renderer.toSql(end, indent), renderer.resolveType(end));
                return d.pairText(s, e);
            }
        }.args(arg("start", TEMPORAL, "the start of the span"),
                arg("end", TEMPORAL, "the end of the span")).category(FunctionCategory.DATETIME)
                .doc("Calendar (civil) distance from *start* to *end* as a mixed interval "
                        + "(years/months/days + clock): "
                        + "calendar_distance('2025-01-01', '2026-05-15 12:00') = 1y4mo14d12h. "
                        + "Use ts − ts for a fixed elapsed span."));

        // add: duration arithmetic with a computed amount (duration literals only cover constants)
        r.register(def("day_add", ReturnTypes.ARG0)
                .args(arg("value", TEMPORAL, "the date or timestamp to shift"),
                        arg("n", INT, "the number of days to add"))
                .template("({0} + INTERVAL ({1}) DAY)")
                .doc("*value* shifted by *n* days; *n* may be any expression."));
        r.register(def("month_add", ReturnTypes.ARG0)
                .args(arg("value", TEMPORAL, "the date or timestamp to shift"),
                        arg("n", INT, "the number of months to add"))
                .template("({0} + INTERVAL ({1}) MONTH)")
                .doc("*value* shifted by *n* months, clamped to the end of the month: "
                        + "month_add('2023-01-31', 1) = 2023-02-28."));
        r.register(def("year_add", ReturnTypes.ARG0)
                .args(arg("value", TEMPORAL, "the date or timestamp to shift"),
                        arg("n", INT, "the number of years to add"))
                .template("({0} + INTERVAL ({1}) YEAR)")
                .doc("*value* shifted by *n* years, clamped (Feb 29 + 1 year = Feb 28)."));

        // begin: start of the unit, type-preserving (DATE->DATE, TIMESTAMP->TIMESTAMP with
        // time zeroed) — the named, dialect-portable replacement for date_trunc.
        //
        // "Portable" is the whole point and is easy to underrate: Oracle, MariaDB, SQL Server and
        // SQLite each render date_trunc from a hand-written switch, so an unlisted unit is not a
        // slower path but an UnsupportedOperationException. These names carry a per-dialect
        // override each and therefore work everywhere; a raw date_trunc call does not.
        r.register(def("minute_begin", ReturnTypes.ARG0).args(arg("value", TEMPORAL, "the date or timestamp to floor to the minute"))
                .template("date_trunc('minute', {0})")
                .doc("Start of the minute (seconds become 00)."));
        r.register(def("hour_begin", ReturnTypes.ARG0).args(arg("value", TEMPORAL, "the date or timestamp to floor to the hour"))
                .template("date_trunc('hour', {0})")
                .doc("Start of the hour (minutes and seconds become 00).")
                .paragraph("The bucket for sub-daily data, and the one the family was missing: with "
                        + "nothing between `minute` (the part, 0-59, which folds every hour of every "
                        + "day together) and `day_begin`, an hourly trend had no portable answer at "
                        + "all. `date_trunc('hour', x)` was not one -- four of the eight engines "
                        + "render that call by hand and knew only day, month, quarter and year."));
        r.register(def("day_begin", ReturnTypes.ARG0).args(arg("value", TEMPORAL, "the date or timestamp to floor to the day"))
                .template("date_trunc('day', {0})")
                .doc("Start of the day (time becomes 00:00:00)."));
        r.register(def("week_begin", ReturnTypes.ARG0).args(arg("value", TEMPORAL, "the date or timestamp to floor to the week"))
                .template("date_trunc('week', {0})")
                .doc("Monday of the week (pairs with week_end). Grouping by this is the reliable way "
                        + "to bucket by week: it is a real date, so it sorts, and it has none of the "
                        + "year-boundary trouble of grouping by `year` and `week` together — "
                        + "2026-12-28 is in week 1, but of 2027."));
        r.register(def("month_begin", ReturnTypes.ARG0).args(arg("value", TEMPORAL, "the date or timestamp to floor to the month"))
                .template("date_trunc('month', {0})")
                .doc("First day of the month (pairs with month_end)."));
        r.register(def("quarter_begin", ReturnTypes.ARG0).args(arg("value", TEMPORAL, "the date or timestamp to floor to the quarter"))
                .template("date_trunc('quarter', {0})")
                .doc("First day of the quarter."));
        r.register(def("year_begin", ReturnTypes.ARG0).args(arg("value", TEMPORAL, "the date or timestamp to floor to the year"))
                .template("date_trunc('year', {0})")
                .doc("First day of the year."));

        // end: the last DAY of the unit — always a DATE (deliberately not a last instant,
        // which has no precision-independent value; for timestamp ranges filter half-open
        // with begin + the next begin, see docs/TEMPORAL.md)
        r.register(def("week_end", ReturnTypes.DATE).args(arg("value", TEMPORAL, "the date or timestamp to take the week end of"))
                .template("CAST(date_trunc('week', {0}) + INTERVAL 6 DAY AS DATE)")
                .doc("Sunday of the week."));
        r.register(def("month_end", ReturnTypes.DATE).args(arg("value", TEMPORAL, "the date or timestamp to take the month end of"))
                .template("last_day({0})")
                .doc("Last day of the month (Excel EOMONTH): month_end('2024-02-10') = 2024-02-29."));
        r.register(def("quarter_end", ReturnTypes.DATE).args(arg("value", TEMPORAL, "the date or timestamp to take the quarter end of"))
                .template("last_day(date_trunc('quarter', {0}) + INTERVAL 2 MONTH)")
                .doc("Last day of the quarter."));
        r.register(def("year_end", ReturnTypes.DATE).args(arg("value", TEMPORAL, "the date or timestamp to take the year end of"))
                .template("last_day(date_trunc('year', {0}) + INTERVAL 11 MONTH)")
                .doc("Last day of the year (December 31)."));

        // Explicit zone crossing — the only escape from the zone-free algebra (docs/TEMPORAL.md).
        r.register(new ZoneShiftFunctionDefinition("at_zone", true)
                .category(FunctionCategory.DATETIME)
                .doc("Reads *value* as a model-zone wall-clock value and returns its wall-clock in the named "
                        + "zone (e.g. for day-bucketing: date(at_zone(o.ts, 'Europe/Berlin')))."));
        r.register(new ZoneShiftFunctionDefinition("to_utc", false)
                .category(FunctionCategory.DATETIME)
                .doc("Inverse of at_zone: reads *value* as a wall-clock in the named zone and returns the "
                        + "model-zone wall-clock value."));
    }

    /**
     * ANSI {@code EXTRACT(part FROM x)} rendering for the date-part functions; opted into by dialects.
     *
     * <p>Every name here must also exist in the canonical catalog. {@code dayofmonth} did not: it
     * rendered the same {@code EXTRACT(DAY FROM x)} as {@code day}, so it was a second name for one
     * function — and because only PostgreSQL and Oracle call this method, it was a second name that
     * existed on two dialects out of eight, with no function page of its own. A query using it
     * worked where it was written and failed everywhere else. Removed; {@code day} is the name.
     */
    public static void registerExtractParts(FunctionRegistry r) {
        for (String[] entry : new String[][]{
                {"year", "YEAR"}, {"month", "MONTH"}, {"day", "DAY"},
                {"hour", "HOUR"}, {"minute", "MINUTE"}, {"second", "SECOND"},
                {"quarter", "QUARTER"}, {"week", "WEEK"}, {"dayofweek", "ISODOW"}, {"dayofyear", "DOY"}}) {
            r.register(def(entry[0], ReturnTypes.INTEGER)
                    .args(arg("value", TEMPORAL, "the date or timestamp to extract from"))
                    .template("EXTRACT(" + entry[1] + " FROM {0})")
                    .doc("Extracts the " + entry[0] + " part of a date or timestamp."));
        }
    }

    /**
     * {@code name(value, format)} mapped onto a two-argument dialect parse function, for dialects
     * that accept the canonical KQL mask as-is (PostgreSQL, Oracle, Snowflake — see
     * {@link ai.koryki.iql.functions.FormatMask}).
     */
    public static FunctionDefinition parseTwoArg(String name, ReturnTypeInference type, String sqlFunction) {
        return def(name, type)
                .args(arg("value", TEXT, "the text to parse"),
                        arg("format", TEXT, "the format mask"))
                .template(sqlFunction + "({0}, {1})")
                .doc("Parses *value* using the *format* mask.");
    }

    /**
     * As {@link #parseTwoArg}, for dialects whose parse function does not understand the canonical
     * mask: {@code mask} translates the format argument first, and {@code sql} assembles the call
     * from the rendered value and the translated format.
     */
    public static FunctionDefinition parseWithMask(String name, ReturnTypeInference type,
            UnaryOperator<String> mask, BinaryOperator<String> sql) {
        return new FunctionDefinition(name, type) {
            @Override
            protected String renderBody(SqlSelectRenderer renderer, Function function, int indent) {
                var args = function.getArguments();
                if (args.size() != 2) {
                    throw new KorykiaiException(name + " requires two arguments: value, format");
                }
                return sql.apply(renderer.toSql(args.get(0), indent),
                        mask.apply(renderer.toSql(args.get(1), indent)));
            }
        }.category(FunctionCategory.DATETIME)
                .args(arg("value", TEXT, "the text to parse"),
                        arg("format", TEXT, "the format mask"))
                .doc("Parses *value* using the *format* mask.");
    }

    private static FunctionDefinition def(String name, ReturnTypeInference type) {
        return new FunctionDefinition(name, type).category(FunctionCategory.DATETIME);
    }
}
