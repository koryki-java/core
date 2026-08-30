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
import ai.koryki.catalog.types.FamilyGroup;
import ai.koryki.catalog.types.Families;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeFamily;
import ai.koryki.catalog.types.WallClockEncoding;
import ai.koryki.iql.functions.*;
import ai.koryki.iql.functions.catalog.DateTimeFunctions;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
import ai.koryki.iql.typing.TimeEncodings;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

/**
 * Default-Dialect is DuckdbDialect.
 */
public class DuckdbBaseDialect implements SqlDialect {

    public static final SqlDialect INSTANCE = new DuckdbBaseDialect();
    private static final DateTimeFormatter TIME_FMT      = DateTimeFormatter.ofPattern("HH:mm:ss").withLocale(Locale.ROOT);

    /** DuckDB list literals accept any scalar element except binary (BLOB) and logical (BOOLEAN) values. */
    private static final TypeFamily LIST_ELEMENT = new FamilyGroup("LIST_ELEMENT", Set.of(
            CoreTypeFamily.DATE, CoreTypeFamily.DECIMAL, CoreTypeFamily.FLOAT, CoreTypeFamily.INTEGER,
            CoreTypeFamily.TIME, CoreTypeFamily.INTERVAL, CoreTypeFamily.TIMESTAMP, CoreTypeFamily.TEXT,
            CoreTypeFamily.JSON, CoreTypeFamily.UUID));

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
        return FormatMask.translate(rendered, FormatMask.STRFTIME);
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
                .args(FunctionArg.arg("value", LIST_ELEMENT))
                .template("[{0}]"));

        // truncate → trunc
        registry.register(new FunctionDefinition("truncate", ReturnTypes.ARG0)
                .variadic(FunctionArg.arg("value", Families.NUMERIC))
                .template("trunc({*})"));

        // regexp functions: DuckDB spellings
        // Replace every match, not just the first. PostgreSQL and DuckDB replace only the first by
        // default -- MariaDB, Oracle and Trino replace all. The g modifier evens that out; the
        // promise is in the catalog, its price is here.
        registry.override("regexp_replace", "regexp_replace({0}, {1}, {2}, 'g')");
        registry.override("regexp_like",   "regexp_matches({*})");
        registry.override("regexp_substr", "regexp_extract({*})");

        // today() → DuckDB's today() function. The canonical CURRENT_DATE keyword is shadowed by a
        // same-named column alias — `SELECT CURRENT_DATE AS current_date` fails to bind — whereas the
        // today() function call is unambiguous.
        registry.override("today", "today()");

        // Uniform-surface translations for functions DuckDB spells differently or lacks.
        // Bare time()/timestamp() parse as the DuckDB type keyword — cast explicitly.
        registry.override("octet_length", "octet_length(encode({0}))");          // octet_length takes a BLOB
        registry.override("dayofweek",    "isodow({0})");                        // dayofweek() counts Sunday as 0
        // no native regexp_count; the optional 1-based start position becomes a substr on the
        // input. Copy-based overlay keeps the canonical signature and documentation.
        registry.register(new FunctionDefinition(registry.overloads("regexp_count").get(0)) {
            @Override
            protected String renderBody(SqlSelectRenderer renderer, Function function, int indent) {
                var args = function.getArguments();
                String s = renderer.toSql(args.get(0), indent);
                String p = renderer.toSql(args.get(1), indent);
                if (args.size() > 2) {
                    s = "substr(" + s + ", " + renderer.toSql(args.get(2), indent) + ")";
                }
                return "len(regexp_extract_all(" + s + ", " + p + "))";
            }
        });

        // DuckDB has neither initcap nor a format-mask to_number(value, format).
        registry.unsupported("initcap");
        registry.unsupported("to_number");

        // to_char(value, format): DuckDB formats dates via strftime with a strptime-style mask.
        registry.register(new FunctionDefinition("to_char", ReturnTypes.TEXT) {
            @Override
            protected String renderBody(SqlSelectRenderer renderer, Function function, int indent) {
                return "strftime(" + renderer.toSql(function.getArguments().get(0), indent)
                        + ", " + translateKQLFormat(renderer.toSql(function.getArguments().get(1), indent)) + ")";
            }
        }.args(FunctionArg.arg("value", Families.ANY), FunctionArg.arg("format", CoreTypeFamily.TEXT)));

        // overlay(string, replacement, start [, length]): DuckDB has no overlay(); splice with substr.
        registry.register(new FunctionDefinition("overlay", ReturnTypes.TEXT) {
            @Override
            protected String renderBody(SqlSelectRenderer renderer, Function function, int indent) {
                var args = function.getArguments();
                String s     = renderer.toSql(args.get(0), indent);
                String repl  = renderer.toSql(args.get(1), indent);
                String start = renderer.toSql(args.get(2), indent);
                String len   = args.size() > 3 ? renderer.toSql(args.get(3), indent) : "length(" + repl + ")";
                return "substr(" + s + ", 1, " + start + " - 1) || " + repl
                        + " || substr(" + s + ", " + start + " + " + len + ")";
            }
        }.args(FunctionArg.arg("string", CoreTypeFamily.TEXT), FunctionArg.arg("replacement", CoreTypeFamily.TEXT),
               FunctionArg.arg("start", CoreTypeFamily.INTEGER), FunctionArg.optionalArg("length", CoreTypeFamily.INTEGER)));

        // DuckDB-specific string functions
        // No instr/group_concat alias: the catalog's strpos and string_agg already cover them
        // portably, and a second spelling only produces queries that do not run elsewhere.
        // Only DuckDB peculiarities the catalog does not know. strpos, split_part, to_hex and md5
        // used to stand here too -- and did damage: a registration without a signature counts as
        // colliding with everything and replaced the catalog entry together with its argument
        // list. That switched off the arity check on DuckDB for exactly those four. Measured:
        // strpos(c.phone) with one argument instead of two went through and produced invalid SQL,
        // while upper(a, b) was rejected correctly. Of all dialects, the one that writes the shared
        // goldens. The catalog already registers all four with the DuckDB spellings.
        registry.register(new FunctionDefinition("printf",     ReturnTypes.TEXT));
        registry.register(new FunctionDefinition("split",      ReturnTypes.TEXT));
        registry.register(new FunctionDefinition("sha256",     ReturnTypes.TEXT));


        // bitwise: bit_and/or/xor aggregate over rows in DuckDB; bit_count is a plain scalar
        for (String name : java.util.List.of("bit_and", "bit_or", "bit_xor")) {
            registry.register(new FunctionDefinition(name, ReturnTypes.INTEGER, FunctionKind.AGGREGATE));
        }
        registry.register(new FunctionDefinition("bit_count", ReturnTypes.INTEGER));

        // to_date(value) | to_date(value, format) | to_date(ts, tz) | to_date(year, month, day)
        registry.register(new FunctionDefinition("to_date", ReturnTypes.DATE)
                .args(FunctionArg.arg("value", Families.TEMPORAL_OR_TEXT))
                .template("CAST({0} AS DATE)"));
        registry.register(new FunctionDefinition("to_date", ReturnTypes.DATE) {
            @Override
            protected String renderBody(SqlSelectRenderer renderer, Function function, int indent) {
                String a0 = renderer.toSql(function.getArguments().get(0), indent);
                String a1 = renderer.toSql(function.getArguments().get(1), indent);
                TypeDescriptor t0 = renderer.resolveType(function.getArguments().get(0));
                if (t0 != null && CoreTypeFamily.TIMESTAMP.equals(t0.getTypeFamily()))
                    return "timezone(" + a1 + ", " + a0 + "::TIMESTAMPTZ)::DATE";
                return "strptime(" + a0 + ", " + translateKQLFormat(a1) + ")::DATE";
            }
        }.args(FunctionArg.arg("value", Families.TEMPORAL_OR_TEXT), FunctionArg.arg("format", CoreTypeFamily.TEXT)));
        registry.register(new FunctionDefinition("to_date", ReturnTypes.DATE)
                .args(FunctionArg.arg("year", CoreTypeFamily.INTEGER), FunctionArg.arg("month", CoreTypeFamily.INTEGER), FunctionArg.arg("day", CoreTypeFamily.INTEGER))
                .template("MAKE_DATE({0}, {1}, {2})"));

        // to_time(value) | to_time(value, format) | to_time(ts, tz) | to_time(hour, minute, second)
        registry.register(new FunctionDefinition("to_time", ReturnTypes.TIME)
                .args(FunctionArg.arg("value", Families.TEMPORAL_OR_TEXT))
                .template("CAST({0} AS TIME)"));
        registry.register(new FunctionDefinition("to_time", ReturnTypes.TIME) {
            @Override
            protected String renderBody(SqlSelectRenderer renderer, Function function, int indent) {
                String a0 = renderer.toSql(function.getArguments().get(0), indent);
                String a1 = renderer.toSql(function.getArguments().get(1), indent);
                TypeDescriptor t0 = renderer.resolveType(function.getArguments().get(0));
                if (t0 != null && CoreTypeFamily.TIMESTAMP.equals(t0.getTypeFamily()))
                    return "timezone(" + a1 + ", " + a0 + "::TIMESTAMPTZ)::TIME";
                return "strptime(" + a0 + ", " + translateKQLFormat(a1) + ")::TIME";
            }
        }.args(FunctionArg.arg("value", Families.TEMPORAL_OR_TEXT), FunctionArg.arg("format", CoreTypeFamily.TEXT)));
        registry.register(new FunctionDefinition("to_time", ReturnTypes.TIME)
                .args(FunctionArg.arg("hour", CoreTypeFamily.INTEGER), FunctionArg.arg("minute", CoreTypeFamily.INTEGER), FunctionArg.arg("second", Families.NUMERIC))
                .template("MAKE_TIME({0}, {1}, {2}::DOUBLE)"));

        // to_timestamp(value) | to_timestamp(value, format) | to_timestamp(value, format, tz)
        // | to_timestamp(year, month, day, hour, minute, second) | to_timestamp(year, month, day, hour, minute, second, tz)
        registry.register(new FunctionDefinition("to_timestamp", ReturnTypes.TIMESTAMP)
                .args(FunctionArg.arg("value", Families.TEMPORAL_OR_TEXT))
                .template("CAST({0} AS TIMESTAMP)"));
        registry.register(new FunctionDefinition("to_timestamp", ReturnTypes.TIMESTAMP) {
            @Override
            protected String renderBody(SqlSelectRenderer renderer, Function function, int indent) {
                return "strptime(" + renderer.toSql(function.getArguments().get(0), indent)
                        + ", " + translateKQLFormat(renderer.toSql(function.getArguments().get(1), indent)) + ")";
            }
        }.args(FunctionArg.arg("value", Families.TEMPORAL_OR_TEXT), FunctionArg.arg("format", CoreTypeFamily.TEXT)));
        registry.register(new FunctionDefinition("to_timestamp", ReturnTypes.TIMESTAMP) {
            @Override
            protected String renderBody(SqlSelectRenderer renderer, Function function, int indent) {
                return "timezone(" + renderer.toSql(function.getArguments().get(2), indent)
                        + ", strptime(" + renderer.toSql(function.getArguments().get(0), indent)
                        + ", " + translateKQLFormat(renderer.toSql(function.getArguments().get(1), indent)) + ")::TIMESTAMPTZ)";
            }
        }.args(FunctionArg.arg("value", Families.TEMPORAL_OR_TEXT), FunctionArg.arg("format", CoreTypeFamily.TEXT), FunctionArg.arg("tz", CoreTypeFamily.TEXT)));
        registry.register(new FunctionDefinition("to_timestamp", ReturnTypes.TIMESTAMP)
                .args(FunctionArg.arg("year", CoreTypeFamily.INTEGER), FunctionArg.arg("month", CoreTypeFamily.INTEGER), FunctionArg.arg("day", CoreTypeFamily.INTEGER),
                      FunctionArg.arg("hour", CoreTypeFamily.INTEGER), FunctionArg.arg("minute", CoreTypeFamily.INTEGER), FunctionArg.arg("second", Families.NUMERIC))
                .template("MAKE_TIMESTAMP({0}, {1}, {2}, {3}, {4}, {5})"));
        registry.register(new FunctionDefinition("to_timestamp", ReturnTypes.TIMESTAMP)
                .args(FunctionArg.arg("year", CoreTypeFamily.INTEGER), FunctionArg.arg("month", CoreTypeFamily.INTEGER), FunctionArg.arg("day", CoreTypeFamily.INTEGER),
                      FunctionArg.arg("hour", CoreTypeFamily.INTEGER), FunctionArg.arg("minute", CoreTypeFamily.INTEGER), FunctionArg.arg("second", Families.NUMERIC),
                      FunctionArg.arg("tz", CoreTypeFamily.TEXT))
                .template("timezone({6}, MAKE_TIMESTAMP({0}, {1}, {2}, {3}, {4}, {5})::TIMESTAMPTZ)"));

        // parse_date/time/timestamp(value, format) → strptime(value, format)::<TYPE>
        //
        // Through parseWithMask, as MariaDB and Trino already do it, rather than hand-rolled: these
        // three were the only definitions in this file registered without .args(...), and a
        // signature is what the rest of the machinery keys off. FunctionDefinition.checkArity is a
        // no-op without one, so the hand-written "requires two arguments" guards were the entire
        // arity check here -- and they answered in a shape no other function uses ("parse_date
        // requires two arguments: value, format" against the catalog's "parse_date expects
        // (value, format), got 1 arguments"). FunctionRegistry.collides is the sharper edge: it
        // treats a signature-less definition as colliding with *everything* of that name, so these
        // three silently dropped every other overload registered before them. One overload each
        // today, so nothing was lost -- but adding a second one would have vanished without a word.
        // The guards go with the hand-rolling; declaring the signature is what makes them dead.
        registry.register(DateTimeFunctions.parseWithMask("parse_date", ReturnTypes.DATE,
                DuckdbBaseDialect::translateKQLFormat, (v, f) -> "strptime(" + v + ", " + f + ")::DATE"));
        registry.register(DateTimeFunctions.parseWithMask("parse_time", ReturnTypes.TIME,
                DuckdbBaseDialect::translateKQLFormat, (v, f) -> "strptime(" + v + ", " + f + ")::TIME"));
        registry.register(DateTimeFunctions.parseWithMask("parse_timestamp", ReturnTypes.TIMESTAMP,
                DuckdbBaseDialect::translateKQLFormat, (v, f) -> "strptime(" + v + ", " + f + ")"));

        // to_interval(value, unit) | to_interval(years, months, days, hours, minutes, seconds)
        // unit is one of: YEAR, MONTH, WEEK, DAY, HOUR, MINUTE, SECOND, MILLISECOND (case-insensitive)
        registry.register(new FunctionDefinition("to_interval", ReturnTypes.INTERVAL) {
            @Override
            protected String renderBody(SqlSelectRenderer renderer, Function function, int indent) {
                String value = renderer.toSql(function.getArguments().get(0), indent);
                String unit  = renderer.toSql(function.getArguments().get(1), indent);
                return toIntervalUnit(value, unit);
            }
        }.args(FunctionArg.arg("value", Families.NUMERIC), FunctionArg.arg("unit", CoreTypeFamily.TEXT)));
        registry.register(new FunctionDefinition("to_interval", ReturnTypes.INTERVAL)
                .args(FunctionArg.arg("years", CoreTypeFamily.INTEGER), FunctionArg.arg("months", CoreTypeFamily.INTEGER), FunctionArg.arg("days", CoreTypeFamily.INTEGER),
                      FunctionArg.arg("hours", CoreTypeFamily.INTEGER), FunctionArg.arg("minutes", CoreTypeFamily.INTEGER), FunctionArg.arg("seconds", Families.NUMERIC))
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
        return "TIME '" + SqlDialect.plainTime(time) + "'";
    }

    @Override
    public String timestampLiteral(LocalDateTime dateTime) {
        return "TIMESTAMP '" + SqlDialect.plainTimestamp(dateTime) + "'";
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
        // A DATE compared with a TIMESTAMP used to get `(…)::DATE` here, demoting the timestamp by
        // discarding its time. docs/TEMPORAL.md says the opposite — "the DATE is promoted to
        // midnight (00:00:00)" — and so do the other seven engines, which leave the promotion to the
        // database. Measured, `order_date >= "2022-07-04 12:00:00"` matched a row dated 2022-07-04
        // here and nowhere else: DuckDB compared 2022-07-04 with 2022-07-04, everyone else midnight
        // with noon. Since DuckDB writes the shared goldens, its answer was the one recorded.
        return SqlDialect.super.renderComparisonOperand(renderer, expression, leftType, rightType, indent);
    }

    // TIME(seconds-from-midnight) output + arithmetic now render raw integers (SqlDialect default);
    // JdbcDatabase#read decodes the integer to a LocalTime.

}
