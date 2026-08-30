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
package ai.koryki.sqlite.iql;

import ai.koryki.iql.SqlRenderer;
import ai.koryki.sqlite.iql.validate.SqliteValidator;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.Families;
import ai.koryki.iql.Identifier;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.functions.FormatMask;
import ai.koryki.iql.functions.FunctionArg;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.FunctionRenderer;
import ai.koryki.iql.functions.ReturnTypes;
import ai.koryki.iql.functions.StandardFunctions;
import ai.koryki.iql.query.Duration;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
import ai.koryki.iql.typing.TimeEncodings;
import ai.koryki.catalog.types.CoreTypeEncoding;
import ai.koryki.catalog.types.TypeDescriptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteDialect implements SqlDialect {

    public static final SqlDialect INSTANCE = new SqliteDialect();

    // SQLite: all compound operators share one precedence level and parenthesized
    // compound operands are a syntax error — left-deep set chains render flat
    @Override
    public boolean uniformSetOperatorPrecedence() {
        return true;
    }

    private SqliteDialect() {
    }

    private static final FunctionRenderer FUNCTION_RENDERER = buildFunctionRenderer();

    @Override
    public FunctionRenderer getFunctionRenderer() {
        return FUNCTION_RENDERER;
    }

    /**
     * SQLite has a small built-in function set: strings map to {@code length/substr/instr/…},
     * date/time to {@code strftime()} and {@code date()/datetime()} modifiers, and there is no
     * regexp, hashing, padding or timezone support (those are marked unsupported).
     */
    private static FunctionRenderer buildFunctionRenderer() {
        FunctionRegistry r = StandardFunctions.registry();

        r.override("quarter",   "((CAST(strftime('%m', {0}) AS INTEGER) + 2) / 3)");
        r.override("week",      "((CAST(strftime('%j', date({0}, '-3 days', 'weekday 4')) AS INTEGER) - 1) / 7 + 1)");
        r.override("dayofweek", "(((CAST(strftime('%w', {0}) AS INTEGER) + 6) % 7) + 1)");
        r.override("dayofyear", "CAST(strftime('%j', {0}) AS INTEGER)");
        r.windowUnsupported("count_distinct");   // COUNT(DISTINCT ...) cannot be a window function here
        // Measured: SQLite has neither the row constructor nor the comma list.
        r.unsupportedVariadic("count_distinct", ai.koryki.iql.functions.catalog.AggregateFunctions.COUNT_DISTINCT_HINT);

        // string functions SQLite spells differently
        r.override("char_length",      "length({0})");
        r.override("left",             "substr({0}, 1, {1})");
        // right(s, n) as substr(s, -n) is wrong at the edges: for n = 0, -0 = 0, and substr(s, 0)
        // returns the WHOLE string instead of an empty one -- measured on string_edges, oracle and
        // sqlite returned "alphabet" there while duckdb and postgresql returned "". The length
        // arithmetic covers both edges: n = 0 gives empty, n > length the whole string.
        r.override("right", "substr({0}, CASE WHEN {1} <= 0 THEN length({0}) + 1 ELSE max(length({0}) - {1} + 1, 1) END)");
        r.override("position",         "instr({1}, {0})");        // position(substring, string)
        r.override("ascii",            "unicode({0})");
        r.override("chr",              "char({0})");
        // random() is documented as 0.0 <= x < 1.0. SQLite has the name but not the contract:
        // it returns a signed 64-bit integer (measured: -859828082107461929), so the column was
        // neither a probability nor a FLOAT. Mapping the full int64 range onto [0,1) restores
        // both; verified over 20000 draws, none outside the interval.
        r.override("random", "(random() / 18446744073709551616.0 + 0.5)");
        r.override("to_hex",           "printf('%X', {0})");
        r.override("bit_length",       "length(CAST({0} AS BLOB)) * 8");
        r.override("starts_with",      "(substr({0}, 1, length({1})) = {1})");

        // date/time extraction via strftime
        r.override("year",   "CAST(strftime('%Y', {0}) AS INTEGER)");
        r.override("month",  "CAST(strftime('%m', {0}) AS INTEGER)");
        r.override("day",    "CAST(strftime('%d', {0}) AS INTEGER)");
        r.override("hour",   "CAST(strftime('%H', {0}) AS INTEGER)");
        r.override("minute", "CAST(strftime('%M', {0}) AS INTEGER)");
        r.override("second", "CAST(strftime('%S', {0}) AS INTEGER)");

        // type casts: SQLite has no DATE/TIME/TIMESTAMP/BOOLEAN types
        r.override("to_date",      "date({0})");
        r.override("to_time",      "time({0})");
        r.override("to_timestamp", "datetime({0})");
        r.override("to_boolean",   "({0} <> 0)");
        // Measured: SQLite's bare CAST truncates (1.7 -> 1) while duckdb, postgresql, oracle,
        // mariadb and trino round (1.7 -> 2). The same query therefore returned different numbers
        // here than everywhere else -- silently, because both results look plausible. The ROUND
        // wrapper evens that out, exactly as MssqlDialect applies it for the same reason. All
        // three widths need it: SQLite knows only INTEGER anyway, so the distinction between
        // SMALLINT, INTEGER and BIGINT is one of name alone here.
        // SQLite has no DECIMAL type: CAST(x AS DECIMAL(10,2)) parses, but type affinity
        // enforces no scale, so the cast did nothing and to_decimal(x, 10, 2) returned the
        // full value -- 1.618034 where every other dialect answered 1.62. The caller asked
        // for two decimals; ROUND is the only way to give them here. The precision argument
        // still cannot be enforced, but it is the scale that shows in the result.
        r.override("to_decimal",   "ROUND({0}, {2})");
        r.override("to_integer",   "CAST(ROUND({0}, 0) AS INTEGER)");
        r.override("to_bigint",    "CAST(ROUND({0}, 0) AS INTEGER)");
        r.override("to_smallint",  "CAST(ROUND({0}, 0) AS INTEGER)");

        // date construction / truncation / boundaries via date() modifiers
        r.override("make_date",   "date(printf('%04d-%02d-%02d', {0}, {1}, {2}))");
        // date_trunc was `date({1}, 'start of ' || {0})`, and SQLite's 'start of' modifier knows
        // exactly three units: day, month and year (measured). Every other unit made date() return
        // NULL — no error, just an empty column, while the same query answered correctly on the
        // other seven dialects. 'quarter' was the one that mattered, because quarter_begin and
        // quarter_end are built on it. Switching on the literal unit the way MariaDB, Oracle and
        // SQL Server already do turns an unsupported unit into a named failure instead.
        r.register(new FunctionDefinition("date_trunc", ReturnTypes.ARG1) {
            @Override
            protected String renderBody(SqlSelectRenderer renderer, Function function, int indent) {
                String unit = renderer.toSql(function.getArguments().get(0), indent).replace("'", "");
                String d = renderer.toSql(function.getArguments().get(1), indent);
                return switch (unit) {
                    // No 'start of hour' modifier in SQLite -- strftime and back is the way.
                    case "minute"  -> "datetime(strftime('%Y-%m-%d %H:%M:00', " + d + "))";
                    case "hour"    -> "datetime(strftime('%Y-%m-%d %H:00:00', " + d + "))";
                    case "day"     -> "datetime(" + d + ", 'start of day')";
                    case "month"   -> "date(" + d + ", 'start of month')";
                    case "quarter" -> quarterBegin(d);
                    case "year"    -> "date(" + d + ", 'start of year')";
                    default -> throw new UnsupportedOperationException(
                            "date_trunc unit not supported by SQLite: " + unit);
                };
            }
        }.args(FunctionArg.arg("unit", CoreTypeFamily.TEXT), FunctionArg.arg("value", Families.TEMPORAL)));
        r.override("minute_begin", "datetime(strftime('%Y-%m-%d %H:%M:00', {0}))");
        r.override("hour_begin",   "datetime(strftime('%Y-%m-%d %H:00:00', {0}))");
        r.override("day_begin",   "datetime({0}, 'start of day')");
        r.override("month_begin", "date({0}, 'start of month')");
        r.override("year_begin",  "date({0}, 'start of year')");
        r.override("month_end",   "date({0}, 'start of month', '+1 month', '-1 day')");
        // SQLite spells the row-wise forms max()/min(); with 2+ args they are scalar, not aggregate.
        // SQLite's trunc() takes one argument only; scale is scaled-cast-unscaled.
        r.override("trunc", 2, "(CAST({0} * pow(10, {1}) AS INTEGER) / pow(10, {1}))");
        r.override("greatest", "max({*})");
        r.override("least",    "min({*})");
        r.override("week_begin",  "date({0}, '-6 days', 'weekday 1')");
        r.override("week_end",    "date({0}, 'weekday 0')");
        r.override("year_end",    "date({0}, 'start of year', '+1 year', '-1 day')");
        // No year()/month() for the canonical template; strftime already packs the two, and its
        // '%Y%m' is zero-padded, so the text is the YYYYMM number and the cast is the only step.
        r.override("year_month",  "CAST(strftime('%Y%m', {0}) AS INTEGER)");
        // The canonical quarter_* and *_between templates are DuckDB-flavoured *literal SQL* —
        // date_trunc(), last_day(), date_diff(), age(), EXTRACT — none of which SQLite has. They
        // are not routed back through the registry, so overriding date_trunc alone does not reach
        // them; each needs its own rendering. Measured against the file: "no such function:
        // date_diff", "no such function: date_trunc", and two syntax errors on INTERVAL and
        // EXTRACT. Nothing could execute, and quarter_begin/quarter_end/years_between carried an
        // `ignore=sqlite` that read like a result difference rather than SQL the engine rejects.
        r.override("quarter_begin", quarterBegin("{0}"));
        r.override("quarter_end",   "date({0}, 'start of month', printf('%+d months', "
                + "3 - ((CAST(strftime('%m', {0}) AS INTEGER) - 1) % 3)), '-1 day')");
        // Distances follow the documented reference, java.time's ChronoUnit.between: whole units,
        // truncated toward zero, so the sign correction differs per direction. Verified against
        // ChronoUnit for eight spanning pairs including both negative directions.
        // complete days inside the interval (24-hour periods), not midnights crossed — measured, the two readings split the engines five to three. The date() wrappers were what made this count boundaries; julianday on the full
        // timestamp gives fractional days, and CAST to INTEGER truncates toward zero.
        r.override("days_between",   "CAST(julianday({1}) - julianday({0}) AS INTEGER)");
        r.override("months_between", truncatedDistance(
                "((CAST(strftime('%Y', {1}) AS INTEGER) * 12 + CAST(strftime('%m', {1}) AS INTEGER)) "
                        + "- (CAST(strftime('%Y', {0}) AS INTEGER) * 12 + CAST(strftime('%m', {0}) AS INTEGER)))",
                "CAST(strftime('%d', {1}) AS INTEGER)", "CAST(strftime('%d', {0}) AS INTEGER)"));
        r.override("years_between", truncatedDistance(
                "(CAST(strftime('%Y', {1}) AS INTEGER) - CAST(strftime('%Y', {0}) AS INTEGER))",
                "strftime('%m-%d', {1})", "strftime('%m-%d', {0})"));
        r.override("day_add",     "date({0}, printf('%+d days', {1}))");
        // month/year shifts clamp to month-end — SQLite '+N months' overflows (2022-10-31 +1mo → 12-01).
        r.override("month_add",   "min(date({0}, printf('%+d months', {1})), "
                + "date({0}, 'start of month', printf('%+d months', {1} + 1), '-1 day'))");
        r.override("year_add",    "min(date({0}, printf('%+d years', {1})), "
                + "date({0}, 'start of month', printf('%+d months', {1} * 12 + 1), '-1 day'))");

        // to_char via strftime (format mask translated to strftime tokens)
        r.register(new FunctionDefinition("to_char", ReturnTypes.TEXT) {
            @Override
            protected String renderBody(SqlSelectRenderer renderer, Function function, int indent) {
                return "strftime(" + sqliteFormat(renderer.toSql(function.getArguments().get(1), indent))
                        + ", " + renderer.toSql(function.getArguments().get(0), indent) + ")";
            }
        }.args(FunctionArg.arg("value", Families.ANY), FunctionArg.arg("format", CoreTypeFamily.TEXT)));

        // no regexp, hashing, padding, translation or time-construction in core SQLite
        for (String fn : List.of(
                "regexp_like", "regexp_count", "regexp_substr", "regexp_replace",
                "md5", "translate", "split_part", "repeat", "overlay", "lpad", "rpad",
                "to_number", "initcap", "make_time", "make_timestamp",
                // no strptime in core SQLite: there is no mask-based parse at all
                "parse_date", "parse_time", "parse_timestamp")) {
            r.unsupported(fn);
        }
        // SQLite has no time-zone database, so it is the one dialect of the eight that cannot
        // convert between named zones. That was already true, but it was expressed by
        // SqlDialect.zoneShiftTimestamp's default *throwing* at render time: a bare
        // KorykiaiException with no position, which no violation golden recorded and which the
        // harness could only recognise by matching on the message text. Declaring the two
        // functions puts them on the same footing as the fourteen above — a located UNSUPPORTED
        // violation, and the fixture is skipped from the catalog rather than by a hand-written
        // ignore= marker.
        r.unsupported("at_zone");
        r.unsupported("to_utc");
        return r;
    }

    /**
     * First day of {@code value}'s quarter: step back to the start of the month, then back by the
     * month's offset within its quarter. SQLite has no 'start of quarter' modifier.
     */
    private static String quarterBegin(String value) {
        return "date(" + value + ", 'start of month', printf('%+d months', "
                + "-((CAST(strftime('%m', " + value + ") AS INTEGER) - 1) % 3)))";
    }

    /**
     * {@code ChronoUnit.between} truncates toward zero, so a span that is not a whole number of
     * units loses its fractional part in the direction of the sign — the correction is one unit
     * <em>down</em> when counting forward and one <em>up</em> when counting backward. Java gets this
     * from integer division on a packed value; SQLite has no such packing, so the two cases are
     * spelled out. {@code raw} is the difference in whole units ignoring the finer field, which
     * {@code endPart} and {@code startPart} then compare.
     */
    private static String truncatedDistance(String raw, String endPart, String startPart) {
        return "(CASE WHEN " + raw + " > 0 AND " + endPart + " < " + startPart + " THEN " + raw + " - 1"
                + " WHEN " + raw + " < 0 AND " + endPart + " > " + startPart + " THEN " + raw + " + 1"
                + " ELSE " + raw + " END)";
    }

    /** Translate a canonical KQL format literal to SQLite strftime tokens; non-literals pass through. */
    private static String sqliteFormat(String rendered) {
        return FormatMask.translate(rendered, FormatMask.STRFTIME);
    }

    // SQLite has no date/time types and no ANSI typed literals (DATE '...', etc.).
    // Temporal values live as ISO-8601 TEXT, so emit plain string literals.
    @Override
    public String dateLiteral(LocalDate date) {
        return "'" + date + "'";
    }

    @Override
    public String timeLiteral(LocalTime time) {
        return "'" + time + "'";
    }

    @Override
    public String timestampLiteral(LocalDateTime dateTime) {
        // stored as 'yyyy-MM-dd HH:mm:ss' (space), not LocalDateTime's 'T' separator
        return "'" + dateTime.toString().replace('T', ' ') + "'";
    }

    // SQLite uses LIMIT n, not the ANSI FETCH FIRST; no ORDER BY requirement.
    @Override
    public String limitClause(int limit, boolean hasOrderBy, int indent) {
        return Identifier.indent(indent) + "LIMIT " + limit + SqlRenderer.NL;
    }

    // TIME(seconds-from-midnight) output + arithmetic render raw integers (SqlDialect default);
    // JdbcDatabase#read decodes the integer to a LocalTime.

    @Override
    public String renderEncodedArithmetic(SqlSelectRenderer renderer, String operator,
            Expression left, TypeDescriptor leftType, Expression right, TypeDescriptor rightType, int indent) {
        return renderEncodedArithmetic(renderer, operator, renderer.toSql(left, indent), leftType, right, rightType, indent);
    }

    /**
     * SQLite date/time arithmetic via {@code date()/datetime()} modifiers (SQLite has no INTERVAL).
     * <ul>
     *   <li>A DATE_FROM_EPOCH_DAY column is the day count since 1970-01-01.</li>
     *   <li>Year/month/quarter are summed to a month total and applied with <em>end-of-month
     *       clamping</em> — SQLite's {@code '+N months'} overflows (2025-12-31 +2mo → 2027-03-03),
     *       so clamp with {@code min(date(base,'+N months'), last-day-of-target-month)} to match the
     *       model. Day/clock parts are plain modifiers; {@code datetime()} is used once a clock part
     *       is present so the result carries a time.</li>
     * </ul>
     */
    /** SQLite integer division truncates. */
    @Override
    public String castToDecimal(String sql) {
        return "CAST(" + sql + " AS REAL)";
    }

    /**
     * SQLite casts the dividend unconditionally, where the other engines only need it when both
     * operands are declared integers. SQLite is dynamically typed: a DECIMAL-declared column holding
     * {@code 2} is stored as an integer, so whether {@code /} truncates depends on the <em>row</em>
     * rather than on the schema. Measured — restricting the cast to declared-integer operands turned
     * a unit cost of 0.1 into 0.0 on the northwind order_details. The declared family cannot decide
     * this here, so it is not consulted.
     */
    @Override
    public String renderDivision(String leftSql, TypeDescriptor leftType,
            String rightSql, TypeDescriptor rightType, boolean guardZero) {
        return castToDecimal(leftSql) + " / " + divisor(rightSql, guardZero);
    }

    /**
     * SQLite has no date type: a date is TEXT, and {@code -} coerces it to its leading integer, so
     * the bare operator returns 0 for every pair of dates in the same year. julianday() is the only
     * honest difference.
     */
    @Override
    public String dateDiffDays(String leftSql, String rightSql) {
        return "CAST(julianday(" + leftSql + ") - julianday(" + rightSql + ") AS INTEGER)";
    }

    @Override
    public String renderEncodedArithmetic(SqlSelectRenderer renderer, String operator,
            String leftSql, TypeDescriptor leftType, Expression right, TypeDescriptor rightType, int indent) {
        String diff = renderTemporalDiff(renderer, operator, leftSql, leftType, right, rightType, indent);
        if (diff != null) {
            return diff;
        }
        Optional<String> time = TimeEncodings.secondsArithmetic(renderer, leftSql, leftType, operator, right, indent);
        if (time.isPresent()) {
            return time.get();
        }
        Duration dur = right.getDuration();
        if (dur == null) {
            return leftSql + " " + operator + " " + renderer.toSql(right, indent);
        }

        // Base time-value: an epoch-day column materializes to a date; an EPOCH timestamp to a datetime;
        // a plain date column is used as-is (the enclosing date()/datetime() normalizes it).
        String base = leftType != null && CoreTypeEncoding.DATE_FROM_EPOCH_DAY.equals(leftType.getTypeEncoding())
                ? "date('1970-01-01', '+' || " + leftSql + " || ' days')"
                : materializeEpochTimestampLeft(leftSql, leftType);

        // A TIMESTAMP base (e.g. an EPOCH-materialized column, or a wall-clock timestamp) keeps its
        // time-of-day even for a date-only shift, so render through datetime() not date().
        boolean timestampBase = leftType != null
                && CoreTypeFamily.TIMESTAMP.equals(leftType.getTypeFamily());

        int sign = "-".equals(operator) ? -1 : 1;
        long months = 0;
        boolean hasClock = false;
        List<String> mods = new ArrayList<>();
        for (Duration.Component c : dur.getComponents()) {
            long v = sign * c.value();
            switch (c.unit()) {
                case YEAR        -> months += v * 12;
                case QUARTAL     -> months += v * 3;
                case MONTH       -> months += v;
                case WEEK        -> mods.add(modifier(v * 7, "days"));
                case DAY         -> mods.add(modifier(v, "days"));
                case HOUR        -> { mods.add(modifier(v, "hours"));   hasClock = true; }
                case MINUTE      -> { mods.add(modifier(v, "minutes")); hasClock = true; }
                case SECOND      -> { mods.add(modifier(v, "seconds")); hasClock = true; }
                case MILLISECOND -> { mods.add("'" + (v >= 0 ? "+" : "") + (v / 1000.0) + " seconds'"); hasClock = true; }
            }
        }

        // datetime() (not date()) when a clock part follows OR the base is a timestamp, so the time-of-day
        // is preserved; date() keeps a pure-date result a DATE for a date base + date-only duration.
        boolean wantTime = hasClock || timestampBase;

        String afterMonths = base;
        if (months != 0) {
            String fn = wantTime ? "datetime(" : "date(";
            String shifted = fn + base + ", " + monthsModifier(months) + ")";
            String lastDay = fn + base + ", 'start of month', " + monthsModifier(months + 1) + ", '-1 day')";
            afterMonths = "min(" + shifted + ", " + lastDay + ")";   // clamp to month-end
        }

        if (mods.isEmpty()) {
            return afterMonths;
        }
        return (wantTime ? "datetime(" : "date(") + afterMonths + ", " + String.join(", ", mods) + ")";
    }

    /**
     * SQLite has no {@code EXTRACT(EPOCH ...)}; {@code strftime('%s', ...)} reads epoch-seconds (assuming
     * UTC). An INSTANT is stored as text with a {@code +00} suffix that {@code strftime} cannot parse
     * (only {@code Z} / {@code ±HH:MM}), so normalize it to {@code Z} first.
     */
    @Override
    public String timestampToEpochSeconds(String expr, boolean instant) {
        String text = instant ? "replace(" + expr + ", '+00', 'Z')" : expr;
        return "CAST(strftime('%s', " + text + ") AS INTEGER)";
    }

    /** SQLite has no {@code make_timestamp}; {@code datetime(seconds, 'unixepoch')} → ISO-text TIMESTAMP (UTC). */
    @Override
    public String epochToTimestamp(String expr, java.time.temporal.ChronoUnit unit) {
        return "datetime(" + SqlDialect.secondsFromEpoch(expr, unit) + ", 'unixepoch')";
    }

    private static String modifier(long value, String unit) {
        return "'" + (value >= 0 ? "+" : "") + value + " " + unit + "'";
    }

    private static String monthsModifier(long months) {
        return "'" + (months >= 0 ? "+" : "") + months + " months'";
    }


    /**
     * SQLite has neither {@code MOD} nor {@code EXTRACT}, and no TIME type: {@code %} for modulo,
     * integer {@code /} already truncates, and a text time is split with {@code substr}.
     */
    @Override
    public String timeColumnAsSeconds(String columnSql, TypeDescriptor timeType) {
        var enc = timeType != null ? timeType.getTypeEncoding() : null;
        if (CoreTypeEncoding.TIME_FROM_INTEGER.equals(enc)) {
            return "(" + columnSql + " / 10000) * 3600"
                 + " + ((" + columnSql + " / 100) % 100) * 60"
                 + " + (" + columnSql + " % 100)";
        }
        if (CoreTypeEncoding.TIME_FROM_STRING.equals(enc)) {
            return "(CAST(substr(" + columnSql + ", 1, 2) AS INTEGER) * 3600"
                 + " + CAST(substr(" + columnSql + ", 4, 2) AS INTEGER) * 60"
                 + " + CAST(substr(" + columnSql + ", 7, 2) AS INTEGER))";
        }
        return SqlDialect.super.timeColumnAsSeconds(columnSql, timeType);
    }


    @Override
    public java.util.List<ai.koryki.iql.Collector<java.util.List<ai.koryki.iql.validate.Violation>>>
            validators(ai.koryki.iql.validate.ValidationContext context) {
        return java.util.List.of(new SqliteValidator(context));
    }

    /** SQLite has neither an interval type nor INTERVAL syntax; spans are modifier strings. */
    @Override
    public ai.koryki.iql.SqlDialect.IntervalSupport intervalSupport() {
        return ai.koryki.iql.SqlDialect.IntervalSupport.NONE;
    }
}
