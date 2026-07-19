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
package ai.koryki.oracle.iql;

import ai.koryki.catalog.types.CoreTypeEncoding;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.WallClockEncoding;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.typing.TimeEncodings;
import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.functions.FunctionArg;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionKind;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.FunctionRenderer;
import ai.koryki.iql.functions.ReturnTypes;
import ai.koryki.iql.functions.StandardFunctions;
import ai.koryki.iql.query.Duration;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
import ai.koryki.catalog.types.TypeDescriptor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class OracleDialect implements SqlDialect {

    public static final SqlDialect INSTANCE = new OracleDialect();

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withLocale(Locale.ROOT);

    private OracleDialect() {
    }

    /** Wall-clock(zone) → model zone: {@code FROM_TZ} reads the naive value as the declared zone, then shift. */
    @Override
    public String wallClockToModelZone(String columnSql,
                                       WallClockEncoding enc, java.time.ZoneId modelZone) {
        String decl = "'" + enc.getZone().getId() + "'";
        String model = "'" + modelZone.getId() + "'";
        String shifted = "FROM_TZ(CAST(" + columnSql + " AS TIMESTAMP), " + decl + ") AT TIME ZONE " + model;
        if (CoreTypeFamily.DATE.equals(enc.family())) {
            // Oracle DATE carries a time-of-day, and CAST(<tstz> AS DATE) keeps the converted clock time
            // (NY-midnight surfaces as 04:00 UTC); TRUNC drops it so a wall-clock DATE is a pure calendar day.
            return "TRUNC(CAST(" + shifted + " AS DATE))";
        }
        return "CAST(" + shifted + " AS TIMESTAMP)";
    }

    @Override
    public String zoneShiftTimestamp(String valueSql, String fromZoneSql, String toZoneSql) {
        return "CAST(FROM_TZ(CAST(" + valueSql + " AS TIMESTAMP), " + fromZoneSql + ") AT TIME ZONE "
                + toZoneSql + " AS TIMESTAMP)";
    }

    /**
     * Builds an Oracle NUMTOYMINTERVAL or NUMTODSINTERVAL expression for one Duration.Component.
     * YM family: YEAR, MONTH, QUARTAL  →  NUMTOYMINTERVAL
     * DS family: DAY, HOUR, MINUTE, SECOND, MILLISECOND, WEEK  →  NUMTODSINTERVAL
     */
    private static String oracleIntervalComponent(Duration.Component c) {
        return switch (c.unit()) {
            case YEAR        -> "NUMTOYMINTERVAL(" + c.value() + ", 'YEAR')";
            case MONTH       -> "NUMTOYMINTERVAL(" + c.value() + ", 'MONTH')";
            case QUARTAL     -> "NUMTOYMINTERVAL(" + (c.value() * 3) + ", 'MONTH')";
            case WEEK        -> "NUMTODSINTERVAL(" + (c.value() * 7) + ", 'DAY')";
            case DAY         -> "NUMTODSINTERVAL(" + c.value() + ", 'DAY')";
            case HOUR        -> "NUMTODSINTERVAL(" + c.value() + ", 'HOUR')";
            case MINUTE      -> "NUMTODSINTERVAL(" + c.value() + ", 'MINUTE')";
            case SECOND      -> "NUMTODSINTERVAL(" + c.value() + ", 'SECOND')";
            case MILLISECOND -> "NUMTODSINTERVAL(" + c.value() + " / 1000.0, 'SECOND')";
        };
    }

    private static boolean isYMUnit(Duration.Unit unit) {
        return unit == Duration.Unit.YEAR || unit == Duration.Unit.MONTH || unit == Duration.Unit.QUARTAL;
    }

    private static boolean isMixedInterval(Duration duration) {
        boolean hasYM = duration.getComponents().stream().anyMatch(c -> isYMUnit(c.unit()));
        boolean hasDS = duration.getComponents().stream().anyMatch(c -> !isYMUnit(c.unit()));
        return hasYM && hasDS;
    }

    /**
     * Maps a KQL to_interval unit string literal (e.g. 'DAY') to an Oracle NUMTO*INTERVAL call.
     * Accepts upper/lower case unit strings with or without trailing S.
     */
    private static String oracleToIntervalUnit(String value, String unit) {
        String bare = (unit.startsWith("'") && unit.endsWith("'"))
                ? unit.substring(1, unit.length() - 1).toUpperCase(Locale.ROOT)
                : null;
        if (bare != null) {
            return switch (bare) {
                case "YEAR",        "YEARS"        -> "NUMTOYMINTERVAL(" + value + ", 'YEAR')";
                case "MONTH",       "MONTHS"       -> "NUMTOYMINTERVAL(" + value + ", 'MONTH')";
                case "WEEK",        "WEEKS"        -> "NUMTODSINTERVAL(" + value + " * 7, 'DAY')";
                case "DAY",         "DAYS"         -> "NUMTODSINTERVAL(" + value + ", 'DAY')";
                case "HOUR",        "HOURS"        -> "NUMTODSINTERVAL(" + value + ", 'HOUR')";
                case "MINUTE",      "MINUTES"      -> "NUMTODSINTERVAL(" + value + ", 'MINUTE')";
                case "SECOND",      "SECONDS"      -> "NUMTODSINTERVAL(" + value + ", 'SECOND')";
                case "MILLISECOND", "MILLISECONDS" -> "NUMTODSINTERVAL(" + value + " / 1000.0, 'SECOND')";
                default -> "NUMTODSINTERVAL(" + value + ", " + unit + ")";
            };
        }
        return "NUMTODSINTERVAL(" + value + ", " + unit + ")";
    }

    /**
     * Expands the right-hand side of a temporal arithmetic expression.
     *
     * For a seconds-from-midnight TIME the operand becomes integer seconds and the result is
     * wrapped mod 86400 (semantics in {@link TimeEncodings}). For DATE/TIMESTAMP it expands into chained
     * Oracle interval additions, handling:
     *   - Duration literals (e.g. 2d4h, 1y2mo15d)
     *   - to_interval(value, unit) — 2-arg
     *   - to_interval(y, mo, d, h, mi, s) — 6-arg
     */
    private static String expandOracleArithmetic(SqlSelectRenderer renderer, String operator, String leftSql, TypeDescriptor leftType, Expression right, int indent) {
        java.util.Optional<String> time = TimeEncodings.secondsArithmetic(renderer, leftSql, leftType, operator, right, indent);
        if (time.isPresent()) {
            return time.get();
        }
        // A DATE stored as an epoch-day integer must become a DATE before an interval is chained
        // (NUMBER ± INTERVAL is ORA-30081). Oracle DATE + NUMBER adds days, recovering the date.
        if (leftType != null
                && CoreTypeEncoding.DATE_FROM_EPOCH_DAY.equals(leftType.getTypeEncoding())) {
            leftSql = "(DATE '1970-01-01' + " + leftSql + ")";
        }
        Duration dur = right.getDuration();
        if (dur != null) {
            // Year/month/quarter go through ADD_MONTHS, which clamps the day to month-end like the
            // model (DuckDB/Postgres/java.time) — Oracle's DATE + NUMTOYMINTERVAL instead raises
            // ORA-01839 on an overflow such as Dec-31 + 2 months. Day/clock parts stay NUMTODSINTERVAL.
            boolean minus = "-".equals(operator);
            long ymMonths = 0;
            for (Duration.Component c : dur.getComponents()) {
                switch (c.unit()) {
                    case YEAR    -> ymMonths += c.value() * 12;
                    case MONTH   -> ymMonths += c.value();
                    case QUARTAL -> ymMonths += c.value() * 3;
                    default      -> { }
                }
            }
            String result = ymMonths != 0
                    ? "ADD_MONTHS(" + leftSql + ", " + (minus ? -ymMonths : ymMonths) + ")"
                    : leftSql;
            for (Duration.Component c : dur.getComponents()) {
                switch (c.unit()) {
                    case YEAR, MONTH, QUARTAL -> { }   // already applied via ADD_MONTHS
                    default -> result = result + " " + operator + " " + oracleIntervalComponent(c);
                }
            }
            return result;
        }
        if (right.getFunction() != null && "to_interval".equals(right.getFunction().getFunc())) {
            Function f = right.getFunction();
            if (f.getArguments().size() == 2) {
                String value = renderer.toSql(f.getArguments().get(0), indent);
                String unit  = renderer.toSql(f.getArguments().get(1), indent);
                return leftSql + " " + operator + " " + oracleToIntervalUnit(value, unit);
            }
            if (f.getArguments().size() == 6) {
                var args = f.getArguments();
                String[] vals = new String[6];
                for (int i = 0; i < 6; i++) vals[i] = renderer.toSql(args.get(i), indent);
                StringBuilder sb = new StringBuilder(leftSql);
                // YM components first, then DS components — keeps Oracle type families separate
                if (!"0".equals(vals[0].trim())) sb.append(" ").append(operator).append(" NUMTOYMINTERVAL(").append(vals[0]).append(", 'YEAR')");
                if (!"0".equals(vals[1].trim())) sb.append(" ").append(operator).append(" NUMTOYMINTERVAL(").append(vals[1]).append(", 'MONTH')");
                if (!"0".equals(vals[2].trim())) sb.append(" ").append(operator).append(" NUMTODSINTERVAL(").append(vals[2]).append(", 'DAY')");
                if (!"0".equals(vals[3].trim())) sb.append(" ").append(operator).append(" NUMTODSINTERVAL(").append(vals[3]).append(", 'HOUR')");
                if (!"0".equals(vals[4].trim())) sb.append(" ").append(operator).append(" NUMTODSINTERVAL(").append(vals[4]).append(", 'MINUTE')");
                if (!"0".equals(vals[5].trim())) sb.append(" ").append(operator).append(" NUMTODSINTERVAL(").append(vals[5]).append(", 'SECOND')");
                return sb.toString();
            }
        }
        return leftSql + " " + operator + " " + renderer.toSql(right, indent);
    }

    private static final FunctionRenderer FUNCTION_RENDERER = buildFunctionRenderer();

    @Override
    public FunctionRenderer getFunctionRenderer() {
        return FUNCTION_RENDERER;
    }

    private static FunctionRenderer buildFunctionRenderer() {
        FunctionRegistry registry = StandardFunctions.registry();

        // to_date(value) | to_date(value, format) | to_date(ts, tz) | to_date(year, month, day)
        registry.register(new FunctionDefinition("to_date", ReturnTypes.DATE)
                .args(FunctionArg.arg("value"))
                .template("CAST({0} AS DATE)"));
        registry.register(new FunctionDefinition("to_date", ReturnTypes.DATE) {
            @Override
            public String render(SqlSelectRenderer renderer, Function function, int indent) {
                String a0 = renderer.toSql(function.getArguments().get(0), indent);
                String a1 = renderer.toSql(function.getArguments().get(1), indent);
                TypeDescriptor t0 = renderer.resolveType(function.getArguments().get(0));
                if (t0 != null && CoreTypeFamily.TIMESTAMP.equals(t0.getTypeFamily()))
                    // tz conversion: treat source as UTC, return local DATE
                    return "CAST(FROM_TZ(CAST(" + a0 + " AS TIMESTAMP), 'UTC') AT TIME ZONE " + a1 + " AS DATE)";
                // parse string with Oracle-native format mask
                return "TO_DATE(" + a0 + ", " + a1 + ")";
            }
        }.args(FunctionArg.arg("value"), FunctionArg.arg("format")));
        registry.register(new FunctionDefinition("to_date", ReturnTypes.DATE)
                .args(FunctionArg.arg("year"), FunctionArg.arg("month"), FunctionArg.arg("day"))
                .template("TO_DATE({0} || '-' || {1} || '-' || {2}, 'YYYY-MM-DD')"));

        // to_time(value) | to_time(value, format) | to_time(ts, tz) | to_time(hour, minute, second)
        // Oracle has no TIME type — all overloads return VARCHAR2 in HH24:MI:SS format.
        registry.register(new FunctionDefinition("to_time", ReturnTypes.TEXT) {
            @Override
            public String render(SqlSelectRenderer renderer, Function function, int indent) {
                Expression arg = function.getArguments().getFirst();
                TypeDescriptor src = renderer.resolveType(arg);
                String sql = renderer.toSql(arg, indent);
                if (src != null && CoreTypeEncoding.TIME_SECONDS_FROM_MIDNIGHT.equals(src.getTypeEncoding()))
                    return "TO_CHAR(DATE '1970-01-01' + " + sql + " / 86400, 'HH24:MI:SS')";
                return "TO_CHAR(" + sql + ", 'HH24:MI:SS')";
            }
        }.args(FunctionArg.arg("value")));
        registry.register(new FunctionDefinition("to_time", ReturnTypes.TEXT) {
            @Override
            public String render(SqlSelectRenderer renderer, Function function, int indent) {
                String a0 = renderer.toSql(function.getArguments().get(0), indent);
                String a1 = renderer.toSql(function.getArguments().get(1), indent);
                TypeDescriptor t0 = renderer.resolveType(function.getArguments().get(0));
                if (t0 != null && CoreTypeFamily.TIMESTAMP.equals(t0.getTypeFamily()))
                    return "TO_CHAR(FROM_TZ(CAST(" + a0 + " AS TIMESTAMP), 'UTC') AT TIME ZONE " + a1 + ", 'HH24:MI:SS')";
                return "TO_CHAR(TO_TIMESTAMP(" + a0 + ", " + a1 + "), 'HH24:MI:SS')";
            }
        }.args(FunctionArg.arg("value"), FunctionArg.arg("format")));
        registry.register(new FunctionDefinition("to_time", ReturnTypes.TEXT)
                .args(FunctionArg.arg("hour"), FunctionArg.arg("minute"), FunctionArg.arg("second"))
                .template("LPAD({0}, 2, '0') || ':' || LPAD({1}, 2, '0') || ':' || LPAD({2}, 2, '0')"));

        // to_timestamp(value) | to_timestamp(value, format) | to_timestamp(value, format, tz)
        // | to_timestamp(year, month, day, hour, minute, second)
        // | to_timestamp(year, month, day, hour, minute, second, tz)
        registry.register(new FunctionDefinition("to_timestamp", ReturnTypes.TIMESTAMP)
                .args(FunctionArg.arg("value"))
                .template("CAST({0} AS TIMESTAMP)"));
        registry.register(new FunctionDefinition("to_timestamp", ReturnTypes.TIMESTAMP)
                .args(FunctionArg.arg("value"), FunctionArg.arg("format"))
                .template("TO_TIMESTAMP({0}, {1})"));
        // to_timestamp(str, fmt, tz): parse string as UTC, then express in tz
        registry.register(new FunctionDefinition("to_timestamp", ReturnTypes.TIMESTAMP)
                .args(FunctionArg.arg("value"), FunctionArg.arg("format"), FunctionArg.arg("tz"))
                .template("FROM_TZ(TO_TIMESTAMP({0}, {1}), 'UTC') AT TIME ZONE {2}"));
        registry.register(new FunctionDefinition("to_timestamp", ReturnTypes.TIMESTAMP)
                .args(FunctionArg.arg("year"), FunctionArg.arg("month"), FunctionArg.arg("day"),
                      FunctionArg.arg("hour"), FunctionArg.arg("minute"), FunctionArg.arg("second"))
                .template("TO_TIMESTAMP({0} || '-' || {1} || '-' || {2} || ' ' || {3} || ':' || {4} || ':' || {5}, 'YYYY-MM-DD HH24:MI:SS')"));
        registry.register(new FunctionDefinition("to_timestamp", ReturnTypes.TIMESTAMP)
                .args(FunctionArg.arg("year"), FunctionArg.arg("month"), FunctionArg.arg("day"),
                      FunctionArg.arg("hour"), FunctionArg.arg("minute"), FunctionArg.arg("second"),
                      FunctionArg.arg("tz"))
                .template("FROM_TZ(TO_TIMESTAMP({0} || '-' || {1} || '-' || {2} || ' ' || {3} || ':' || {4} || ':' || {5}, 'YYYY-MM-DD HH24:MI:SS'), 'UTC') AT TIME ZONE {6}"));

        // to_interval(value, unit) | to_interval(years, months, days, hours, minutes, seconds)
        // Oracle has two incompatible interval families: YEAR-TO-MONTH and DAY-TO-SECOND.
        // The 6-arg form cannot represent a mixed YM+DS interval as a standalone value —
        // use arithmetic (see renderEncodedArithmetic) to chain intervals on a DATE instead.
        registry.register(new FunctionDefinition("to_interval", ReturnTypes.INTERVAL) {
            @Override
            public String render(SqlSelectRenderer renderer, Function function, int indent) {
                String value = renderer.toSql(function.getArguments().get(0), indent);
                String unit  = renderer.toSql(function.getArguments().get(1), indent);
                return oracleToIntervalUnit(value, unit);
            }
        }.args(FunctionArg.arg("value"), FunctionArg.arg("unit")));
        registry.register(new FunctionDefinition("to_interval", ReturnTypes.INTERVAL) {
            @Override
            public String render(SqlSelectRenderer renderer, Function function, int indent) {
                // Standalone 6-arg: only valid if all non-zero values belong to one family.
                // For arithmetic use, renderEncodedArithmetic handles the expansion.
                var args = function.getArguments();
                String[] vals = new String[6];
                for (int i = 0; i < 6; i++) vals[i] = renderer.toSql(args.get(i), indent);
                boolean hasYM = !"0".equals(vals[0].trim()) || !"0".equals(vals[1].trim());
                boolean hasDS = !"0".equals(vals[2].trim()) || !"0".equals(vals[3].trim())
                        || !"0".equals(vals[4].trim()) || !"0".equals(vals[5].trim());
                if (hasYM && hasDS) {
                    throw new UnsupportedOperationException(
                            "Oracle cannot represent a mixed YEAR-TO-MONTH + DAY-TO-SECOND interval as a standalone value. "
                            + "Use date arithmetic: date + to_interval(...) instead.");
                }
                List<String> parts = new java.util.ArrayList<>();
                if (!"0".equals(vals[0].trim())) parts.add("NUMTOYMINTERVAL(" + vals[0] + ", 'YEAR')");
                if (!"0".equals(vals[1].trim())) parts.add("NUMTOYMINTERVAL(" + vals[1] + ", 'MONTH')");
                if (!"0".equals(vals[2].trim())) parts.add("NUMTODSINTERVAL(" + vals[2] + ", 'DAY')");
                if (!"0".equals(vals[3].trim())) parts.add("NUMTODSINTERVAL(" + vals[3] + ", 'HOUR')");
                if (!"0".equals(vals[4].trim())) parts.add("NUMTODSINTERVAL(" + vals[4] + ", 'MINUTE')");
                if (!"0".equals(vals[5].trim())) parts.add("NUMTODSINTERVAL(" + vals[5] + ", 'SECOND')");
                if (parts.isEmpty()) parts.add("NUMTODSINTERVAL(0, 'SECOND')");
                return String.join(" + ", parts);
            }
        }.args(FunctionArg.arg("years"), FunctionArg.arg("months"), FunctionArg.arg("days"),
               FunctionArg.arg("hours"), FunctionArg.arg("minutes"), FunctionArg.arg("seconds")));


        // date-part extraction
        StandardFunctions.registerExtractParts(registry);

        // string_agg → LISTAGG
        registry.register(new FunctionDefinition("listagg", ReturnTypes.TEXT, FunctionKind.AGGREGATE));

        // position(substr, str) → INSTR(str, substr)
        registry.register(new FunctionDefinition("instr", ReturnTypes.INTEGER));

        // parse_date/time/timestamp(value, format) → TO_DATE / TO_TIMESTAMP
        // Oracle format strings are canonical in KQL — no translation needed.
        // Oracle has no TIME type; parse_time maps to TO_DATE.
        registry.register(StandardFunctions.parseTwoArg("parse_date",      ReturnTypes.DATE,      "TO_DATE"));
        registry.register(StandardFunctions.parseTwoArg("parse_time",      ReturnTypes.TIME,      "TO_DATE"));
        registry.register(StandardFunctions.parseTwoArg("parse_timestamp", ReturnTypes.TIMESTAMP, "TO_TIMESTAMP"));

        // PostgreSQL-chapter functions: Oracle spellings

        // Distances and Rolling (TEMPORAL.md)
        // MONTHS_BETWEEN deviates from java.time when both dates are the last
        // day of their months (Oracle returns a whole month) — conformance item.
        // ADD_MONTHS snaps to the last day when the input is a last day
        // (Feb 28 + 1mo = Mar 31; java.time says Mar 28) — conformance item.

        // PostgreSQL-chapter functions without an Oracle equivalent


        // to_text is the rolled-out, tested function: keep its dialect cast-type override.
        registry.overrideAll("to_text", "CAST({0} AS VARCHAR(4000))");
        registry.override("months_between", "TRUNC(MONTHS_BETWEEN({1}, {0}))");
        registry.override("days_between",   "TRUNC(CAST({1} AS DATE) - CAST({0} AS DATE))");
        registry.override("month_end",   "TRUNC(LAST_DAY({0}))");
        registry.override("quarter_end", "LAST_DAY(ADD_MONTHS(TRUNC({0}, 'Q'), 2))");
        registry.override("year_end",    "LAST_DAY(ADD_MONTHS(TRUNC({0}, 'YYYY'), 11))");
        return registry;
    }

    @Override
    public String mapSetOperator(String operator) {
        // Oracle uses MINUS natively; no mapping needed.
        return operator;
    }

    @Override
    public String timestampLiteral(LocalDateTime dateTime) {
        return "TIMESTAMP '" + dateTime.format(TIMESTAMP_FMT) + "'";
    }

    @Override
    public String durationLiteral(Duration duration) {
        if (isMixedInterval(duration)) {
            throw new UnsupportedOperationException(
                    "Oracle cannot represent a mixed YEAR-TO-MONTH + DAY-TO-SECOND interval as a standalone value. "
                    + "Use date arithmetic: date + duration instead.");
        }
        return duration.getComponents().stream()
                .map(OracleDialect::oracleIntervalComponent)
                .collect(Collectors.joining(" + "));
    }

    @Override
    public String renderEncodedArithmetic(SqlSelectRenderer renderer, String operator,
            Expression left, TypeDescriptor leftType,
            Expression right, TypeDescriptor rightType,
            int indent) {
        return renderEncodedArithmetic(renderer, operator, renderer.toSql(left, indent), leftType, right, rightType, indent);
    }

    @Override
    public String renderEncodedArithmetic(SqlSelectRenderer renderer, String operator,
            String leftSql, TypeDescriptor leftType,
            Expression right, TypeDescriptor rightType,
            int indent) {
        String diff = renderTimestampDiff(renderer, operator, leftSql, leftType, right, rightType, indent);
        if (diff != null) {
            return diff;
        }
        // EPOCH timestamp ± duration: materialize to a TIMESTAMP first (epoch-day stays inside expand*)
        leftSql = materializeEpochTimestampLeft(leftSql, leftType);
        return expandOracleArithmetic(renderer, operator, leftSql, leftType, right, indent);
    }

    /** Oracle has no {@code make_timestamp}; epoch-seconds → TIMESTAMP via NUMTODSINTERVAL on the epoch base. */
    @Override
    public String epochToTimestamp(String expr, java.time.temporal.ChronoUnit unit) {
        return "(TIMESTAMP '1970-01-01 00:00:00' + NUMTODSINTERVAL(" + SqlDialect.secondsFromEpoch(expr, unit) + ", 'SECOND'))";
    }

    /**
     * Oracle has no {@code EXTRACT(EPOCH ...)}. {@code CAST(... AS DATE)} yields the UTC wall clock
     * under the UTC session; DATE subtraction is in days, so {@code * 86400} gives epoch-seconds
     * (whole-second precision, which suffices for a fixed elapsed span).
     */
    @Override
    public String timestampToEpochSeconds(String expr, boolean instant) {
        return "ROUND((CAST(" + expr + " AS DATE) - DATE '1970-01-01') * 86400)";
    }

    /** Oracle casts numbers to text with {@code TO_CHAR} (plain {@code CAST(.. AS VARCHAR)} needs a length). */
    @Override
    public String pairText(String a, String b) {
        return "(TO_CHAR(" + a + ") || ';' || TO_CHAR(" + b + "))";
    }

    @Override
    public String renderComparisonOperand(SqlSelectRenderer renderer, Expression expression,
            TypeDescriptor leftType, TypeDescriptor rightType, int indent) {
        // DATE-encoded TIME columns compare against an epoch-anchored DATE, not a seconds number
        java.util.Optional<java.time.LocalDateTime> anchored =
                TimeEncodings.timeFromDateOperand(leftType, expression);
        if (anchored.isPresent()) {
            return "TO_DATE('" + anchored.get().format(TIMESTAMP_FMT) + "', 'YYYY-MM-DD HH24:MI:SS')";
        }
        if (CoreTypeFamily.TIME.equals(leftType.getTypeFamily()) && expression.getLocalTime() != null) {
            return String.valueOf(expression.getLocalTime().toSecondOfDay());
        }
        java.util.Optional<String> reconciled =
                TimeEncodings.reconcile(renderer, expression, leftType, rightType, indent);
        if (reconciled.isPresent()) {
            return reconciled.get();
        }
        return SqlDialect.super.renderComparisonOperand(renderer, expression, leftType, rightType, indent);
    }

    @Override
    public String recursive(boolean recursive) {
        return "";
    }

}
