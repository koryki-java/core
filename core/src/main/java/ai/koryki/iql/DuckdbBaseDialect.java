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
package ai.koryki.iql;

import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.WallClockEncoding;
import ai.koryki.iql.functions.*;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
import ai.koryki.iql.typing.TimeEncodings;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Default-Dialect is DuckdbDialect.
 */
public class DuckdbBaseDialect implements SqlDialect {

    public static final SqlDialect INSTANCE = new DuckdbBaseDialect();
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withLocale(Locale.ROOT);
    private static final DateTimeFormatter TIME_FMT      = DateTimeFormatter.ofPattern("HH:mm:ss").withLocale(Locale.ROOT);

    protected DuckdbBaseDialect() {
    }

    /**
     * Wall-clock(zone) → model zone. DuckDB's two-arg {@code AT TIME ZONE} flips between a naive
     * timestamp and an instant: applying the declared zone reads the naive stored value as an instant,
     * then applying the model zone renders that instant back as a naive model-zone wall-clock value. A
     * {@code DATE_WALLCLOCK} is converted at start-of-day in the declared zone and taken back to a date.
     */
    @Override
    public String wallClockToModelZone(String columnSql,
                                       WallClockEncoding enc, java.time.ZoneId modelZone) {
        return SqlDialect.atTimeZoneToModelZone(columnSql, enc, modelZone);
    }

    @Override
    public String zoneShiftTimestamp(String valueSql, String fromZoneSql, String toZoneSql) {
        return SqlDialect.atTimeZoneShift(valueSql, fromZoneSql, toZoneSql);
    }

    /**
     * Translates a KQL Oracle-style format string literal (e.g. 'YYYY-MM-DD HH24:MI:SS')
     * to a DuckDB strptime format literal (e.g. '%Y-%m-%d %H:%M:%S').
     * Non-literal expressions (runtime values) are returned unchanged.
     */
    private static String translateKQLFormat(String rendered) {
        if (!rendered.startsWith("'") || !rendered.endsWith("'")) {
            return rendered;
        }
        String fmt = rendered.substring(1, rendered.length() - 1);
        // Replace longer tokens before shorter prefixes to avoid partial matches
        fmt = fmt.replace("MONTH", "%B")
                .replace("MON",   "%b")
                .replace("HH24",  "%H")
                .replace("HH12",  "%I")
                .replace("HH",    "%I")
                .replace("YYYY",  "%Y")
                .replace("YY",    "%y")
                .replace("MM",    "%m")
                .replace("DD",    "%d")
                .replace("DAY",   "%A")
                .replace("DY",    "%a")
                .replace("MI",    "%M")
                .replace("SS",    "%S")
                .replace("AM",    "%p")
                .replace("PM",    "%p");
        return "'" + fmt + "'";
    }

    private static String toIntervalUnit(String value, String unit) {
        // unit may be a string literal like 'DAY' or a runtime expression
        String bare = (unit.startsWith("'") && unit.endsWith("'"))
                ? unit.substring(1, unit.length() - 1).toUpperCase(Locale.ROOT)
                : null;
        if (bare != null) {
            return switch (bare) {
                case "YEAR",        "YEARS"        -> "to_years("        + value + ")";
                case "MONTH",       "MONTHS"       -> "to_months("       + value + ")";
                case "WEEK",        "WEEKS"         -> "to_days("         + value + " * 7)";
                case "DAY",         "DAYS"         -> "to_days("         + value + ")";
                case "HOUR",        "HOURS"        -> "to_hours("        + value + ")";
                case "MINUTE",      "MINUTES"      -> "to_minutes("      + value + ")";
                case "SECOND",      "SECONDS"      -> "to_seconds("      + value + ")";
                case "MILLISECOND", "MILLISECONDS" -> "to_milliseconds(" + value + ")";
                case "MICROSECOND", "MICROSECONDS" -> "to_microseconds(" + value + ")";
                default -> "INTERVAL (" + value + ") " + bare;
            };
        }
        // runtime unit — fall back to INTERVAL expr cast
        return "INTERVAL (" + value + ") || ' ' || " + unit;
    }

    private static final FunctionRenderer FUNCTION_RENDERER = buildFunctionRenderer();

    @Override
    public FunctionRenderer getFunctionRenderer() {
        return FUNCTION_RENDERER;
    }

