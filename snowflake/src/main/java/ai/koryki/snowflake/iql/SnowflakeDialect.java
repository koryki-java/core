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
package ai.koryki.snowflake.iql;

import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.WallClockEncoding;
import ai.koryki.catalog.types.CoreTypeEncoding;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionKind;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.FunctionRenderer;
import ai.koryki.iql.functions.ReturnTypes;
import ai.koryki.iql.functions.StandardFunctions;
import ai.koryki.iql.query.Expression;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.iql.typing.TimeEncodings;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class SnowflakeDialect implements SqlDialect {

    public static final SqlDialect INSTANCE = new SnowflakeDialect();

    private static final DateTimeFormatter TIME_FMT      = DateTimeFormatter.ofPattern("HH:mm:ss").withLocale(Locale.ROOT);

    private SnowflakeDialect() {
    }

    /** Wall-clock(zone) → model zone via 3-arg {@code CONVERT_TIMEZONE(src, tgt, naive_ts)} → NTZ. */
    @Override
    public String wallClockToModelZone(String columnSql,
                                       WallClockEncoding enc, java.time.ZoneId modelZone) {
        String decl = "'" + enc.getZone().getId() + "'";
        String model = "'" + modelZone.getId() + "'";
        if (CoreTypeFamily.DATE.equals(enc.family())) {
            return "CAST(CONVERT_TIMEZONE(" + decl + ", " + model + ", CAST(" + columnSql
                    + " AS TIMESTAMP_NTZ)) AS DATE)";
        }
        return "CONVERT_TIMEZONE(" + decl + ", " + model + ", " + columnSql + ")";
    }

    @Override
    public String zoneShiftTimestamp(String valueSql, String fromZoneSql, String toZoneSql) {
        return "CONVERT_TIMEZONE(" + fromZoneSql + ", " + toZoneSql + ", " + valueSql + ")";
    }

    private static final FunctionRenderer FUNCTION_RENDERER = buildFunctionRenderer();

    @Override
    public FunctionRenderer getFunctionRenderer() {
        return FUNCTION_RENDERER;
    }

    /**
     * Doubles backslashes. Snowflake reads them as escapes inside string literals, like MariaDB and
     * unlike the SQL standard. Measured on {@code expression/backslash_literal}:
     * {@code regexp_substr(phone, '\d+')} returned nothing here while duckdb, postgresql, oracle
     * and trino returned {@code 030} -- no error, just an empty field. Every string is affected; a
     * regex only makes it most visible.
     */
    @Override
    public String textLiteral(String quoted) {
        return quoted.replace("\\", "\\\\");
    }

    private static FunctionRenderer buildFunctionRenderer() {
        FunctionRegistry registry = StandardFunctions.registry();

        // concat and concat_ws skip NULL arguments -- so documented, and so the other seven
        // dialects behave. Snowflake does not: its documentation says explicitly "the Snowflake
        // CONCAT_WS function doesn't skip NULL values. If any argument is NULL, the function
        // returns NULL", and CONCAT behaves the same. Measured, both columns came back empty where
        // all others gave 'ab' and 'a-b'. ARRAY_CONSTRUCT_COMPACT discards NULL elements and
        // thereby restores exactly the skipping semantics.
        registry.override("concat",    "ARRAY_TO_STRING(ARRAY_CONSTRUCT_COMPACT({*}), '')");
        registry.override("concat_ws", "ARRAY_TO_STRING(ARRAY_CONSTRUCT_COMPACT({1*}), {0})");

        // No listagg alias: the catalog's string_agg already renders as LISTAGG here, portably.

        // log2 → LOG(2, x)

        // PostgreSQL-chapter functions: Snowflake spellings

        // Distances and Rolling (TEMPORAL.md). DATEDIFF counts boundary
        // crossings; months_between corrects to whole completed units like the
        // mssql overlay. Snowflake LAST_DAY takes a part, so quarter_end/year_end
        // are direct (the canonical INTERVAL n MONTH is not valid Snowflake syntax).
        // month_end inherits the canonical last_day({0}) template.
        // random() is documented as 0.0 <= x < 1.0. Snowflake has the name but, like SQLite,
        // not the contract: RANDOM() returns a signed 64-bit integer. UNIFORM over floats draws
        // from the unit interval; verified against a live account by random_contract, which
        // checks both halves of the promise -- the range and a fresh draw per row.
        registry.override("random", "UNIFORM(0::float, 1::float, RANDOM())");
        // complete days inside the interval (24-hour periods), not midnights crossed — measured, the two readings split the engines five to three
        registry.override("days_between",   "TRUNC(DATEDIFF(second, {0}, {1}) / 86400)");
        registry.override("months_between", "(DATEDIFF(month, {0}, {1}) - CASE WHEN DAY({1}) < DAY({0}) THEN 1 ELSE 0 END)");
        registry.override("week_begin",     "DATEADD(day, -(DAYOFWEEKISO({0}) - 1), {0})");
        registry.override("week_end",       "DATEADD(day, 7 - DAYOFWEEKISO({0}), {0})");
        registry.override("quarter_end",    "LAST_DAY({0}, 'quarter')");
        registry.override("year_end",       "LAST_DAY({0}, 'year')");

        // no Snowflake equivalent

        // parse_date/time/timestamp(value, format) → TO_DATE / TO_TIME / TO_TIMESTAMP
        registry.register(StandardFunctions.parseTwoArg("parse_date",      ReturnTypes.DATE,      "TO_DATE"));
        registry.register(StandardFunctions.parseTwoArg("parse_time",      ReturnTypes.TIME,      "TO_TIME"));
        registry.register(StandardFunctions.parseTwoArg("parse_timestamp", ReturnTypes.TIMESTAMP, "TO_TIMESTAMP"));

        // string functions (Snowflake spellings)
        registry.override("char_length",      "LENGTH({0})");
        registry.override("starts_with",      "STARTSWITH({0}, {1})");
        registry.override("overlay",          "INSERT({0}, {2}, {3}, {1})");
        registry.override("string_agg", 2, "LISTAGG({0}, {1})");
        registry.override("string_agg", 3, "LISTAGG({0}, {1}) WITHIN GROUP (ORDER BY {2})");
        registry.windowUnsupported("string_agg");   // LISTAGG OVER () accepts only PARTITION BY, no window ORDER
        registry.override("regexp_like",      "(REGEXP_INSTR({0}, {1}) > 0)");  // Snowflake REGEXP_LIKE is full-match

        // math / date-time
        // Snowflake has no LOG10 -- the same two-argument form as Oracle. Measured once the
        // database was reachable again: "SQL compilation error: Unknown function LOG10."
        registry.override("log10",     "LOG(10, {0})");
        registry.override("make_date", "DATE_FROM_PARTS({0}, {1}, {2})");
        registry.override("make_time", "TIME_FROM_PARTS({0}, {1}, {2})");
        registry.override("make_timestamp", "TIMESTAMP_NTZ_FROM_PARTS({0}, {1}, {2}, {3}, {4}, {5})");
        registry.override("day_add",   "DATEADD('day', {1}, {0})");
        registry.override("month_add", "DATEADD('month', {1}, {0})");
        registry.override("year_add",  "DATEADD('year', {1}, {0})");
        registry.override("years_between", "FLOOR(MONTHS_BETWEEN({1}, {0}) / 12)");  // canonical uses PG age()

        // WEEK()/DAYOFWEEK() follow the WEEK_START and WEEK_OF_YEAR_POLICY session parameters;
        // the ISO variants do not, so the answer does not depend on how the account is configured.
        registry.override("week",      "WEEKISO({0})");
        registry.override("dayofweek", "DAYOFWEEKISO({0})");

        registry.unsupported("to_hex");   // Snowflake has no number → hex conversion

        // Measured against the account: Snowflake takes the comma list and rejects the row
        // constructor with a compile error, so it sides with MariaDB rather than with the base.
        registry.override("count_distinct", 2, "COUNT(DISTINCT {*})");

        return registry;
    }

    @Override
    public String timestampLiteral(LocalDateTime dateTime) {
        return "TIMESTAMP '" + SqlDialect.plainTimestamp(dateTime) + "'";
    }

    @Override
    public String timeLiteral(LocalTime time) {
        return "TIME '" + SqlDialect.plainTime(time) + "'";
    }

    @Override
    public String mapSetOperator(String operator) {
        if ("MINUS".equals(operator)) {
            return "EXCEPT";
        }
        return operator;
    }

    @Override
    public String renderComparisonOperand(SqlSelectRenderer renderer, Expression expression,
            TypeDescriptor leftType, TypeDescriptor rightType, int indent) {
        return TimeEncodings.secondsFromMidnightLiteral(leftType, expression)
                .orElseGet(() -> SqlDialect.super.renderComparisonOperand(renderer, expression, leftType, rightType, indent));
    }

    // TIME(seconds-from-midnight) output + arithmetic render raw integers (SqlDialect default);
    // JdbcDatabase#read decodes the integer to a LocalTime.

    @Override
    public String durationLiteral(ai.koryki.iql.query.Duration duration) {
        // Comma-separated components, and Snowflake applies them one after another -- so the
        // calendar units must be folded first or 2024-02-29 + 1y1mo clamps in between and
        // answers 2025-03-28 instead of 2025-03-29 (measured).
        return SqlDialect.combinedInterval(SqlDialect.calendarFolded(duration), ", ");
    }

    /** Snowflake has no {@code EXTRACT(EPOCH ...)}; {@code DATE_PART(EPOCH_SECOND, ...)} reads epoch-seconds. */
    @Override
    public String timestampToEpochSeconds(String expr, boolean instant) {
        return "DATE_PART(EPOCH_SECOND, " + expr + ")";
    }

    /** Snowflake has no {@code make_timestamp}; {@code TO_TIMESTAMP_NTZ(seconds, 0)} → naive TIMESTAMP. */
    @Override
    public String epochToTimestamp(String expr, java.time.temporal.ChronoUnit unit) {
        return "TO_TIMESTAMP_NTZ(" + SqlDialect.secondsFromEpoch(expr, unit) + ", 0)";
    }

    /**
     * Snowflake has no {@code EXTRACT(EPOCH FROM …)} for a TIME; the hour/minute/second accessors
     * are the portable route, as on Trino.
     */
    @Override
    public String timeColumnAsSeconds(String columnSql, TypeDescriptor timeType) {
        var enc = timeType != null ? timeType.getTypeEncoding() : null;
        if (CoreTypeEncoding.TIME_FROM_STRING.equals(enc)) {
            String t = "CAST(" + columnSql + " AS TIME)";
            return "HOUR(" + t + ") * 3600 + MINUTE(" + t + ") * 60 + SECOND(" + t + ")";
        }
        return SqlDialect.super.timeColumnAsSeconds(columnSql, timeType);
    }


    /**
     * Snowflake has no interval data type: {@code durationLiteral} produces
     * {@code INTERVAL '1 hour, 2 minute'}, which is valid only inside date arithmetic. As a value
     * the engine answers "interval literal is not supported in this form" — measured, not inferred
     * from its design.
     */
    @Override
    public ai.koryki.iql.SqlDialect.IntervalSupport intervalSupport() {
        return ai.koryki.iql.SqlDialect.IntervalSupport.NONE;
    }
}
