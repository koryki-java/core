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

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.WallClockEncoding;
import ai.koryki.iql.NestedSql;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.functions.FunctionArg;
import ai.koryki.iql.functions.FunctionCategory;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionKind;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.FunctionRenderer;
import ai.koryki.iql.functions.ReturnTypes;
import ai.koryki.iql.functions.StandardFunctions;
import ai.koryki.iql.functions.catalog.ListFunctions;
import ai.koryki.iql.query.Selector;

public class PostgreSqlDialect implements SqlDialect {

    public static final SqlDialect INSTANCE = new PostgreSqlDialect();

    private PostgreSqlDialect() {}

    /**
     * Wall-clock(zone) → model zone. PostgreSQL's {@code AT TIME ZONE} flips naive - instant like
     * DuckDB.
     */
    @Override
    public String wallClockToModelZone(
            String columnSql, WallClockEncoding enc, java.time.ZoneId modelZone) {
        return SqlDialect.atTimeZoneToModelZone(columnSql, enc, modelZone);
    }

    @Override
    public String zoneShiftTimestamp(String valueSql, String fromZoneSql, String toZoneSql) {
        return SqlDialect.atTimeZoneShift(valueSql, fromZoneSql, toZoneSql);
    }

    /**
     * {@code list_transform} and {@code list_filter} over {@code generate_subscripts}, which,
     * unlike {@code unnest}, hands over an element of a composite type whole instead of spreading
     * it into columns. The list is evaluated once, in a derived table; the position runs in the
     * order of the list; a list that is NULL stays NULL, as it does for a lambda on DuckDB, where
     * an empty subquery would make it {@code {}}.
     *
     * <pre>
     * CASE WHEN l IS NULL THEN NULL ELSE ARRAY(
     *   SELECT body FROM (SELECT _s._a[_i] AS x, _i AS i FROM (SELECT l AS _a) AS _s,
     *                     generate_subscripts(_s._a, 1) AS _i) AS _u [WHERE test] ORDER BY i) END
     * </pre>
     */
    private static String listWithLambda(
            ai.koryki.iql.SqlSelectRenderer renderer,
            ai.koryki.iql.query.Function function,
            int indent,
            boolean filter) {
        String list = renderer.toSql(function.getArguments().get(0), indent);
        TypeDescriptor returned = renderer.lambdaBodyType(function.getArguments().get(1));
        if (!filter && returned != null && CoreTypeFamily.ARRAY.equals(returned.getTypeFamily())) {
            // an array of arrays is one multi-dimensional array: it cannot be collected from rows
            // of different lengths ("cannot accumulate arrays of different dimensionality"), and
            // it would mean a different type from the list of lists the query asks for
            throw new KorykiaiException(
                    "PostgreSQL cannot return a list of lists from list_transform: its arrays do not nest");
        }
        // the list is named for the NULL check, the elements and the subscripts: once, then
        return NestedSql.bindOnce(
                list,
                ONCE,
                array ->
                        renderer.lambda(
                                function.getArguments().get(1),
                                indent,
                                (names, body) -> {
                                    String element = names.get(0);
                                    String position =
                                            names.size() > 1 ? names.get(1) : "_pos_" + element;
                                    String select = filter ? element : body;
                                    String where = filter ? " WHERE " + body : "";
                                    return "CASE WHEN "
                                            + array
                                            + " IS NULL THEN NULL ELSE ARRAY(SELECT "
                                            + select
                                            + " FROM (SELECT "
                                            + array
                                            + "[_i] AS "
                                            + element
                                            + ", _i AS "
                                            + position
                                            + " FROM generate_subscripts("
                                            + array
                                            + ", 1) AS _i) AS _u"
                                            + where
                                            + " ORDER BY "
                                            + position
                                            + ") END";
                                }));
    }

    /** One row holding a value, to name it more than once: see {@link NestedSql#bindOnce}. */
    private static final String ONCE = "FROM (SELECT {0} AS L) {1}";

