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

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.types.CoreTypeEncoding;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.Families;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.WallClockEncoding;
import ai.koryki.iql.NestedSql;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.functions.FormatMask;
import ai.koryki.iql.functions.FunctionArg;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.FunctionRenderer;
import ai.koryki.iql.functions.ReturnTypes;
import ai.koryki.iql.functions.StandardFunctions;
import ai.koryki.iql.functions.catalog.DateTimeFunctions;
import ai.koryki.iql.functions.catalog.ListFunctions;
import ai.koryki.iql.query.Duration;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
import ai.koryki.iql.query.Selector;
import ai.koryki.iql.typing.TimeEncodings;
import java.util.List;
import java.util.Optional;

public class TrinoDialect implements SqlDialect {

    public static final SqlDialect INSTANCE = new TrinoDialect();

    private TrinoDialect() {}

    /**
     * Trino rejects the ISO/{@code T} form from {@code LocalDateTime.toString()}; emit
     * space-separated with seconds.
     */
    @Override
    public String timestampLiteral(java.time.LocalDateTime dateTime) {
        return "TIMESTAMP '" + SqlDialect.plainTimestamp(dateTime) + "'";
    }

    /**
     * Wall-clock(zone) → model zone: {@code with_timezone} reads the naive value as the declared
     * zone.
     */
    @Override
    public String wallClockToModelZone(
            String columnSql, WallClockEncoding enc, java.time.ZoneId modelZone) {
        String decl = "'" + enc.getZone().getId() + "'";
        String model = "'" + modelZone.getId() + "'";
        if (CoreTypeFamily.DATE.equals(enc.family())) {
            return "CAST(with_timezone(CAST("
                    + columnSql
                    + " AS TIMESTAMP), "
                    + decl
                    + ") AT TIME ZONE "
                    + model
                    + " AS DATE)";
        }
        return "CAST(with_timezone("
                + columnSql
                + ", "
                + decl
                + ") AT TIME ZONE "
                + model
                + " AS TIMESTAMP)";
    }

    @Override
    public String zoneShiftTimestamp(String valueSql, String fromZoneSql, String toZoneSql) {
        return "CAST(with_timezone("
                + valueSql
                + ", "
                + fromZoneSql
                + ") AT TIME ZONE "
                + toZoneSql
                + " AS TIMESTAMP)";
    }

    private static String listWithLambda(
            String name, SqlSelectRenderer renderer, Function function, int indent) {
        String list = renderer.toSql(function.getArguments().get(0), indent);
        // the position and the fold name the list several times
        boolean repeated =
                name.equals("list_reduce")
                        || function.getArguments().get(1).getLambda().getParameters().size() > 1;
        return repeated
                ? NestedSql.bindOnce(
                        list, ONCE, ref -> lambdaOver(name, renderer, function, ref, indent))
                : lambdaOver(name, renderer, function, list, indent);
    }

    private static String lambdaOver(
            String name, SqlSelectRenderer renderer, Function function, String list, int indent) {
        return renderer.lambda(
                function.getArguments().get(1),
                indent,
                (names, body) -> {
                    String head =
                            names.size() == 1 ? names.get(0) : "(" + String.join(", ", names) + ")";
                    switch (name) {
                        case "list_transform":
                            if (names.size() == 1) {
                                return "transform(" + list + ", " + head + " -> " + body + ")";
                            }
                            // the position: the list zipped with 1..n. sequence(1, 0) counts down
                            // and fails on an empty list, so the sequence is cut to the length.
                            return "zip_with("
                                    + list
                                    + ", slice(sequence(1, greatest(cardinality("
                                    + list
                                    + "), 1)), 1, cardinality("
                                    + list
                                    + ")), "
                                    + head
                                    + " -> "
                                    + body
                                    + ")";
                        case "list_filter":
                            if (names.size() != 1) {
                                throw new KorykiaiException(
                                        "list_filter with the position as a second parameter is not supported on Trino");
                            }
                            return "filter(" + list + ", " + head + " -> " + body + ")";
                        default:
                            // reduce(array, initial, (state, element) -> state, state -> state):
                            // the
                            // first element is the initial state and the rest the elements, as in
                            // DuckDB; an empty list has no first element and gives NULL
                            return "reduce(slice("
                                    + list
                                    + ", 2, cardinality("
                                    + list
                                    + ")), element_at("
                                    + list
                                    + ", 1), "
                                    + head
                                    + " -> "
                                    + body
                                    + ", s -> s)";
                    }
                });
    }