    private static FunctionRenderer buildFunctionRenderer() {
        FunctionRegistry registry = StandardFunctions.registry();

        // list([x]) — DuckDB array literal
        registry.register(new FunctionDefinition("list", ReturnTypes.TEXT)
                .args(FunctionArg.arg("value"))
                .template("[{0}]"));

        // rand() → random()
        registry.register(new FunctionDefinition("rand", ReturnTypes.FLOAT).template("random()"));

        // truncate → trunc
        registry.register(new FunctionDefinition("truncate", ReturnTypes.ARG0)
                .variadic(FunctionArg.arg("value"))
                .template("trunc({*})"));

        // regexp functions: DuckDB spellings
        registry.override("regexp_like",   "regexp_matches({*})");
        registry.override("regexp_substr", "regexp_extract({*})");

        // DuckDB-specific string functions
        registry.register(new FunctionDefinition("printf",     ReturnTypes.TEXT));
        registry.register(new FunctionDefinition("btrim",      ReturnTypes.TEXT));
        registry.register(new FunctionDefinition("strpos",     ReturnTypes.INTEGER));
        registry.register(new FunctionDefinition("instr",      ReturnTypes.INTEGER));
        registry.register(new FunctionDefinition("split_part", ReturnTypes.TEXT));
        registry.register(new FunctionDefinition("split",      ReturnTypes.TEXT));
        registry.register(new FunctionDefinition("to_hex",     ReturnTypes.TEXT));
        registry.register(new FunctionDefinition("md5",        ReturnTypes.TEXT));
        registry.register(new FunctionDefinition("sha256",     ReturnTypes.TEXT));
        registry.register(new FunctionDefinition("group_concat", ReturnTypes.TEXT));


        // bitwise
        for (String name : java.util.List.of("bit_and", "bit_or", "bit_xor", "bit_count")) {
            registry.register(new FunctionDefinition(name, ReturnTypes.INTEGER));
        }

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
                    return "timezone(" + a1 + ", " + a0 + "::TIMESTAMPTZ)::DATE";
                return "strptime(" + a0 + ", " + translateKQLFormat(a1) + ")::DATE";
            }
        }.args(FunctionArg.arg("value"), FunctionArg.arg("format")));
        registry.register(new FunctionDefinition("to_date", ReturnTypes.DATE)
                .args(FunctionArg.arg("year"), FunctionArg.arg("month"), FunctionArg.arg("day"))
                .template("MAKE_DATE({0}, {1}, {2})"));

        // to_time(value) | to_time(value, format) | to_time(ts, tz) | to_time(hour, minute, second)
        registry.register(new FunctionDefinition("to_time", ReturnTypes.TIME)
                .args(FunctionArg.arg("value"))
                .template("CAST({0} AS TIME)"));
        registry.register(new FunctionDefinition("to_time", ReturnTypes.TIME) {
            @Override
            public String render(SqlSelectRenderer renderer, Function function, int indent) {
                String a0 = renderer.toSql(function.getArguments().get(0), indent);
                String a1 = renderer.toSql(function.getArguments().get(1), indent);
                TypeDescriptor t0 = renderer.resolveType(function.getArguments().get(0));
                if (t0 != null && CoreTypeFamily.TIMESTAMP.equals(t0.getTypeFamily()))
                    return "timezone(" + a1 + ", " + a0 + "::TIMESTAMPTZ)::TIME";
                return "strptime(" + a0 + ", " + translateKQLFormat(a1) + ")::TIME";
            }
        }.args(FunctionArg.arg("value"), FunctionArg.arg("format")));
        registry.register(new FunctionDefinition("to_time", ReturnTypes.TIME)
                .args(FunctionArg.arg("hour"), FunctionArg.arg("minute"), FunctionArg.arg("second"))
                .template("MAKE_TIME({0}, {1}, {2}::DOUBLE)"));

        // to_timestamp(value) | to_timestamp(value, format) | to_timestamp(value, format, tz)
        // | to_timestamp(year, month, day, hour, minute, second) | to_timestamp(year, month, day, hour, minute, second, tz)
        registry.register(new FunctionDefinition("to_timestamp", ReturnTypes.TIMESTAMP)
                .args(FunctionArg.arg("value"))
                .template("CAST({0} AS TIMESTAMP)"));
        registry.register(new FunctionDefinition("to_timestamp", ReturnTypes.TIMESTAMP) {
            @Override
            public String render(SqlSelectRenderer renderer, Function function, int indent) {
                return "strptime(" + renderer.toSql(function.getArguments().get(0), indent)
                        + ", " + translateKQLFormat(renderer.toSql(function.getArguments().get(1), indent)) + ")";
            }
        }.args(FunctionArg.arg("value"), FunctionArg.arg("format")));
        registry.register(new FunctionDefinition("to_timestamp", ReturnTypes.TIMESTAMP) {
            @Override
            public String render(SqlSelectRenderer renderer, Function function, int indent) {
                return "timezone(" + renderer.toSql(function.getArguments().get(2), indent)
                        + ", strptime(" + renderer.toSql(function.getArguments().get(0), indent)
                        + ", " + translateKQLFormat(renderer.toSql(function.getArguments().get(1), indent)) + ")::TIMESTAMPTZ)";
            }
        }.args(FunctionArg.arg("value"), FunctionArg.arg("format"), FunctionArg.arg("tz")));
        registry.register(new FunctionDefinition("to_timestamp", ReturnTypes.TIMESTAMP)
                .args(FunctionArg.arg("year"), FunctionArg.arg("month"), FunctionArg.arg("day"),
                      FunctionArg.arg("hour"), FunctionArg.arg("minute"), FunctionArg.arg("second"))
                .template("MAKE_TIMESTAMP({0}, {1}, {2}, {3}, {4}, {5})"));
        registry.register(new FunctionDefinition("to_timestamp", ReturnTypes.TIMESTAMP)
                .args(FunctionArg.arg("year"), FunctionArg.arg("month"), FunctionArg.arg("day"),
                      FunctionArg.arg("hour"), FunctionArg.arg("minute"), FunctionArg.arg("second"),
                      FunctionArg.arg("tz"))
                .template("timezone({6}, MAKE_TIMESTAMP({0}, {1}, {2}, {3}, {4}, {5})::TIMESTAMPTZ)"));

        // parse_date/time/timestamp(value, format) → strptime(value, format)::<TYPE>
        registry.register(new FunctionDefinition("parse_date", ReturnTypes.DATE) {
            @Override
            public String render(SqlSelectRenderer renderer, Function function, int indent) {
                if (function.getArguments().size() != 2)
                    throw new IllegalArgumentException("parse_date requires two arguments: value, format");
                return "strptime(" + renderer.toSql(function.getArguments().get(0), indent)
                        + ", " + translateKQLFormat(renderer.toSql(function.getArguments().get(1), indent)) + ")::DATE";
            }
        });
        registry.register(new FunctionDefinition("parse_time", ReturnTypes.TIME) {
            @Override
            public String render(SqlSelectRenderer renderer, Function function, int indent) {
                if (function.getArguments().size() != 2)
                    throw new IllegalArgumentException("parse_time requires two arguments: value, format");
                return "strptime(" + renderer.toSql(function.getArguments().get(0), indent)
                        + ", " + translateKQLFormat(renderer.toSql(function.getArguments().get(1), indent)) + ")::TIME";
            }
        });
        registry.register(new FunctionDefinition("parse_timestamp", ReturnTypes.TIMESTAMP) {
            @Override
            public String render(SqlSelectRenderer renderer, Function function, int indent) {
                if (function.getArguments().size() != 2)
                    throw new IllegalArgumentException("parse_timestamp requires two arguments: value, format");
                return "strptime(" + renderer.toSql(function.getArguments().get(0), indent)
                        + ", " + translateKQLFormat(renderer.toSql(function.getArguments().get(1), indent)) + ")";
            }
        });

        // to_interval(value, unit) | to_interval(years, months, days, hours, minutes, seconds)
        // unit is one of: YEAR, MONTH, WEEK, DAY, HOUR, MINUTE, SECOND, MILLISECOND (case-insensitive)
        registry.register(new FunctionDefinition("to_interval", ReturnTypes.INTERVAL) {
            @Override
            public String render(SqlSelectRenderer renderer, Function function, int indent) {
                String value = renderer.toSql(function.getArguments().get(0), indent);
                String unit  = renderer.toSql(function.getArguments().get(1), indent);
                return toIntervalUnit(value, unit);
            }
        }.args(FunctionArg.arg("value"), FunctionArg.arg("unit")));
        registry.register(new FunctionDefinition("to_interval", ReturnTypes.INTERVAL)
                .args(FunctionArg.arg("years"), FunctionArg.arg("months"), FunctionArg.arg("days"),
                      FunctionArg.arg("hours"), FunctionArg.arg("minutes"), FunctionArg.arg("seconds"))
                .template("to_years({0}) + to_months({1}) + to_days({2}) + to_hours({3}) + to_minutes({4}) + to_seconds({5})"));

        return registry;
    }

    @Override
    public String mapSetOperator(String operator) {
        if ("MINUS".equals(operator)) {
            return "EXCEPT";
        }
        return operator;
    }

    @Override
    public String timeLiteral(LocalTime time) {
        return "TIME '" + time.format(TIME_FMT) + "'";
    }

    @Override
    public String timestampLiteral(LocalDateTime dateTime) {
        return "TIMESTAMP '" + dateTime.format(TIMESTAMP_FMT) + "'";
    }

    @Override
    public String durationLiteral(ai.koryki.iql.query.Duration duration) {
        return SqlDialect.combinedInterval(duration, " ");   // DuckDB parses the space-separated verbose form
    }

    @Override
    public String renderComparisonOperand(SqlSelectRenderer renderer, Expression expression,
            TypeDescriptor leftType, TypeDescriptor rightType, int indent) {
        java.util.Optional<String> seconds = TimeEncodings.secondsFromMidnightLiteral(leftType, expression);
        if (seconds.isPresent()) {
            return seconds.get();
        }
        java.util.Optional<String> reconciled = TimeEncodings.reconcile(renderer, expression, leftType, rightType, indent);
        if (reconciled.isPresent()) {
            return reconciled.get();
        }
        if (CoreTypeFamily.DATE.equals(leftType.getTypeFamily())
                && rightType != null && CoreTypeFamily.TIMESTAMP.equals(rightType.getTypeFamily())) {
            return "(" + renderer.toSql(expression, indent) + ")::DATE";
        }
        return SqlDialect.super.renderComparisonOperand(renderer, expression, leftType, rightType, indent);
    }

    // TIME(seconds-from-midnight) output + arithmetic now render raw integers (SqlDialect default);
    // JdbcDatabase#read decodes the integer to a LocalTime.

}
