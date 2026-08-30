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
package ai.koryki.postgresql.iql;

import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.WallClockEncoding;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.functions.FunctionArg;
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionKind;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.FunctionRenderer;
import ai.koryki.iql.functions.ReturnTypes;
import ai.koryki.iql.functions.StandardFunctions;

public class PostgreSqlDialect implements SqlDialect {

    public static final SqlDialect INSTANCE = new PostgreSqlDialect();

    private PostgreSqlDialect() {
    }

    /** Wall-clock(zone) → model zone. PostgreSQL's {@code AT TIME ZONE} flips naive - instant like DuckDB. */
    @Override
    public String wallClockToModelZone(String columnSql,
                                       WallClockEncoding enc, java.time.ZoneId modelZone) {
        return SqlDialect.atTimeZoneToModelZone(columnSql, enc, modelZone);
    }

    @Override
    public String zoneShiftTimestamp(String valueSql, String fromZoneSql, String toZoneSql) {
        return SqlDialect.atTimeZoneShift(valueSql, fromZoneSql, toZoneSql);
    }

    private static final FunctionRenderer FUNCTION_RENDERER = buildFunctionRenderer();

    @Override
    public FunctionRenderer getFunctionRenderer() {
        return FUNCTION_RENDERER;
    }

    private static FunctionRenderer buildFunctionRenderer() {
        FunctionRegistry registry = StandardFunctions.registry();

        // date-part extraction
        StandardFunctions.registerExtractParts(registry);


        // PostgreSQL-specific string functions (the portable ones moved to the canonical catalog)
        registry.windowUnsupported("count_distinct");   // COUNT(DISTINCT ...) cannot be a window function here
        registry.register(new FunctionDefinition("format", ReturnTypes.TEXT));
        registry.register(new FunctionDefinition("sha256", ReturnTypes.TEXT));

        // bitwise aggregates
        for (String name : java.util.List.of("bit_and", "bit_or", "bit_xor")) {
            registry.register(new FunctionDefinition(name, ReturnTypes.INTEGER, FunctionKind.AGGREGATE));
        }

        // parse_date/time/timestamp(value, format) → TO_DATE / TO_TIMESTAMP
        // Distances and Rolling (TEMPORAL.md) — months/years_between and the
        // roll-down family inherit the canonical age()/date_trunc templates.

        registry.register(StandardFunctions.parseTwoArg("parse_date",      ReturnTypes.DATE,      "TO_DATE"));
        // TO_TIMESTAMP returns timestamptz, so reading it back applies a zone conversion and the
        // parsed wall-clock shifts. Cast to a plain timestamp, as parse_time already does.
        registry.register(new FunctionDefinition("parse_timestamp", ReturnTypes.TIMESTAMP)
                .category(FunctionCategory.DATETIME)
                .args(FunctionArg.arg("value", CoreTypeFamily.TEXT), FunctionArg.arg("format", CoreTypeFamily.TEXT))
                .doc("Parses *value* using the *format* mask.")
                .template("TO_TIMESTAMP({0}, {1})::TIMESTAMP"));
        registry.register(new FunctionDefinition("parse_time", ReturnTypes.TIME)
                .args(FunctionArg.arg("value", CoreTypeFamily.TEXT), FunctionArg.arg("format", CoreTypeFamily.TEXT))
                .template("TO_TIMESTAMP({0}, {1})::TIME"));

        // round(double precision, integer) does not exist in PostgreSQL — only round(numeric, int).
        // Replace every match, not just the first. PostgreSQL and DuckDB replace only the first by
        // default -- MariaDB, Oracle and Trino replace all. The g modifier evens that out; the
        // promise is in the catalog, its price is here.
        registry.override("regexp_replace", "regexp_replace({0}, {1}, {2}, 'g')");
        registry.override("round", 2, "round(CAST({0} AS numeric), {1})");
        // Same reason as one line above: trunc(double precision, integer) does not exist in
        // PostgreSQL, only trunc(numeric, integer). Overlooked when round was fixed.
        registry.override("trunc", 2, "trunc(CAST({0} AS numeric), {1})");
        // complete days inside the interval (24-hour periods), not midnights crossed — measured, the two readings split the engines five to three
        // PostgreSQL has a real BOOLEAN type and refuses CAST(smallint AS boolean)
        // ("cannot cast type smallint to boolean"). The comparison is the portable form, and
        // MariaDB and SQLite already render it this way. to_boolean only accepts a number or
        // text (Families.NUMBER_OR_TEXT), so a genuine boolean never reaches this.
        registry.override("to_boolean", "({0} <> 0)");
        registry.override("days_between",
                "CAST(trunc(EXTRACT(EPOCH FROM (CAST({1} AS timestamp) - CAST({0} AS timestamp))) / 86400.0) AS INTEGER)");
        registry.override("month_end",   "CAST(date_trunc('month', {0}) + INTERVAL '1 month - 1 day' AS DATE)");
        registry.override("week_end",    "CAST(date_trunc('week', {0}) + INTERVAL '6 days' AS DATE)");
        registry.override("quarter_end", "CAST(date_trunc('quarter', {0}) + INTERVAL '3 months - 1 day' AS DATE)");
        registry.override("year_end",    "CAST(date_trunc('year', {0}) + INTERVAL '1 year - 1 day' AS DATE)");
        // registerExtractParts renders second as EXTRACT(SECOND FROM x), and that is the one date
        // part here that is not whole: for 12:14:40.75 it answers 40.750000, where the reference
        // (DuckDB second(), and MariaDB/Snowflake/Trino/SQLite/SQL Server with it) answers 40. The
        // catalog declares INTEGER, so the fraction was a lie in two places at once -- the declared
        // type and the value. It stayed invisible because check_temporal holds only whole seconds.
        // trunc, not a bare cast: CAST(40.75 AS INTEGER) rounds to 41, which is a second that never
        // happened. hour and minute need no such treatment; EXTRACT gives those as whole numbers.
        registry.override("second", "CAST(trunc(EXTRACT(SECOND FROM {0})) AS INTEGER)");
        // The canonical year_month is year(x)/month(x) literal SQL, which PostgreSQL has no
        // functions for -- it spells the parts as EXTRACT, and since PG 14 EXTRACT answers with
        // numeric rather than an integer type, so the cast is what keeps the declared INTEGER true.
        registry.override("year_month",
                "CAST(EXTRACT(YEAR FROM {0}) * 100 + EXTRACT(MONTH FROM {0}) AS INTEGER)");

        // Postgres interval arithmetic (canonical uses MySQL `INTERVAL (n) DAY`) and type/name spellings.
        registry.override("day_add",   "({0} + {1} * INTERVAL '1 day')");
        registry.override("month_add", "({0} + {1} * INTERVAL '1 month')");
        registry.override("year_add",  "({0} + {1} * INTERVAL '1 year')");
        registry.override("to_double", "CAST({0} AS DOUBLE PRECISION)"); // Postgres has no DOUBLE
        registry.override("to_hex",    "upper(to_hex({0}))");            // Postgres to_hex is lower-case
        // Postgres has three distinct clocks: statement_timestamp() (per statement) → now,
        // clock_timestamp() (real wall-clock, advances mid-statement) → clock_now, and
        // transaction_timestamp()/CURRENT_TIMESTAMP (per transaction, unused here).
        registry.override("now",       "statement_timestamp()");
        registry.override("clock_now", "clock_timestamp()");
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
    public String durationLiteral(ai.koryki.iql.query.Duration duration) {
        return SqlDialect.combinedInterval(duration, " ");   // Postgres parses the space-separated verbose form
    }

    /** Postgres has no {@code make_timestamp}; build a naive TIMESTAMP from the epoch base + seconds. */
    @Override
    public String epochToTimestamp(String expr, java.time.temporal.ChronoUnit unit) {
        return "(TIMESTAMP '1970-01-01 00:00:00' + " + SqlDialect.secondsFromEpoch(expr, unit) + " * INTERVAL '1 second')";
    }

    /** PostgreSQL division truncates when both operands are integers. */
    @Override
    public String castToDecimal(String sql) {
        return "CAST(" + sql + " AS numeric)";
    }

}
