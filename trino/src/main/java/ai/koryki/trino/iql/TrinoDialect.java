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
package ai.koryki.trino.iql;

import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.FunctionRenderer;
import ai.koryki.iql.functions.StandardFunctions;
import ai.koryki.iql.query.Duration;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.types.TimeEncodings;
import ai.koryki.catalog.schema.types.CoreTypeEncoding;
import ai.koryki.catalog.schema.types.CoreTypeFamily;
import ai.koryki.catalog.schema.types.TypeDescriptor;

import java.util.Optional;

public class TrinoDialect implements SqlDialect {

    public static final SqlDialect INSTANCE = new TrinoDialect();

    private static final java.time.format.DateTimeFormatter TIMESTAMP_FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TrinoDialect() {
    }

    /** Trino rejects the ISO/{@code T} form from {@code LocalDateTime.toString()}; emit space-separated with seconds. */
    @Override
    public String timestampLiteral(java.time.LocalDateTime dateTime) {
        return "TIMESTAMP '" + dateTime.format(TIMESTAMP_FMT) + "'";
    }

    /** Wall-clock(zone) → model zone: {@code with_timezone} reads the naive value as the declared zone. */
    @Override
    public String wallClockToModelZone(String columnSql,
            ai.koryki.catalog.schema.types.WallClockEncoding enc, java.time.ZoneId modelZone) {
        String decl = "'" + enc.getZone().getId() + "'";
        String model = "'" + modelZone.getId() + "'";
        if (ai.koryki.catalog.schema.types.CoreTypeFamily.DATE.equals(enc.family())) {
            return "CAST(with_timezone(CAST(" + columnSql + " AS TIMESTAMP), " + decl + ") AT TIME ZONE "
                    + model + " AS DATE)";
        }
        return "CAST(with_timezone(" + columnSql + ", " + decl + ") AT TIME ZONE " + model + " AS TIMESTAMP)";
    }

    @Override
    public String zoneShiftTimestamp(String valueSql, String fromZoneSql, String toZoneSql) {
        return "CAST(with_timezone(" + valueSql + ", " + fromZoneSql + ") AT TIME ZONE "
                + toZoneSql + " AS TIMESTAMP)";
    }

    @Override
    public FunctionRenderer getFunctionRenderer() {
        FunctionRegistry registry = StandardFunctions.registry();
        // to_text is the rolled-out, tested function: keep its dialect cast-type override.
        registry.overrideAll("to_text", "CAST({0} AS VARCHAR)");
        registry.override("months_between", "date_diff('month', {0}, {1})");
        registry.override("month_end",   "last_day_of_month({0})");
        registry.override("quarter_end", "last_day_of_month(date_trunc('quarter', {0}) + INTERVAL '2' MONTH)");
        registry.override("year_end",    "last_day_of_month(date_trunc('year', {0}) + INTERVAL '11' MONTH)");
        return registry;
    }

    // TIME(seconds-from-midnight) output + arithmetic render raw integers (SqlDialect default);
    // JdbcDatabase#read decodes the integer to a LocalTime.

    @Override
    public String renderEncodedArithmetic(SqlSelectRenderer renderer, String operator,
            Expression left, TypeDescriptor leftType, Expression right, TypeDescriptor rightType, int indent) {
        return renderEncodedArithmetic(renderer, operator, renderer.toSql(left, indent), leftType, right, rightType, indent);
    }

    /**
     * Trino date/time arithmetic. Overrides the default because:
     * <ul>
     *   <li>Trino has no QUARTER or WEEK interval qualifier (fold to MONTH*3 / DAY*7), and no
     *       MILLISECOND — each duration component is chained on the date as {@code INTERVAL 'n' UNIT}
     *       (Trino clamps month-end, so chaining matches the model).</li>
     *   <li>{@code DATE + INTEGER} is not valid, so a DATE_FROM_EPOCH_DAY column adds its days via
     *       {@code date_add('day', <col>, DATE '1970-01-01')}.</li>
     * </ul>
     */
    @Override
    public String renderEncodedArithmetic(SqlSelectRenderer renderer, String operator,
            String leftSql, TypeDescriptor leftType, Expression right, TypeDescriptor rightType, int indent) {
        String diff = renderTimestampDiff(renderer, operator, leftSql, leftType, right, rightType, indent);
        if (diff != null) {
            return diff;
        }
        Optional<String> time = TimeEncodings.secondsArithmetic(renderer, leftSql, leftType, operator, right, indent);
        if (time.isPresent()) {
            return time.get();
        }
        if (leftType != null && CoreTypeEncoding.DATE_FROM_EPOCH_DAY.equals(leftType.getTypeEncoding())) {
            leftSql = "date_add('day', " + leftSql + ", DATE '1970-01-01')";
        }
        leftSql = materializeEpochTimestampLeft(leftSql, leftType);
        Duration dur = right.getDuration();
        if (dur != null) {
            // Trino refuses to add a clock interval (hour/minute/second) to a DATE — promote the
            // date base to TIMESTAMP first when the duration carries any clock component.
            boolean date = leftType != null && CoreTypeFamily.DATE.equals(leftType.getTypeFamily());
            boolean hasClock = dur.getComponents().stream().anyMatch(c -> switch (c.unit()) {
                case HOUR, MINUTE, SECOND, MILLISECOND -> true;
                default -> false;
            });
            if (date && hasClock) {
                leftSql = "CAST(" + leftSql + " AS TIMESTAMP)";
            }
            StringBuilder sb = new StringBuilder(leftSql);
            for (Duration.Component c : dur.getComponents()) {
                sb.append(' ').append(operator).append(' ').append(intervalComponent(c));
            }
            return sb.toString();
        }
        return leftSql + " " + operator + " " + renderer.toSql(right, indent);
    }

    /** Trino has no {@code EXTRACT(EPOCH ...)}; {@code to_unixtime} reads epoch-seconds (UTC session). */
    @Override
    public String timestampToEpochSeconds(String expr, boolean instant) {
        return "CAST(to_unixtime(" + expr + ") AS BIGINT)";
    }

    /** Trino has no {@code make_timestamp}; {@code from_unixtime} is zone-aware, so CAST to a naive TIMESTAMP. */
    @Override
    public String epochToTimestamp(String expr, java.time.temporal.ChronoUnit unit) {
        return "CAST(from_unixtime(" + SqlDialect.secondsFromEpoch(expr, unit) + ") AS TIMESTAMP)";
    }

    /** One duration component as a Trino {@code INTERVAL 'n' UNIT} (no QUARTER/WEEK: fold to MONTH/DAY). */
    private static String intervalComponent(Duration.Component c) {
        return switch (c.unit()) {
            case YEAR        -> "INTERVAL '" + c.value() + "' YEAR";
            case QUARTAL     -> "INTERVAL '" + (c.value() * 3) + "' MONTH";
            case MONTH       -> "INTERVAL '" + c.value() + "' MONTH";
            case WEEK        -> "INTERVAL '" + (c.value() * 7) + "' DAY";
            case DAY         -> "INTERVAL '" + c.value() + "' DAY";
            case HOUR        -> "INTERVAL '" + c.value() + "' HOUR";
            case MINUTE      -> "INTERVAL '" + c.value() + "' MINUTE";
            case SECOND      -> "INTERVAL '" + c.value() + "' SECOND";
            case MILLISECOND -> "INTERVAL '" + c.value() + "' MILLISECOND";
        };
    }

}