    /** A position from the end is the length minus what is left: the array is named twice. */
    private static boolean fromEnd(Selector step) {
        return (step.getIndex() != null && step.getIndex() < 0)
                || (step.getFrom() != null && step.getFrom() < 0)
                || (step.getTo() != null && step.getTo() < 0);
    }

    private static final java.util.regex.Pattern REFERENCE =
            java.util.regex.Pattern.compile(
                    "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?(\\[[^\\]]*\\])*");

    private static final java.util.regex.Pattern PLAIN_NAME =
            java.util.regex.Pattern.compile("[a-z_][a-z0-9_]*");

    /**
     * PostgreSQL arrays count from 1 like koryki, but have no negative positions, and composite
     * types (the structs) have no positions at all; JSON is {@code jsonb} with {@code ->}. A MAP
     * has no counterpart: a map is a jsonb object here, so it arrives as JSON.
     *
     * <p>An array of arrays is a multi-dimensional array: {@code m[2][1]} is one subscript, and
     * {@code m[2]} alone is NULL. A chain has to go through every dimension at once, and a slice of
     * one has no portable meaning, so both are refused instead of rendering a NULL.
     */
    @Override
    public String selection(String base, TypeDescriptor baseType, java.util.List<Selector> steps) {
        String sql = base;
        TypeDescriptor type = baseType;
        for (int i = 0; i < steps.size(); i++) {
            Selector step = steps.get(i);
            if (CoreTypeFamily.ARRAY.equals(type.getTypeFamily())
                    && steps.subList(i, steps.size()).stream().anyMatch(PostgreSqlDialect::fromEnd)
                    && !NestedSql.isReference(sql)) {
                int from = i;
                TypeDescriptor typeHere = type;
                return NestedSql.bindOnce(
                        sql,
                        ONCE,
                        ref -> selection(ref, typeHere, steps.subList(from, steps.size())));
            }
            if (CoreTypeFamily.JSON.equals(type.getTypeFamily())) {
                return jsonPath(sql, steps.subList(i, steps.size()));
            } else if (CoreTypeFamily.STRUCT.equals(type.getTypeFamily())) {
                sql = "(" + sql + ")." + fieldName(type, step);
            } else if (CoreTypeFamily.ARRAY.equals(type.getTypeFamily())) {
                if (CoreTypeFamily.ARRAY.equals(type.element().getTypeFamily())) {
                    return dimensions(sql, type, steps.subList(i, steps.size()));
                }
                sql = arrayStep(sql, step);
            } else {
                throw new KorykiaiException(
                        "PostgreSQL cannot select from " + type.portable() + " with a selector");
            }
            type = ai.koryki.iql.typing.Selectors.step(type, step);
        }
        return sql;
    }

    private static String operand(String sql) {
        return REFERENCE.matcher(sql).matches() ? sql : "(" + sql + ")";
    }

    private static String arrayStep(String sql, Selector step) {
        String array = operand(sql);
        if (step.getIndex() != null) {
            return array + "[" + position(array, step.getIndex(), 1) + "]";
        }
        return array
                + "["
                + (step.getFrom() == null ? "" : position(array, step.getFrom(), 1))
                + ":"
                + (step.getTo() == null ? "" : position(array, step.getTo(), 1))
                + "]";
    }

    /**
     * A position from the end becomes the length minus what is left, as there is no negative one;
     * {@code dimension} is the one the position counts in, for an array of several.
     */
    private static String position(String array, int n, int dimension) {
        if (n > 0) {
            return String.valueOf(n);
        }
        String length = "array_length(" + array + ", " + dimension + ")";
        return n == -1 ? length : length + " - " + (-n - 1);
    }

    /**
     * The declared name of the field, from the type: quoted unless it is a plain lower-case name.
     */
    private static String fieldName(TypeDescriptor type, Selector step) {
        return NestedSql.identifier(NestedSql.fieldName(type, step), PLAIN_NAME);
    }