    private static final java.util.regex.Pattern PLAIN_NAME =
            java.util.regex.Pattern.compile("[a-z_][a-z0-9_]*");

    /**
     * Trino has the shapes natively. Lists: {@code element_at}, which counts from 1, from the end
     * when negative, and is NULL outside the list where a subscript is an error; ranges through
     * {@code slice(list, start, length)}. Rows (the structs): a field by name or by position, in
     * parentheses. Maps: {@code element_at}, NULL for a key the map does not have. JSON: {@code
     * json_extract} with a path counted from 0, which has no position from the end.
     */
    @Override
    public NestedSupport nestedSupport() {
        return NestedSupport.full("Trino").withoutJsonFromEnd();
    }

    @Override
    public String selection(String base, TypeDescriptor baseType, List<Selector> steps) {
        String sql = base;
        TypeDescriptor type = baseType;
        for (int i = 0; i < steps.size(); i++) {
            Selector step = steps.get(i);
            if (CoreTypeFamily.ARRAY.equals(type.getTypeFamily())
                    && needsSize(step)
                    && !NestedSql.isReference(sql)) {
                // the size of the list is asked for as well as its elements: evaluate it once
                int from = i;
                TypeDescriptor typeHere = type;
                return NestedSql.bindOnce(
                        sql,
                        ONCE,
                        ref -> selection(ref, typeHere, steps.subList(from, steps.size())));
            }
            if (CoreTypeFamily.JSON.equals(type.getTypeFamily())) {
                return jsonPath(sql, steps.subList(i, steps.size()));
            } else if (CoreTypeFamily.ARRAY.equals(type.getTypeFamily())) {
                sql =
                        step.getIndex() != null
                                ? "element_at(" + sql + ", " + step.getIndex() + ")"
                                : slice(sql, step);
            } else if (CoreTypeFamily.STRUCT.equals(type.getTypeFamily())) {
                sql = row(sql, type, step);
            } else if (CoreTypeFamily.MAP.equals(type.getTypeFamily())) {
                sql = "element_at(" + sql + ", " + mapKey(type, step) + ")";
            } else {
                throw new KorykiaiException(
                        "Trino cannot select from " + type.portable() + " with a selector");
            }
            type = ai.koryki.iql.typing.Selectors.step(type, step);
        }
        return sql;
    }

    /** One row holding a value, to name it more than once: see {@link NestedSql#bindOnce}. */
    private static final String ONCE = "FROM (SELECT {0} AS L) {1}";

    /** A range whose bounds are not both plain positions needs the length of the list. */
    private static boolean needsSize(Selector step) {
        return step.getIndex() == null
                && !(step.getFrom() != null
                        && step.getTo() != null
                        && step.getFrom() > 0
                        && step.getTo() > 0);
    }

    private static String slice(String list, Selector step) {
        String length = "cardinality(" + list + ")";
        Integer from = step.getFrom();
        Integer to = step.getTo();
        if (from != null && to != null && from > 0 && to > 0) {
            return "slice(" + list + ", " + from + ", " + Math.max(to - from + 1, 0) + ")";
        }
        // a start before the list is the first element: slice() reads a start of 0 as an error and
        // a negative one as counting from the end, which would answer the wrong elements
        String start =
                from == null
                        ? "1"
                        : from > 0
                                ? String.valueOf(from)
                                : "greatest(" + length + " + " + (from + 1) + ", 1)";
        String stop = to == null ? length : to > 0 ? String.valueOf(to) : length + " + " + (to + 1);
        return "slice("
                + list
                + ", "
                + start
                + ", greatest("
                + stop
                + " - ("
                + start
                + ") + 1, 0))";
    }

