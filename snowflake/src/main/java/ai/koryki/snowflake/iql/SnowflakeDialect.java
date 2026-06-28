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

import ai.koryki.catalog.schema.types.CoreTypeEncoding;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.functions.ConditionalFunctionDefinition;
import ai.koryki.iql.functions.FunctionArg;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionKind;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.FunctionRenderer;
import ai.koryki.iql.functions.ReturnTypes;
import ai.koryki.iql.functions.StandardFunctions;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
import ai.koryki.catalog.schema.types.TypeDescriptor;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class SnowflakeDialect implements SqlDialect {

    public static final SqlDialect INSTANCE = new SnowflakeDialect();

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withLocale(Locale.ROOT);
    private static final DateTimeFormatter TIME_FMT      = DateTimeFormatter.ofPattern("HH:mm:ss").withLocale(Locale.ROOT);

    private SnowflakeDialect() {
    }

    /** Wall-clock(zone) → model zone via 3-arg {@code CONVERT_TIMEZONE(src, tgt, naive_ts)} → NTZ. */
    @Override
    public String wallClockToModelZone(String columnSql,
            ai.koryki.catalog.schema.types.WallClockEncoding enc, java.time.ZoneId modelZone) {
        String decl = "'" + enc.getZone().getId() + "'";
        String model = "'" + modelZone.getId() + "'";
        if (ai.koryki.catalog.schema.types.CoreTypeFamily.DATE.equals(enc.family())) {
            return "CAST(CONVERT_TIMEZONE(" + decl + ", " + model + ", CAST(" + columnSql
                    + " AS TIMESTAMP_NTZ)) AS DATE)";
        }
        return "CONVERT_TIMEZONE(" + decl + ", " + model + ", " + columnSql + ")";
    }

    @Override
    public String zoneShiftTimestamp(String valueSql, String fromZoneSql, String toZoneSql) {
        return "CONVERT_TIMEZONE(" + fromZoneSql + ", " + toZoneSql + ", " + valueSql + ")";
    }

    @Override
    public FunctionRenderer getFunctionRenderer() {
        FunctionRegistry registry = StandardFunctions.registry();

        // string_agg → LISTAGG
        registry.register(new FunctionDefinition("listagg", ReturnTypes.TEXT, FunctionKind.AGGREGATE));

        // log2 → LOG(2, x)

        // PostgreSQL-chapter functions: Snowflake spellings

        // Distances and Rolling (TEMPORAL.md). DATEDIFF counts boundary
        // crossings; months_between corrects to whole completed units like the
        // mssql overlay. Snowflake LAST_DAY takes a part, so quarter_end/year_end
        // are direct (the canonical INTERVAL n MONTH is not valid Snowflake syntax).
        // month_end inherits the canonical last_day({0}) template.
        registry.override("days_between",   "DATEDIFF(day, {0}, {1})");
        registry.override("months_between", "(DATEDIFF(month, {0}, {1}) - CASE WHEN DAY({1}) < DAY({0}) THEN 1 ELSE 0 END)");
        registry.override("quarter_end",    "LAST_DAY({0}, 'quarter')");
        registry.override("year_end",       "LAST_DAY({0}, 'year')");

        // no Snowflake equivalent

        // parse_date/time/timestamp(value, format) → TO_DATE / TO_TIME / TO_TIMESTAMP
        registry.register(StandardFunctions.parseTwoArg("parse_date",      ReturnTypes.DATE,      "TO_DATE"));
        registry.register(StandardFunctions.parseTwoArg("parse_time",      ReturnTypes.TIME,      "TO_TIME"));
        registry.register(StandardFunctions.parseTwoArg("parse_timestamp", ReturnTypes.TIMESTAMP, "TO_TIMESTAMP"));


        return registry;
    }

    @Override
    public String timestampLiteral(LocalDateTime dateTime) {
        return "TIMESTAMP '" + dateTime.format(TIMESTAMP_FMT) + "'";
    }

    @Override
    public String timeLiteral(LocalTime time) {
        return "TIME '" + time.format(TIME_FMT) + "'";
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
        return ai.koryki.iql.types.TimeEncodings.secondsFromMidnightLiteral(leftType, expression)
                .or(() -> ai.koryki.iql.types.TimeEncodings.reconcile(renderer, expression, leftType, rightType, indent))
                .orElseGet(() -> SqlDialect.super.renderComparisonOperand(renderer, expression, leftType, rightType, indent));
    }

    // TIME(seconds-from-midnight) output + arithmetic render raw integers (SqlDialect default);
    // JdbcDatabase#read decodes the integer to a LocalTime.

    @Override
    public String durationLiteral(ai.koryki.iql.query.Duration duration) {
        return SqlDialect.combinedInterval(duration, ", ");   // Snowflake requires comma-separated components
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
}
