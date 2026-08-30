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

import ai.koryki.mariadb.iql.validate.MariadbValidator;
import ai.koryki.catalog.types.CoreTypeEncoding;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.Families;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.WallClockEncoding;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.functions.*;
import ai.koryki.iql.functions.catalog.DateTimeFunctions;
import ai.koryki.iql.query.Duration;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
import ai.koryki.iql.typing.TimeEncodings;

import java.util.List;

public class MariadbDialect implements SqlDialect {

    public static final SqlDialect INSTANCE = new MariadbDialect();

    private MariadbDialect() {
    }

    /** Wall-clock(zone) → model zone via {@code CONVERT_TZ} (named zones; server tz tables must be loaded). */
    @Override
    public String wallClockToModelZone(String columnSql,
                                       WallClockEncoding enc, java.time.ZoneId modelZone) {
        String decl = "'" + enc.getZone().getId() + "'";
        String model = "'" + modelZone.getId() + "'";
        if (CoreTypeFamily.DATE.equals(enc.family())) {
            return "CAST(CONVERT_TZ(CAST(" + columnSql + " AS DATETIME), " + decl + ", " + model + ") AS DATE)";
        }
        return "CONVERT_TZ(" + columnSql + ", " + decl + ", " + model + ")";
    }

    /**
     * MariaDB and MySQL reserve these on top of the standard's.
     *
     * <p>{@code key} and {@code index} are the ones a schema author trips over; {@code rank},
     * {@code lead} and {@code lag} became reserved when the window functions arrived and catch
     * older schemas out. {@code interval} is reserved here and nowhere else among the eight.
     */
    private static final java.util.Set<String> RESERVED = java.util.Set.of(
            "accessible", "analyze", "asensitive", "before", "call", "cascade", "change",
            "condition", "continue", "database", "databases", "day_hour", "day_microsecond",
            "day_minute", "day_second", "delayed", "describe", "deterministic", "distinctrow",
            "div", "dual", "each", "elseif", "enclosed", "escaped", "exit", "explain", "float4",
            "float8", "force", "fulltext", "general", "groups", "high_priority", "hour_microsecond",
            "hour_minute", "hour_second", "if", "ignore", "index", "infile", "inout", "int1",
            "int2", "int3", "int4", "int8", "interval", "iterate", "key", "keys", "kill", "lag",
            "lead", "leave", "lines", "load", "localtime", "localtimestamp", "lock", "long",
            "longblob", "longtext", "loop", "low_priority", "match", "mediumblob", "mediumint",
            "mediumtext", "middleint", "minute_microsecond", "minute_second", "mod", "modifies",
            "no_write_to_binlog", "optimize", "option", "optionally", "out", "outfile", "purge",
            "range", "rank", "read", "reads", "read_write", "regexp", "release", "rename",
            "repeat", "replace", "require", "resignal", "restrict", "return", "rlike", "rows",
            "schemas", "second_microsecond", "sensitive", "separator", "show", "signal", "spatial",
            "specific", "sql_big_result", "sql_calc_found_rows", "sql_small_result", "sqlexception",
            "sqlstate", "sqlwarning", "ssl", "starting", "straight_join", "terminated",
            "tinyblob", "tinyint", "tinytext", "trigger", "undo", "unlock", "unsigned", "usage",
            "use", "utc_date", "utc_time", "utc_timestamp", "varbinary", "varcharacter", "while",
            "write", "xor", "year_month", "zerofill");