    private static String row(String sql, TypeDescriptor type, Selector step) {
        if (step.getIndex() != null) {
            int n = type.children().size();
            return "("
                    + sql
                    + ")["
                    + (step.getIndex() > 0 ? step.getIndex() : n + step.getIndex() + 1)
                    + "]";
        }
        // the name as the type declares it; Trino reads names without regard to case anyway
        return "(" + sql + ")." + NestedSql.identifier(NestedSql.fieldName(type, step), PLAIN_NAME);
    }

    private static String mapKey(TypeDescriptor type, Selector step) {
        if (step.getIndex() != null) {
            return String.valueOf(step.getIndex());
        }
        return NestedSql.literal(INSTANCE, NestedSql.key(step));
    }

    /**
     * {@code json_extract_scalar}: the scalar at the path, text without quotes; NULL for a
     * non-scalar.
     */
    @Override
    public String selectionScalar(String base, TypeDescriptor baseType, List<Selector> steps) {
        String path =
                NestedSql.jsonPath(
                        steps, "$", NestedSql.FromEnd.NONE, NestedSql.Quoting.BRACKET, "Trino");
        return "json_extract_scalar(" + base + ", " + NestedSql.literal(INSTANCE, path) + ")";
    }

    /** A JSON array to a Trino array by a cast; anything that is not an array gives NULL. */
    @Override
    public String jsonList(String json, TypeDescriptor element) {
        return "CAST(" + json + " AS ARRAY(" + declaredScalarType(element) + "))";
    }

    @Override
    public String jsonScalar(String json) {
        return "json_extract_scalar(" + json + ", '$')";
    }

    private static String jsonPath(String sql, List<Selector> steps) {
        String path =
                NestedSql.jsonPath(
                        steps, "$", NestedSql.FromEnd.NONE, NestedSql.Quoting.BRACKET, "Trino");
        return "json_extract(" + sql + ", " + NestedSql.literal(INSTANCE, path) + ")";
    }

    private static final FunctionRenderer FUNCTION_RENDERER = buildFunctionRenderer();

    @Override
    public FunctionRenderer getFunctionRenderer() {
        return FUNCTION_RENDERER;
    }

