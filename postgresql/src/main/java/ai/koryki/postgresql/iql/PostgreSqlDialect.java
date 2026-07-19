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

import ai.koryki.catalog.types.WallClockEncoding;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.functions.FunctionArg;
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
        registry.register(StandardFunctions.parseTwoArg("parse_timestamp", ReturnTypes.TIMESTAMP, "TO_TIMESTAMP"));
        registry.register(new FunctionDefinition("parse_time", ReturnTypes.TIME)
                .args(FunctionArg.arg("value"), FunctionArg.arg("format"))
                .template("TO_TIMESTAMP({0}, {1})::TIME"));

        registry.override("days_between", "CAST({1}::date - {0}::date AS INTEGER)");
        registry.override("month_end",   "CAST(date_trunc('month', {0}) + INTERVAL '1 month - 1 day' AS DATE)");
        registry.override("quarter_end", "CAST(date_trunc('quarter', {0}) + INTERVAL '3 months - 1 day' AS DATE)");
        registry.override("year_end",    "CAST(date_trunc('year', {0}) + INTERVAL '1 year - 1 day' AS DATE)");
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
}
