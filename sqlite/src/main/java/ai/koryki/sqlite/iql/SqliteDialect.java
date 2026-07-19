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

import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.iql.Identifier;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.query.Duration;
import ai.koryki.iql.query.Expression;
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

    private SqliteDialect() {
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
        return Identifier.indent(indent) + "LIMIT " + limit + System.lineSeparator();
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

}