    private static FunctionRenderer buildFunctionRenderer() {
        FunctionRegistry registry = StandardFunctions.registry();

        // lists with lambdas: transform, filter and reduce are Trino's own, with a lambda of one
        // parameter; the position as a second parameter and the start from the first element are
        // spelled out
        // what a list answers about itself; array_max and array_min give NULL as soon as one
        // element
        // is NULL, so the aggregates run over UNNEST, which ignores them
        registry.register(ListFunctions.supported("list_size").template("cardinality({0})"));
        registry.register(
                ListFunctions.supported("list_contains")
                        .template("(array_position({0}, {1}) > 0)"));
        registry.register(
                ListFunctions.supported("list_sum")
                        .template("(SELECT sum(_e) FROM UNNEST({0}) AS _u(_e))"));
        registry.register(
                ListFunctions.supported("list_min")
                        .template("(SELECT min(_e) FROM UNNEST({0}) AS _u(_e))"));
        registry.register(
                ListFunctions.supported("list_max")
                        .template("(SELECT max(_e) FROM UNNEST({0}) AS _u(_e))"));
        registry.register(
                ListFunctions.supported("list_avg")
                        .template("(SELECT avg(CAST(_e AS DOUBLE)) FROM UNNEST({0}) AS _u(_e))"));
        registry.register(ListFunctions.supported("list_any"));
        registry.register(ListFunctions.supported("list_all"));
        // array_sort puts NULL last; the descending order needs the comparison spelled out for it
        registry.register(
                new FunctionDefinition(ListFunctions.supported("list_sort")) {
                    @Override
                    protected String renderBody(
                            SqlSelectRenderer renderer, Function function, int indent) {
                        String list = renderer.toSql(function.getArguments().get(0), indent);
                        return ListFunctions.descending(function)
                                ? "array_sort("
                                        + list
                                        + ", (a, b) -> CASE WHEN a IS NULL AND b IS NULL THEN 0"
                                        + " WHEN a IS NULL THEN 1 WHEN b IS NULL THEN -1"
                                        + " WHEN a < b THEN 1 WHEN a > b THEN -1 ELSE 0 END)"
                                : "array_sort(" + list + ")";
                    }
                });
        registry.register(
                ListFunctions.supported("list_distinct")
                        .template("array_sort(array_distinct(filter({0}, x -> x IS NOT NULL)))"));
        registry.register(
                ListFunctions.supported("list_concat")
                        .template("coalesce(concat({0}, {1}), {0}, {1})"));
        registry.register(ListFunctions.supported("list").template("ARRAY[{*}]"));
        registry.register(
                ListFunctions.supported("list_agg")
                        .template("array_agg({0}) FILTER (WHERE {0} IS NOT NULL)"));
        for (String name : List.of("list_transform", "list_filter", "list_reduce")) {
            registry.register(
                    new FunctionDefinition(ListFunctions.supported(name)) {
                        @Override
                        protected String renderBody(
                                SqlSelectRenderer renderer, Function function, int indent) {
                            return listWithLambda(name, renderer, function, indent);
                        }
                    });
        }
        // to_text is the rolled-out, tested function: keep its dialect cast-type override.
        registry.overrideAll("to_text", "CAST({0} AS VARCHAR)");
        registry.override("trunc", 1, "truncate({0})");
        // truncate(double, integer) does not exist in Trino, only the DECIMAL form.
        registry.override("trunc", 2, "truncate(CAST({0} AS DECIMAL(38,10)), {1})");
        registry.override("months_between", "date_diff('month', {0}, {1})");
        registry.override("month_end", "last_day_of_month({0})");
        registry.override("week_end", "CAST(date_trunc('week', {0}) + INTERVAL '6' DAY AS DATE)");
        registry.override(
                "quarter_end",
                "last_day_of_month(date_trunc('quarter', {0}) + INTERVAL '2' MONTH)");
        registry.override(
                "year_end", "last_day_of_month(date_trunc('year', {0}) + INTERVAL '11' MONTH)");

        // string functions (Trino/Presto spellings)
        registry.override("char_length", "length({0})");
        registry.override("bit_length", "length(to_utf8({0})) * 8");
        registry.override("octet_length", "length(to_utf8({0}))");
        registry.override("left", "substr({0}, 1, {1})"); // LEFT/RIGHT are reserved (joins)
        // right(s, n) as substr(s, -n) is wrong at the edges: for n = 0, -0 = 0, and substr(s, 0)
        // returns the WHOLE string instead of an empty one -- measured on string_edges, oracle and
        // sqlite returned "alphabet" there while duckdb and postgresql returned "". The length
        // arithmetic covers both edges: n = 0 gives empty, n > length the whole string.
        registry.override(
                "right",
                "substr({0}, CASE WHEN {1} <= 0 THEN length({0}) + 1 ELSE GREATEST(length({0}) - {1} + 1, 1) END)");
        // concat skips NULL arguments -- so documented, and so duckdb, postgresql, oracle, sqlite
        // and mssql behave. Here CONCAT returned NULL for the WHOLE result on a NULL argument,
        // measured on concat_null. concat_ws skips NULL by definition, and with an empty separator
        // it is the same as concat.
        registry.override("concat", "concat_ws('', {*})");
        registry.override("ascii", "codepoint(cast(substr({0}, 1, 1) as varchar(1)))");
        registry.override("to_hex", "upper(to_base({0}, 16))");
        registry.override("repeat", "array_join(repeat({0}, {1}), '')"); // repeat() yields an array
        registry.override("regexp_substr", "regexp_extract({0}, {1})");
        registry.override("string_agg", 2, "array_join(array_agg({0}), {1})");
        registry.override("string_agg", 3, "array_join(array_agg({0} ORDER BY {2}), {1})");

        // avg over a DECIMAL column returns DECIMAL of the SAME scale here, so the mean of
        // three prices came out 11.92 where the other seven gave 11.916667 -- the average of
        // money is not money, and truncating it to the column's scale loses the answer.
        registry.override("avg", "avg(CAST({0} AS DOUBLE))");
        // Trino answered correctly through the canonical template, because its date_diff('day',
        // ...)
        // counts complete days where DuckDB's counts boundaries — same name, different semantics.
        // Now
        // that the canonical form is second-based it needs its own, since Trino has no trunc().
        registry.override(
                "days_between",
                "(date_diff('second', CAST({0} AS TIMESTAMP), CAST({1} AS TIMESTAMP)) / 86400)");
        registry.windowUnsupported("string_agg"); // OVER cannot attach to the outer array_join call

        // numeric / temporal casts & arithmetic
        registry.override("to_float", "CAST({0} AS REAL)"); // Trino has no FLOAT type
        registry.override("day_add", "date_add('day', {1}, {0})");
        registry.override("month_add", "date_add('month', {1}, {0})");
        registry.override("year_add", "date_add('year', {1}, {0})");
        registry.override(
                "years_between", "date_diff('year', {0}, {1})"); // canonical uses PG age()
        registry.override("dayofweek", "day_of_week({0})"); // already ISO: Monday is 1
        registry.override("dayofyear", "day_of_year({0})");
        registry.override("make_date", "date(format('%04d-%02d-%02d', {0}, {1}, {2}))");

        // to_char via date_format (MySQL-style tokens)
        registry.register(
                new FunctionDefinition("to_char", ReturnTypes.TEXT) {
                    @Override
                    protected String renderBody(
                            SqlSelectRenderer renderer, Function function, int indent) {
                        return "date_format("
                                + renderer.toSql(function.getArguments().get(0), indent)
                                + ", "
                                + trinoFormat(
                                        renderer.toSql(function.getArguments().get(1), indent))
                                + ")";
                    }
                }.args(
                        FunctionArg.arg("value", Families.ANY),
                        FunctionArg.arg("format", CoreTypeFamily.TEXT)));

        // parse_date/time/timestamp(value, format) → date_parse, which returns TIMESTAMP and takes
        // the same MySQL-style codes as date_format above.
        registry.register(
                DateTimeFunctions.parseWithMask(
                        "parse_date",
                        ReturnTypes.DATE,
                        TrinoDialect::trinoFormat,
                        (v, f) -> "CAST(date_parse(" + v + ", " + f + ") AS DATE)"));
        registry.register(
                DateTimeFunctions.parseWithMask(
                        "parse_time",
                        ReturnTypes.TIME,
                        TrinoDialect::trinoFormat,
                        (v, f) -> "CAST(date_parse(" + v + ", " + f + ") AS TIME)"));
        registry.register(
                DateTimeFunctions.parseWithMask(
                        "parse_timestamp",
                        ReturnTypes.TIMESTAMP,
                        TrinoDialect::trinoFormat,
                        (v, f) -> "date_parse(" + v + ", " + f + ")"));

        // overlay(string, replacement, start [, length]) → splice with substr (Trino has no
        // overlay)
        registry.register(
                new FunctionDefinition("overlay", ReturnTypes.TEXT) {
                    @Override
                    protected String renderBody(
                            SqlSelectRenderer renderer, Function function, int indent) {
                        var args = function.getArguments();
                        String s = renderer.toSql(args.get(0), indent);
                        String repl = renderer.toSql(args.get(1), indent);
                        String start = renderer.toSql(args.get(2), indent);
                        String len =
                                args.size() > 3
                                        ? renderer.toSql(args.get(3), indent)
                                        : "length(" + repl + ")";
                        return "substr("
                                + s
                                + ", 1, "
                                + start
                                + " - 1) || "
                                + repl
                                + " || substr("
                                + s
                                + ", "
                                + start
                                + " + "
                                + len
                                + ")";
                    }
                }.args(
                        FunctionArg.arg("string", CoreTypeFamily.TEXT),
                                FunctionArg.arg("replacement", CoreTypeFamily.TEXT),
                        FunctionArg.arg("start", CoreTypeFamily.INTEGER),
                                FunctionArg.optionalArg("length", CoreTypeFamily.INTEGER)));

        // no initcap, make_time/make_timestamp, string md5 or format-mask to_number in core Trino
        for (String fn : List.of("initcap", "make_time", "make_timestamp", "md5", "to_number")) {
            registry.unsupported(fn);
        }
        // Trino's parser rejects WITHIN GROUP outright, and there is no native MEDIAN either.
        // approx_percentile exists but is approximate, not the exact answer this catalog promises,
        // so it is not used as a silent stand-in. Measured 2026-09-27.
        for (String fn : List.of("median", "quantile_cont", "quantile_disc")) {
            registry.unsupported(fn);
        }
        return registry;
    }