    private String dimensions(String sql, TypeDescriptor type, java.util.List<Selector> steps) {
        int depth = 0;
        for (TypeDescriptor t = type;
                CoreTypeFamily.ARRAY.equals(t.getTypeFamily());
                t = t.element()) {
            depth++;
        }
        StringBuilder subscripts = new StringBuilder();
        TypeDescriptor t = type;
        int taken = 0;
        while (taken < steps.size()
                && taken < depth
                && CoreTypeFamily.ARRAY.equals(t.getTypeFamily())
                && steps.get(taken).getIndex() != null) {
            subscripts
                    .append('[')
                    .append(position(operand(sql), steps.get(taken).getIndex(), taken + 1))
                    .append(']');
            t = t.element();
            taken++;
        }
        if (taken < depth) {
            throw new KorykiaiException(
                    "PostgreSQL arrays of arrays are multi-dimensional: select a position in every"
                            + " dimension ("
                            + depth
                            + "), as @i@j; a row alone, a slice or a name is NULL or wrong there");
        }
        String result = operand(sql) + subscripts;
        java.util.List<Selector> rest = steps.subList(taken, steps.size());
        return rest.isEmpty() ? result : selection(result, t, rest);
    }

    /**
     * {@code (j -> 'a' -> 'b' -> 0)}: names as keys, positions counted from 0, negative from the
     * end.
     */
    private static String jsonPath(String sql, java.util.List<Selector> steps) {
        StringBuilder path = new StringBuilder("(" + sql);
        for (Selector step : steps) {
            if (step.getIndex() != null) {
                int n = step.getIndex();
                path.append(" -> ").append(n > 0 ? n - 1 : n);
            } else {
                String name = NestedSql.key(step);
                path.append(" -> '").append(name.replace("'", "''")).append("'");
            }
        }
        return path.append(")").toString();
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

        // lists with lambdas: no lambda expressions here, so a subquery over the list; list_reduce
        // has no counterpart without a custom aggregate and is rejected with what to do instead
        registry.register(
                ListFunctions.supported("list_reduce")
                        .unsupported()
                        .unsupportedHint(
                                "PostgreSQL has no fold over an array; sum, min, max or string_agg over the"
                                        + " unnested elements cover the usual cases"));
        for (String name : java.util.List.of("list_transform", "list_filter")) {
            boolean filter = name.equals("list_filter");
            registry.register(
                    new FunctionDefinition(ListFunctions.supported(name)) {
                        @Override
                        protected String renderBody(
                                ai.koryki.iql.SqlSelectRenderer renderer,
                                ai.koryki.iql.query.Function function,
                                int indent) {
                            return listWithLambda(renderer, function, indent, filter);
                        }
                    });
        }

        // PostgreSQL-specific string functions (the portable ones moved to the canonical catalog)
        registry.windowUnsupported(
                "count_distinct"); // COUNT(DISTINCT ...) cannot be a window function here
        registry.register(new FunctionDefinition("format", ReturnTypes.TEXT));
        registry.register(new FunctionDefinition("sha256", ReturnTypes.TEXT));

        // bitwise aggregates
        for (String name : java.util.List.of("bit_and", "bit_or", "bit_xor")) {
            registry.register(
                    new FunctionDefinition(name, ReturnTypes.INTEGER, FunctionKind.AGGREGATE));
        }

        // parse_date/time/timestamp(value, format) → TO_DATE / TO_TIMESTAMP
        // Distances and Rolling (TEMPORAL.md) — months/years_between and the
        // roll-down family inherit the canonical age()/date_trunc templates.

        registry.register(StandardFunctions.parseTwoArg("parse_date", ReturnTypes.DATE, "TO_DATE"));
        // TO_TIMESTAMP returns timestamptz, so reading it back applies a zone conversion and the
        // parsed wall-clock shifts. Cast to a plain timestamp, as parse_time already does.
        registry.register(
                new FunctionDefinition("parse_timestamp", ReturnTypes.TIMESTAMP)
                        .category(FunctionCategory.DATETIME)
                        .args(
                                FunctionArg.arg("value", CoreTypeFamily.TEXT),
                                FunctionArg.arg("format", CoreTypeFamily.TEXT))
                        .doc("Parses *value* using the *format* mask.")
                        .template("TO_TIMESTAMP({0}, {1})::TIMESTAMP"));
        registry.register(
                new FunctionDefinition("parse_time", ReturnTypes.TIME)
                        .args(
                                FunctionArg.arg("value", CoreTypeFamily.TEXT),
                                FunctionArg.arg("format", CoreTypeFamily.TEXT))
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
        // complete days inside the interval (24-hour periods), not midnights crossed — measured,
        // the two readings split the engines five to three
        // PostgreSQL has a real BOOLEAN type and refuses CAST(smallint AS boolean)
        // ("cannot cast type smallint to boolean"). The comparison is the portable form, and
        // MariaDB and SQLite already render it this way. to_boolean only accepts a number or
        // text (Families.NUMBER_OR_TEXT), so a genuine boolean never reaches this.
        registry.override("to_boolean", "({0} <> 0)");
        registry.override(
                "days_between",
                "CAST(trunc(EXTRACT(EPOCH FROM (CAST({1} AS timestamp) - CAST({0} AS timestamp))) / 86400.0) AS INTEGER)");
        registry.override(
                "month_end", "CAST(date_trunc('month', {0}) + INTERVAL '1 month - 1 day' AS DATE)");
        registry.override("week_end", "CAST(date_trunc('week', {0}) + INTERVAL '6 days' AS DATE)");
        registry.override(
                "quarter_end",
                "CAST(date_trunc('quarter', {0}) + INTERVAL '3 months - 1 day' AS DATE)");
        registry.override(
                "year_end", "CAST(date_trunc('year', {0}) + INTERVAL '1 year - 1 day' AS DATE)");
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
        registry.override(
                "year_month",
                "CAST(EXTRACT(YEAR FROM {0}) * 100 + EXTRACT(MONTH FROM {0}) AS INTEGER)");

        // Postgres interval arithmetic (canonical uses MySQL `INTERVAL (n) DAY`) and type/name
        // spellings.
        registry.override("day_add", "({0} + {1} * INTERVAL '1 day')");
        registry.override("month_add", "({0} + {1} * INTERVAL '1 month')");
        registry.override("year_add", "({0} + {1} * INTERVAL '1 year')");
        registry.override("to_double", "CAST({0} AS DOUBLE PRECISION)"); // Postgres has no DOUBLE
        registry.override("to_hex", "upper(to_hex({0}))"); // Postgres to_hex is lower-case
        // Postgres has three distinct clocks: statement_timestamp() (per statement) → now,
        // clock_timestamp() (real wall-clock, advances mid-statement) → clock_now, and
        // transaction_timestamp()/CURRENT_TIMESTAMP (per transaction, unused here).
        registry.override("now", "statement_timestamp()");
        registry.override("clock_now", "clock_timestamp()");
        // No native MEDIAN; PERCENTILE_CONT(0.5) WITHIN GROUP is exactly the same computation, not
        // an approximation -- measured equal to Oracle's native MEDIAN on the same input
        // (2026-09-27). quantile_cont/quantile_disc already render this way by default and need no
        // override here.
        registry.override("median", "PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY {0})");
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
        return SqlDialect.combinedInterval(
                duration, " "); // Postgres parses the space-separated verbose form
    }

    /**
     * Postgres has no {@code make_timestamp}; build a naive TIMESTAMP from the epoch base +
     * seconds.
     */
    @Override
    public String epochToTimestamp(String expr, java.time.temporal.ChronoUnit unit) {
        return "(TIMESTAMP '1970-01-01 00:00:00' + "
                + SqlDialect.secondsFromEpoch(expr, unit)
                + " * INTERVAL '1 second')";
    }

    /** PostgreSQL division truncates when both operands are integers. */
    @Override
    public String castToDecimal(String sql) {
        return "CAST(" + sql + " AS numeric)";
    }
}
