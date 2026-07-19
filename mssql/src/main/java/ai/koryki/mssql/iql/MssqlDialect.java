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
package ai.koryki.mssql.iql;

import ai.koryki.catalog.types.WallClockEncoding;
import ai.koryki.iql.Identifier;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.FunctionRenderer;
import ai.koryki.iql.functions.StandardFunctions;
import ai.koryki.iql.query.Duration;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.typing.TimeEncodings;
import ai.koryki.catalog.types.CoreTypeEncoding;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeDescriptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class MssqlDialect implements SqlDialect {

    public static final SqlDialect INSTANCE = new MssqlDialect();

    private MssqlDialect() {
    }

    /** SQL Server {@code AT TIME ZONE} uses Windows zone names, not IANA; map the names we support. */
    private static final java.util.Map<String, String> WINDOWS_ZONE = java.util.Map.ofEntries(
            java.util.Map.entry("UTC", "UTC"),
            java.util.Map.entry("Etc/UTC", "UTC"),
            java.util.Map.entry("America/New_York", "Eastern Standard Time"),
            java.util.Map.entry("America/Chicago", "Central Standard Time"),
            java.util.Map.entry("America/Denver", "Mountain Standard Time"),
            java.util.Map.entry("America/Los_Angeles", "Pacific Standard Time"),
            java.util.Map.entry("Europe/London", "GMT Standard Time"),
            java.util.Map.entry("Europe/Berlin", "W. Europe Standard Time"),
            java.util.Map.entry("Europe/Paris", "Romance Standard Time"),
            java.util.Map.entry("Asia/Tokyo", "Tokyo Standard Time"),
            java.util.Map.entry("Asia/Kolkata", "India Standard Time"),
            java.util.Map.entry("Australia/Sydney", "AUS Eastern Standard Time"));

    private static String windowsZone(java.time.ZoneId zone) {
        String win = WINDOWS_ZONE.get(zone.getId());
        if (win == null) {
            throw new ai.koryki.antlr.KorykiaiException("no Windows time-zone name mapped for '"
                    + zone.getId() + "' (SQL Server AT TIME ZONE requires Windows names)");
        }
        return win;
    }

    /** Wall-clock(zone) → model zone: {@code AT TIME ZONE} (Windows names) reads naive as declared, then shifts. */
    @Override
    public String wallClockToModelZone(String columnSql,
                                       WallClockEncoding enc, java.time.ZoneId modelZone) {
        String decl = "'" + windowsZone(enc.getZone()) + "'";
        String model = "'" + windowsZone(modelZone) + "'";
        String shifted = "(CAST(" + columnSql + " AS datetime2) AT TIME ZONE " + decl + ") AT TIME ZONE " + model;
        String target = CoreTypeFamily.DATE.equals(enc.family()) ? "DATE" : "datetime2";
        return "CAST((" + shifted + ") AS " + target + ")";
    }

    /** at_zone / to_utc: the zone args arrive SQL-quoted as IANA names; map each to its Windows name. */
    @Override
    public String zoneShiftTimestamp(String valueSql, String fromZoneSql, String toZoneSql) {
        String from = "'" + windowsZone(java.time.ZoneId.of(unquote(fromZoneSql))) + "'";
        String to = "'" + windowsZone(java.time.ZoneId.of(unquote(toZoneSql))) + "'";
        return "CAST(((CAST(" + valueSql + " AS datetime2) AT TIME ZONE " + from + ") AT TIME ZONE " + to
                + ") AS datetime2)";
    }

    private static String unquote(String s) {
        return s.length() >= 2 && s.startsWith("'") && s.endsWith("'") ? s.substring(1, s.length() - 1) : s;
    }

    private static final FunctionRenderer FUNCTION_RENDERER = buildFunctionRenderer();

    @Override
    public FunctionRenderer getFunctionRenderer() {
        return FUNCTION_RENDERER;
    }

    private static FunctionRenderer buildFunctionRenderer() {
        FunctionRegistry registry = StandardFunctions.registry();
        // to_text is the rolled-out, tested function: keep its dialect cast-type override.
        registry.overrideAll("to_text", "CAST({0} AS VARCHAR(MAX))");
        registry.override("ceil", "CEILING({*})");
        registry.override("substr", "SUBSTRING({*})");
        for (String lenName : java.util.List.of("length", "char_length", "character_length")) {
            registry.override(lenName, "LEN({*})");
        }
        registry.override("days_between", "DATEDIFF(day, {0}, {1})");
        registry.override("months_between", "(DATEDIFF(month, {0}, {1}) - CASE WHEN DAY({1}) < DAY({0}) THEN 1 ELSE 0 END)");
        registry.override("month_end",   "EOMONTH({0})");
        registry.override("quarter_end", "EOMONTH(DATEADD(QUARTER, DATEDIFF(QUARTER, 0, {0}), 0), 2)");
        registry.override("year_end",    "DATEFROMPARTS(YEAR({0}), 12, 31)");
        return registry;
    }

    // T-SQL has no ANSI typed literals (DATE '...', TIME '...', TIMESTAMP '...').
    // Emit explicit CASTs of ISO-8601 strings, which T-SQL parses unambiguously.
    @Override
    public String dateLiteral(LocalDate date) {
        return "CAST('" + date + "' AS DATE)";
    }

    @Override
    public String timeLiteral(LocalTime time) {
        return "CAST('" + time + "' AS TIME)";
    }

    // SQL Server DATETIME2 CAST requires full hh:mm:ss — LocalDateTime.toString() omits
    // seconds when zero (e.g. "T00:00"), which SQL Server rejects.
    private static final java.time.format.DateTimeFormatter DATETIME2_FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Override
    public String timestampLiteral(LocalDateTime dateTime) {
        return "CAST('" + dateTime.format(DATETIME2_FMT) + "' AS DATETIME2)";
    }

    private static final java.time.format.DateTimeFormatter DATETIMEOFFSET_FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx");

    /** SQL Server INSTANT columns are {@code DATETIMEOFFSET}; render the literal with an explicit offset. */
    @Override
    public String instantLiteral(java.time.Instant instant, java.time.ZoneId modelZone) {
        return "CAST('" + instant.atZone(modelZone).format(DATETIMEOFFSET_FMT) + "' AS datetimeoffset)";
    }

    // T-SQL has no standalone FETCH FIRST. Paging is OFFSET/FETCH, which requires an
    // ORDER BY — synthesize a dummy one (ORDER BY (SELECT NULL)) when the query has none.
    @Override
    public String limitClause(int limit, boolean hasOrderBy, int indent) {
        StringBuilder b = new StringBuilder();
        if (!hasOrderBy) {
            b.append(Identifier.indent(indent)).append("ORDER BY (SELECT NULL)").append(System.lineSeparator());
        }
        b.append(Identifier.indent(indent))
                .append("OFFSET 0 ROWS FETCH NEXT ").append(limit).append(" ROWS ONLY")
                .append(System.lineSeparator());
        return b.toString();
    }

    // TIME(seconds-from-midnight) output + arithmetic render raw integers (SqlDialect default);
    // JdbcDatabase#read decodes the integer to a LocalTime.

    @Override
    public String renderEncodedArithmetic(SqlSelectRenderer renderer, String operator,
            Expression left, TypeDescriptor leftType, Expression right, TypeDescriptor rightType, int indent) {
        return renderEncodedArithmetic(renderer, operator, renderer.toSql(left, indent), leftType, right, rightType, indent);
    }

    /**
     * T-SQL date/time arithmetic. SQL Server has no INTERVAL literal, so each duration component is
     * a nested {@code DATEADD(unit, ±n, base)} (DATEADD clamps month-end like the model and promotes
     * a DATE to DATETIME once a clock unit is added). A DATE_FROM_EPOCH_DAY column becomes its date
     * via {@code DATEADD(DAY, <col>, CAST('1970-01-01' AS DATE))}.
     */
    @Override
    public String renderEncodedArithmetic(SqlSelectRenderer renderer, String operator,
            String leftSql, TypeDescriptor leftType, Expression right, TypeDescriptor rightType, int indent) {
        String diff = renderTimestampDiff(renderer, operator, leftSql, leftType, right, rightType, indent);
        if (diff != null) {
            return diff;
        }
        java.util.Optional<String> time = TimeEncodings.secondsArithmetic(renderer, leftSql, leftType, operator, right, indent);
        if (time.isPresent()) {
            return time.get();
        }
        String base = leftSql;
        if (leftType != null && CoreTypeEncoding.DATE_FROM_EPOCH_DAY.equals(leftType.getTypeEncoding())) {
            base = "DATEADD(DAY, " + leftSql + ", " + dateLiteral(LocalDate.of(1970, 1, 1)) + ")";
        }
        base = materializeEpochTimestampLeft(base, leftType);
        Duration dur = right.getDuration();
        if (dur != null) {
            // SQL Server refuses a clock-unit DATEADD on a DATE; promote the date base to DATETIME2
            // first when the duration carries any clock component.
            boolean date = leftType != null && CoreTypeFamily.DATE.equals(leftType.getTypeFamily());
            boolean hasClock = dur.getComponents().stream().anyMatch(c -> switch (c.unit()) {
                case HOUR, MINUTE, SECOND, MILLISECOND -> true;
                default -> false;
            });
            if (date && hasClock) {
                base = "CAST(" + base + " AS DATETIME2)";
            }
            boolean minus = "-".equals(operator);
            String expr = base;
            for (Duration.Component c : dur.getComponents()) {
                long n = minus ? -c.value() : c.value();
                expr = "DATEADD(" + dateaddUnit(c) + ", " + n + ", " + expr + ")";
            }
            return expr;
        }
        return leftSql + " " + operator + " " + renderer.toSql(right, indent);
    }

    /**
     * T-SQL has no {@code EXTRACT(EPOCH ...)}; {@code DATEDIFF_BIG(SECOND, '1970-01-01', ...)} gives
     * epoch-seconds. An INSTANT is a DATETIMEOFFSET — normalize it to a UTC wall-clock DATETIME2 first
     * so the epoch is the true instant regardless of the stored offset.
     */
    @Override
    public String timestampToEpochSeconds(String expr, boolean instant) {
        // SWITCHOFFSET(.., 0) normalizes a DATETIMEOFFSET to UTC (preserving the instant) before the
        // CAST drops the offset — built-in, unlike AT TIME ZONE which needs CLR on some instances.
        String dt = instant ? "CAST(SWITCHOFFSET(" + expr + ", 0) AS DATETIME2)" : expr;
        return "DATEDIFF_BIG(SECOND, '1970-01-01', " + dt + ")";
    }

    /** T-SQL has no {@code make_timestamp}; {@code DATEADD(SECOND, seconds, '1970-01-01')} → DATETIME (UTC). */
    @Override
    public String epochToTimestamp(String expr, java.time.temporal.ChronoUnit unit) {
        return "DATEADD(SECOND, " + SqlDialect.secondsFromEpoch(expr, unit) + ", '1970-01-01')";
    }

    /** T-SQL uses {@code CONCAT} ({@code ||} is not concatenation); it casts the numbers to text implicitly. */
    @Override
    public String pairText(String a, String b) {
        return "CONCAT(" + a + ", ';', " + b + ")";
    }

    /** Map a duration component to its T-SQL DATEADD datepart keyword. */
    private static String dateaddUnit(Duration.Component c) {
        return switch (c.unit()) {
            case YEAR        -> "YEAR";
            case QUARTAL     -> "QUARTER";
            case MONTH       -> "MONTH";
            case WEEK        -> "WEEK";
            case DAY         -> "DAY";
            case HOUR        -> "HOUR";
            case MINUTE      -> "MINUTE";
            case SECOND      -> "SECOND";
            case MILLISECOND -> "MILLISECOND";
        };
    }

}