    /** Canonical KQL format literal → Trino date_format/date_parse (MySQL-style) tokens. */
    private static String trinoFormat(String rendered) {
        return FormatMask.translate(rendered, FormatMask.MYSQL);
    }

    // TIME(seconds-from-midnight) output + arithmetic render raw integers (SqlDialect default);
    // JdbcDatabase#read decodes the integer to a LocalTime.

    @Override
    public String renderEncodedArithmetic(
            SqlSelectRenderer renderer,
            String operator,
            Expression left,
            TypeDescriptor leftType,
            Expression right,
            TypeDescriptor rightType,
            int indent) {
        return renderEncodedArithmetic(
                renderer,
                operator,
                renderer.toSql(left, indent),
                leftType,
                right,
                rightType,
                indent);
    }

    /**
     * Trino date/time arithmetic. Overrides the default because:
     *
     * <ul>
     *   <li>Trino has no QUARTER or WEEK interval qualifier (fold to MONTH*3 / DAY*7), and no
     *       MILLISECOND — each duration component is chained on the date as {@code INTERVAL 'n'
     *       UNIT} (Trino clamps month-end, so chaining matches the model).
     *   <li>{@code DATE + INTEGER} is not valid, so a DATE_FROM_EPOCH_DAY column adds its days via
     *       {@code date_add('day', <col>, DATE '1970-01-01')}.
     * </ul>
     */
    /**
     * Trino's {@code date - date} is legal but yields an INTERVAL DAY TO SECOND, not a day count.
     */
    @Override
    public String dateDiffDays(String leftSql, String rightSql) {
        return "date_diff('day', " + rightSql + ", " + leftSql + ")";
    }