    @Override
    public boolean isReserved(String name) {
        return SqlDialect.super.isReserved(name)
                || RESERVED.contains(name.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Backticks.
     *
     * <p>MariaDB and MySQL read a double-quoted name as a string literal unless {@code ANSI_QUOTES}
     * is set in the session, and whether it is set is not ours to know. A backtick means the same
     * thing in both modes.
     */
    @Override
    public String quote(String identifier) {
        return '`' + identifier.replace("`", "``") + '`';
    }

    @Override
    public String zoneShiftTimestamp(String valueSql, String fromZoneSql, String toZoneSql) {
        return "CONVERT_TZ(" + valueSql + ", " + fromZoneSql + ", " + toZoneSql + ")";
    }

    /**
     * Doubles backslashes. MariaDB reads them as escapes <em>inside</em> string literals, unlike
     * the SQL standard and unlike every other dialect supported here. A written {@code '\d+'}
     * therefore reached the database as {@code d+}.
     *
     * <p>Measured on {@code expression/backslash_literal}: {@code regexp_substr(phone, '\d+')}
     * returned nothing here while duckdb, postgresql, oracle and trino returned {@code 030}. No
     * error, just an empty field — the nastiest kind, because it looks like a missing match. Every
     * string is affected; a regex only makes it most visible.
     *
     * <p>At this point {@code \'} has already become {@code ''} (SqlSelectRenderer), so the
     * remaining backslashes are the author's intent. The alternative would be
     * {@code sql_mode=NO_BACKSLASH_ESCAPES} on the connection, but that changes the behaviour of
     * the whole session instead of only the literals koryki emits itself.
     */
    @Override
    public String textLiteral(String quoted) {
        return quoted.replace("\\", "\\\\");
    }

    private static final FunctionRenderer FUNCTION_RENDERER = buildFunctionRenderer();

    @Override
    public FunctionRenderer getFunctionRenderer() {
        return FUNCTION_RENDERER;
    }

    private static FunctionRenderer buildFunctionRenderer() {
        FunctionRegistry registry = StandardFunctions.registry();
        // to_text is the rolled-out, tested function: keep its dialect cast-type override.
        // MariaDB has no IS [NOT] DISTINCT FROM (verified: syntax error); <=> is its null-safe
        // equal, so the distinctness operator is its negation.
        registry.override("DISTINCT", "NOT ({0} <=> {1})");
        registry.overrideAll("to_text", "CAST({0} AS CHAR)");
        // random() is documented as 0.0 <= x < 1.0 and went to every engine verbatim; measured,
        // MariaDB answered "FUNCTION demo.random does not exist". RAND() is its spelling.
        registry.override("random", "RAND()");
        registry.override("trunc", 1, "TRUNCATE({0}, 0)");
        registry.override("trunc", 2, "TRUNCATE({0}, {1})");
        registry.override("days_between",   "TIMESTAMPDIFF(DAY, {0}, {1})");
        registry.override("months_between", "TIMESTAMPDIFF(MONTH, {0}, {1})");
        registry.override("month_end",   "LAST_DAY({0})");
        registry.override("week_begin",  "(DATE({0}) - INTERVAL WEEKDAY({0}) DAY)");
        registry.override("week_end",    "(DATE({0}) - INTERVAL WEEKDAY({0}) DAY + INTERVAL 6 DAY)");
        registry.override("quarter_end", "LAST_DAY(MAKEDATE(YEAR({0}), 1) + INTERVAL (QUARTER({0}) * 3 - 1) MONTH)");
        registry.override("year_end",    "LAST_DAY(MAKEDATE(YEAR({0}), 1) + INTERVAL 11 MONTH)");

        // string functions (MySQL/MariaDB spellings)
        registry.override("char_length", "CHAR_LENGTH({0})");          // character count, not bytes
        registry.override("to_hex",      "HEX({0})");
        // MariaDB has no character-set trim: TRIM(BOTH x FROM s) removes a substring, not a set
        // of characters, and would give the wrong 'helloba' for trim('abhelloba','ab'). This used
        // to carry only the one-argument template, so the two-argument call died on SqlTemplate's
        // too-many-operands guard -- named, but without a position, without the UNSUPPORTED
        // category, and therefore with a hand-written ignore= marker on the fixture whose own
        // comment had to explain that it is precisely NOT a difference in the result.
        registry.override("trim",        "TRIM({0})");
        registry.unsupported("trim", 2);
        registry.override("starts_with", "(LEFT({0}, CHAR_LENGTH({1})) = {1})");
        registry.override("overlay",     "INSERT({0}, {2}, {3}, {1})");
        registry.override("string_agg", 2, "GROUP_CONCAT({0} SEPARATOR {1})");
        registry.override("string_agg", 3, "GROUP_CONCAT({0} ORDER BY {2} SEPARATOR {1})");
        registry.override("week",      "WEEKOFYEAR({0})");
        registry.override("dayofweek", "(WEEKDAY({0}) + 1)");
        // MariaDB's LAG/LEAD take only (expr, offset) -- they have no third parameter for the
        // default used on rows without a neighbour. Measured: a syntax error right at " 0)".
        // Emulating it as COALESCE(LAG(x, n), default) does not work: the renderer appends the
        // OVER clause AFTER the body, so it would land outside the COALESCE.
        registry.unsupported("lag", 3);
        registry.unsupported("lead", 3);
        registry.windowUnsupported("count_distinct");   // COUNT(DISTINCT ...) cannot be a window function here
        // Measured: MariaDB takes the comma list and rejects the row constructor.
        registry.override("count_distinct", 2, "COUNT(DISTINCT {*})");
        registry.windowUnsupported("string_agg");   // MySQL/MariaDB: GROUP_CONCAT cannot be a window function
        registry.override("split_part",  "SUBSTRING_INDEX(SUBSTRING_INDEX({0}, {1}, {2}), {1}, -1)");
        registry.override("regexp_like", "({0} REGEXP {1})");

        // numeric/temporal casts — MySQL CAST target types (no BIGINT/SMALLINT/BOOLEAN/TIMESTAMP)
        // concat skips NULL arguments -- so documented, and so duckdb, postgresql, oracle, sqlite
        // and mssql behave. Here CONCAT returned NULL for the WHOLE result on a NULL argument,
        // measured on concat_null. concat_ws skips NULL by definition, and with an empty separator
        // it is the same as concat.
        registry.override("concat", "concat_ws('', {*})");
        registry.override("to_bigint",    "CAST({0} AS SIGNED)");
        registry.override("to_smallint",  "CAST({0} AS SIGNED)");
        registry.override("to_boolean",   "({0} <> 0)");
        registry.override("to_timestamp", "CAST({0} AS DATETIME)");

        // date/time construction & distance
        registry.override("make_time",      "MAKETIME({0}, {1}, {2})");
        registry.override("make_date",      "STR_TO_DATE(CONCAT({0}, '-', {1}, '-', {2}), '%Y-%m-%d')");
        registry.override("make_timestamp", "STR_TO_DATE(CONCAT({0}, '-', {1}, '-', {2}, ' ', "
                + "{3}, ':', {4}, ':', {5}), '%Y-%m-%d %H:%i:%s')");
        registry.override("years_between",  "TIMESTAMPDIFF(YEAR, {0}, {1})");   // canonical uses PG age()
        registry.override("clock_now",      "SYSDATE()");                       // real execution time, distinct from NOW()=CURRENT_TIMESTAMP

        // period boundaries — the canonical *_begin templates emit a literal date_trunc(...) call,
        // so they need their own MySQL rendering (they don't route through the date_trunc override).
        registry.override("minute_begin",  "CAST(DATE_FORMAT({0}, '%Y-%m-%d %H:%i:00') AS DATETIME)");
        registry.override("hour_begin",    "CAST(DATE_FORMAT({0}, '%Y-%m-%d %H:00:00') AS DATETIME)");
        registry.override("day_begin",     "CAST(DATE({0}) AS DATETIME)");
        registry.override("month_begin",   "(MAKEDATE(YEAR({0}), 1) + INTERVAL (MONTH({0}) - 1) MONTH)");
        registry.override("quarter_begin", "(MAKEDATE(YEAR({0}), 1) + INTERVAL (QUARTER({0}) - 1) * 3 MONTH)");
        registry.override("year_begin",    "MAKEDATE(YEAR({0}), 1)");

        // date_trunc itself (invoked directly)
        registry.register(new FunctionDefinition("date_trunc", ReturnTypes.ARG1) {
            @Override
            protected String renderBody(SqlSelectRenderer renderer, Function function, int indent) {
                String unit = renderer.toSql(function.getArguments().get(0), indent).replace("'", "");
                String d = renderer.toSql(function.getArguments().get(1), indent);
                return switch (unit) {
                    case "minute"  -> "CAST(DATE_FORMAT(" + d + ", '%Y-%m-%d %H:%i:00') AS DATETIME)";
                    case "hour"    -> "CAST(DATE_FORMAT(" + d + ", '%Y-%m-%d %H:00:00') AS DATETIME)";
                    case "day"     -> "CAST(DATE(" + d + ") AS DATETIME)";
                    case "month"   -> "(MAKEDATE(YEAR(" + d + "), 1) + INTERVAL (MONTH(" + d + ") - 1) MONTH)";
                    case "quarter" -> "(MAKEDATE(YEAR(" + d + "), 1) + INTERVAL (QUARTER(" + d + ") - 1) * 3 MONTH)";
                    case "year"    -> "MAKEDATE(YEAR(" + d + "), 1)";
                    default -> throw new UnsupportedOperationException(
                            "date_trunc unit not supported by MariaDB: " + unit);
                };
            }
        }.args(FunctionArg.arg("unit", CoreTypeFamily.TEXT), FunctionArg.arg("value", Families.TEMPORAL)));

        // to_char via DATE_FORMAT (format mask translated to MySQL tokens)
        registry.register(new FunctionDefinition("to_char", ReturnTypes.TEXT) {
            @Override
            protected String renderBody(SqlSelectRenderer renderer, Function function, int indent) {
                return "DATE_FORMAT(" + renderer.toSql(function.getArguments().get(0), indent)
                        + ", " + mysqlFormat(renderer.toSql(function.getArguments().get(1), indent)) + ")";
            }
        }.args(FunctionArg.arg("value", Families.ANY), FunctionArg.arg("format", CoreTypeFamily.TEXT)));

        // parse_date/time/timestamp(value, format) → STR_TO_DATE, which returns DATETIME;
        // DATE()/TIME() narrow it to the declared return type.
        registry.register(DateTimeFunctions.parseWithMask("parse_date", ReturnTypes.DATE,
                MariadbDialect::mysqlFormat, (v, f) -> "DATE(STR_TO_DATE(" + v + ", " + f + "))"));
        registry.register(DateTimeFunctions.parseWithMask("parse_time", ReturnTypes.TIME,
                MariadbDialect::mysqlFormat, (v, f) -> "TIME(STR_TO_DATE(" + v + ", " + f + "))"));
        registry.register(DateTimeFunctions.parseWithMask("parse_timestamp", ReturnTypes.TIMESTAMP,
                MariadbDialect::mysqlFormat, (v, f) -> "STR_TO_DATE(" + v + ", " + f + ")"));

        // MySQL/MariaDB have no translate, format-mask to_number, initcap or regexp_count
        for (String fn : List.of("translate", "to_number", "initcap", "regexp_count")) {
            registry.unsupported(fn);
        }
        return registry;
    }

    /** Translate a canonical KQL format literal to MySQL DATE_FORMAT tokens; non-literals pass through. */
    private static String mysqlFormat(String rendered) {
        return FormatMask.translate(rendered, FormatMask.MYSQL);
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
    /**
     * {@code DATE - DATE} is numeric yyyymmdd subtraction here — the same trap as {@code DATE + INT}
     * noted above, and it agrees with the day count only within a single month.
     */
    @Override
    public String dateDiffDays(String leftSql, String rightSql) {
        return "TIMESTAMPDIFF(DAY, " + rightSql + ", " + leftSql + ")";
    }

    @Override
    public String renderEncodedArithmetic(SqlSelectRenderer renderer, String operator,
            String leftSql, TypeDescriptor leftType, Expression right, TypeDescriptor rightType, int indent) {
        String diff = renderTemporalDiff(renderer, operator, leftSql, leftType, right, rightType, indent);
        if (diff != null) {
            return diff;
        }
        java.util.Optional<String> time = TimeEncodings
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
            // Calendar units are one step, not one per unit -- see SqlDialect.calendarFolded.
            for (Duration.Component c : SqlDialect.calendarFolded(dur)) {
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


    /**
     * MariaDB has {@code MOD} and {@code FLOOR} but not {@code EXTRACT(EPOCH …)};
     * {@code TIME_TO_SEC} is the direct equivalent for a 'HH:MM:SS' text time. {@code CAST(… AS
     * INTEGER)} is not valid either, so the integer form stays in {@code FLOOR}.
     */
    @Override
    public String timeColumnAsSeconds(String columnSql, TypeDescriptor timeType) {
        var enc = timeType != null ? timeType.getTypeEncoding() : null;
        if (CoreTypeEncoding.TIME_FROM_INTEGER.equals(enc)) {
            return "FLOOR(" + columnSql + " / 10000) * 3600"
                 + " + MOD(FLOOR(" + columnSql + " / 100), 100) * 60"
                 + " + MOD(" + columnSql + ", 100)";
        }
        if (CoreTypeEncoding.TIME_FROM_STRING.equals(enc)) {
            return "TIME_TO_SEC(" + columnSql + ")";
        }
        return SqlDialect.super.timeColumnAsSeconds(columnSql, timeType);
    }


    /** Negating distinctness gives plain {@code <=>} — no NOT wrapper needed. */
    @Override
    public String negatedOperatorTemplate(String op) {
        return "DISTINCT".equals(op) ? "{0} <=> {1}" : SqlDialect.super.negatedOperatorTemplate(op);
    }


    @Override
    public java.util.List<ai.koryki.iql.Collector<java.util.List<ai.koryki.iql.validate.Violation>>>
            validators(ai.koryki.iql.validate.ValidationContext context) {
        return java.util.List.of(new MariadbValidator(context));
    }

    /** MariaDB knows INTERVAL only inside date arithmetic; there is no interval value type. */
    @Override
    public ai.koryki.iql.SqlDialect.IntervalSupport intervalSupport() {
        return ai.koryki.iql.SqlDialect.IntervalSupport.NONE;
    }
}
