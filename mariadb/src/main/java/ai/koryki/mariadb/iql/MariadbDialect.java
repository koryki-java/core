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
package ai.koryki.mariadb.iql;

import ai.koryki.catalog.schema.types.CoreTypeEncoding;
import ai.koryki.catalog.schema.types.CoreTypeFamily;
import ai.koryki.catalog.schema.types.TypeDescriptor;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.functions.*;
import ai.koryki.iql.query.Duration;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class MariadbDialect implements SqlDialect {

    public static final SqlDialect INSTANCE = new MariadbDialect();

    private MariadbDialect() {
    }

    /** Wall-clock(zone) → model zone via {@code CONVERT_TZ} (named zones; server tz tables must be loaded). */
    @Override
    public String wallClockToModelZone(String columnSql,
            ai.koryki.catalog.schema.types.WallClockEncoding enc, java.time.ZoneId modelZone) {
        String decl = "'" + enc.getZone().getId() + "'";
        String model = "'" + modelZone.getId() + "'";
        if (ai.koryki.catalog.schema.types.CoreTypeFamily.DATE.equals(enc.family())) {
            return "CAST(CONVERT_TZ(CAST(" + columnSql + " AS DATETIME), " + decl + ", " + model + ") AS DATE)";
        }
        return "CONVERT_TZ(" + columnSql + ", " + decl + ", " + model + ")";
    }

    @Override
    public String zoneShiftTimestamp(String valueSql, String fromZoneSql, String toZoneSql) {
        return "CONVERT_TZ(" + valueSql + ", " + fromZoneSql + ", " + toZoneSql + ")";
    }

    @Override
    public FunctionRenderer getFunctionRenderer() {
        FunctionRegistry registry = StandardFunctions.registry();
        // to_text is the rolled-out, tested function: keep its dialect cast-type override.
        registry.overrideAll("to_text", "CAST({0} AS CHAR)");
        registry.override("days_between",   "TIMESTAMPDIFF(DAY, {0}, {1})");
        registry.override("months_between", "TIMESTAMPDIFF(MONTH, {0}, {1})");
        registry.override("month_end",   "LAST_DAY({0})");
        registry.override("quarter_end", "LAST_DAY(MAKEDATE(YEAR({0}), 1) + INTERVAL (QUARTER({0}) * 3 - 1) MONTH)");
        registry.override("year_end",    "LAST_DAY(MAKEDATE(YEAR({0}), 1) + INTERVAL 11 MONTH)");
        return registry;
    }

    // MariaDB/MySQL use the trailing "WITH ROLLUP" modifier instead of the
    // standard-SQL "GROUP BY ROLLUP (...)" grouping-set syntax.
    @Override
    public String rollupPrefix() {
        return "";
    }

    @Override
    public String rollupSuffix() {
        return " WITH ROLLUP";
    }

    // TIME(seconds-from-midnight) output + arithmetic render raw integers (SqlDialect default);
    // JdbcDatabase#read decodes the integer to a LocalTime.

    @Override
    public String renderEncodedArithmetic(SqlSelectRenderer renderer, String operator,
            Expression left, TypeDescriptor leftType, Expression right, TypeDescriptor rightType, int indent) {
        return renderEncodedArithmetic(renderer, operator, renderer.toSql(left, indent), leftType, right, rightType, indent);
    }

    /**
     * MariaDB date/time arithmetic. Two MariaDB-specifics force an override of the default:
     * <ul>
     *   <li>It cannot add two INTERVALs together, so a multi-component duration must be
     *       <em>chained</em> on the date ({@code d - INTERVAL 1 DAY - INTERVAL 1 HOUR}), not
     *       wrapped as {@code d - (INTERVAL 1 DAY + INTERVAL 1 HOUR)}. Month-end is clamped by
     *       MariaDB itself, so chaining matches the model.</li>
     *   <li>{@code DATE + INT} is numeric (yyyymmdd + n), so a DATE_FROM_EPOCH_DAY column must add
     *       its days as {@code INTERVAL <col> DAY}, not a bare {@code +}.</li>
     * </ul>
     */
    @Override
    public String renderEncodedArithmetic(SqlSelectRenderer renderer, String operator,
            String leftSql, TypeDescriptor leftType, Expression right, TypeDescriptor rightType, int indent) {
        String diff = renderTimestampDiff(renderer, operator, leftSql, leftType, right, rightType, indent);
        if (diff != null) {
            return diff;
        }
        java.util.Optional<String> time = ai.koryki.iql.types.TimeEncodings
                .secondsArithmetic(renderer, leftSql, leftType, operator, right, indent);
        if (time.isPresent()) {
            return time.get();
        }
        if (leftType != null && CoreTypeEncoding.DATE_FROM_EPOCH_DAY.equals(leftType.getTypeEncoding())) {
            leftSql = "(DATE '1970-01-01' + INTERVAL " + leftSql + " DAY)";
        }
        leftSql = materializeEpochTimestampLeft(leftSql, leftType);
        Duration dur = right.getDuration();
        if (dur != null) {
            StringBuilder sb = new StringBuilder(leftSql);
            for (Duration.Component c : dur.getComponents()) {
                sb.append(' ').append(operator).append(' ').append(intervalComponent(c));
            }
            return sb.toString();
        }
        return leftSql + " " + operator + " " + renderer.toSql(right, indent);
    }

    /** MariaDB has no {@code EXTRACT(EPOCH ...)}; {@code UNIX_TIMESTAMP} reads epoch-seconds (UTC session). */
    @Override
    public String timestampToEpochSeconds(String expr, boolean instant) {
        return "UNIX_TIMESTAMP(" + expr + ")";
    }

    /** MariaDB has no {@code make_timestamp}; {@code FROM_UNIXTIME(seconds)} → DATETIME (UTC session). */
    @Override
    public String epochToTimestamp(String expr, java.time.temporal.ChronoUnit unit) {
        return "FROM_UNIXTIME(" + SqlDialect.secondsFromEpoch(expr, unit) + ")";
    }

    /** MariaDB uses {@code CONCAT} ({@code ||} is logical OR); it casts the numbers to text implicitly. */
    @Override
    public String pairText(String a, String b) {
        return "CONCAT(" + a + ", ';', " + b + ")";
    }

    /** One duration component as a MariaDB {@code INTERVAL '<value>' <UNIT>} (QUARTAL→QUARTER, ms→µs). */
    private static String intervalComponent(Duration.Component c) {
        return switch (c.unit()) {
            case MILLISECOND -> "INTERVAL '" + (c.value() * 1000) + "' MICROSECOND";
            case SECOND      -> "INTERVAL '" + c.value() + "' SECOND";
            case MINUTE      -> "INTERVAL '" + c.value() + "' MINUTE";
            case HOUR        -> "INTERVAL '" + c.value() + "' HOUR";
            case DAY         -> "INTERVAL '" + c.value() + "' DAY";
            case WEEK        -> "INTERVAL '" + c.value() + "' WEEK";
            case MONTH       -> "INTERVAL '" + c.value() + "' MONTH";
            case QUARTAL     -> "INTERVAL '" + c.value() + "' QUARTER";
            case YEAR        -> "INTERVAL '" + c.value() + "' YEAR";
        };
    }

}