    @Override
    public String renderEncodedArithmetic(
            SqlSelectRenderer renderer,
            String operator,
            String leftSql,
            TypeDescriptor leftType,
            Expression right,
            TypeDescriptor rightType,
            int indent) {
        String diff =
                renderTemporalDiff(renderer, operator, leftSql, leftType, right, rightType, indent);
        if (diff != null) {
            return diff;
        }
        Optional<String> time =
                TimeEncodings.secondsArithmetic(
                        renderer, leftSql, leftType, operator, right, indent);
        if (time.isPresent()) {
            return time.get();
        }
        if (leftType != null
                && CoreTypeEncoding.DATE_FROM_EPOCH_DAY.equals(leftType.getTypeEncoding())) {
            leftSql = "date_add('day', " + leftSql + ", DATE '1970-01-01')";
        }
        leftSql = materializeEpochTimestampLeft(leftSql, leftType);
        Duration dur = right.getDuration();
        if (dur != null) {
            // Trino refuses to add a clock interval (hour/minute/second) to a DATE — promote the
            // date base to TIMESTAMP first when the duration carries any clock component.
            boolean date = leftType != null && CoreTypeFamily.DATE.equals(leftType.getTypeFamily());
            boolean hasClock =
                    dur.getComponents().stream()
                            .anyMatch(
                                    c ->
                                            switch (c.unit()) {
                                                case HOUR, MINUTE, SECOND, MILLISECOND -> true;
                                                default -> false;
                                            });
            if (date && hasClock) {
                leftSql = "CAST(" + leftSql + " AS TIMESTAMP)";
            }
            StringBuilder sb = new StringBuilder(leftSql);
            // Calendar units are one step, not one per unit -- see SqlDialect.calendarFolded.
            for (Duration.Component c : SqlDialect.calendarFolded(dur)) {
                sb.append(' ').append(operator).append(' ').append(intervalComponent(c));
            }
            return sb.toString();
        }
        return leftSql + " " + operator + " " + renderer.toSql(right, indent);
    }

    /**
     * Trino has no {@code EXTRACT(EPOCH ...)}; {@code to_unixtime} reads epoch-seconds (UTC
     * session).
     */
    @Override
    public String timestampToEpochSeconds(String expr, boolean instant) {
        return "CAST(to_unixtime(" + expr + ") AS BIGINT)";
    }

    /**
     * Trino has no {@code make_timestamp}; {@code from_unixtime} is zone-aware, so CAST to a naive
     * TIMESTAMP.
     */
    @Override
    public String epochToTimestamp(String expr, java.time.temporal.ChronoUnit unit) {
        return "CAST(from_unixtime(" + SqlDialect.secondsFromEpoch(expr, unit) + ") AS TIMESTAMP)";
    }

    /**
     * One duration component as a Trino {@code INTERVAL 'n' UNIT} (no QUARTER/WEEK: fold to
     * MONTH/DAY).
     */
    private static String intervalComponent(Duration.Component c) {
        return switch (c.unit()) {
            case YEAR -> "INTERVAL '" + c.value() + "' YEAR";
            case QUARTAL -> "INTERVAL '" + (c.value() * 3) + "' MONTH";
            case MONTH -> "INTERVAL '" + c.value() + "' MONTH";
            case WEEK -> "INTERVAL '" + (c.value() * 7) + "' DAY";
            case DAY -> "INTERVAL '" + c.value() + "' DAY";
            case HOUR -> "INTERVAL '" + c.value() + "' HOUR";
            case MINUTE -> "INTERVAL '" + c.value() + "' MINUTE";
            case SECOND -> "INTERVAL '" + c.value() + "' SECOND";
            case MILLISECOND -> "INTERVAL '" + c.value() + "' MILLISECOND";
        };
    }

    /**
     * Trino casts text to TIME happily but has no {@code EXTRACT(EPOCH …)} for it; the
     * hour/minute/second accessors are the portable route.
     */
    @Override
    public String timeColumnAsSeconds(String columnSql, TypeDescriptor timeType) {
        var enc = timeType != null ? timeType.getTypeEncoding() : null;
        if (CoreTypeEncoding.TIME_FROM_STRING.equals(enc)) {
            String t = "CAST(" + columnSql + " AS TIME)";
            return "hour(" + t + ") * 3600 + minute(" + t + ") * 60 + second(" + t + ")";
        }
        return SqlDialect.super.timeColumnAsSeconds(columnSql, timeType);
    }

    /** Trino division truncates when both operands are integers. */
    @Override
    public String castToDecimal(String sql) {
        return "CAST(" + sql + " AS double)";
    }

    /**
     * Trino splits like Oracle: interval year to month + interval day to second is not a valid
     * operator.
     */
    @Override
    public ai.koryki.iql.SqlDialect.IntervalSupport intervalSupport() {
        return ai.koryki.iql.SqlDialect.IntervalSupport.SPLIT;
    }
}
