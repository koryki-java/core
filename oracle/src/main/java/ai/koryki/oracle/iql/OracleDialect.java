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

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.types.CoreTypeEncoding;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.Families;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.WallClockEncoding;
import ai.koryki.iql.NestedSql;
import ai.koryki.iql.SqlDialect;
import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.functions.FunctionArg;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.FunctionRenderer;
import ai.koryki.iql.functions.ReturnTypes;
import ai.koryki.iql.functions.StandardFunctions;
import ai.koryki.iql.functions.catalog.ListFunctions;
import ai.koryki.iql.query.Duration;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
import ai.koryki.iql.query.Selector;
import ai.koryki.iql.typing.TimeEncodings;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class OracleDialect implements SqlDialect {

    public static final SqlDialect INSTANCE = new OracleDialect();

    /**
     * Oracle's reserved words, on top of the standard's.
     *
     * <p>The ones that matter here are the ordinary-looking nouns. {@code date}, {@code number},
     * {@code level}, {@code comment}, {@code size} and {@code row} are names a schema author
     * reaches for without a second thought, and every one of them stops Oracle's parser while
     * passing unremarked on the other seven engines. Two are already in the catalogs -- {@code
     * date} and {@code long}, in the covid19 model -- and rendered bare until now.
     *
     * <p>Only the words Oracle actually reserves (V$RESERVED_WORDS, {@code reserved = 'Y'}); its
     * much longer keyword list is accepted bare and is not this set's business.
     */
    private static final java.util.Set<String> RESERVED =
            java.util.Set.of(
                    "access",
                    "audit",
                    "cluster",
                    "comment",
                    "compress",
                    "current",
                    "date",
                    "decimal",
                    "exclusive",
                    "file",
                    "float",
                    "identified",
                    "immediate",
                    "increment",
                    "index",
                    "initial",
                    "integer",
                    "level",
                    "lock",
                    "long",
                    "maxextents",
                    "minus",
                    "mlslabel",
                    "mode",
                    "modify",
                    "noaudit",
                    "nocompress",
                    "nowait",
                    "number",
                    "of",
                    "offline",
                    "online",
                    "option",
                    "pctfree",
                    "prior",
                    "public",
                    "raw",
                    "rename",
                    "resource",
                    "row",
                    "rowid",
                    "rownum",
                    "rows",
                    "session",
                    "share",
                    "size",
                    "smallint",
                    "start",
                    "successful",
                    "synonym",
                    "sysdate",
                    "trigger",
                    "uid",
                    "validate",
                    "varchar",
                    "varchar2",
                    "view",
                    "whenever");

    @Override
    public boolean isReserved(String name) {
        return SqlDialect.super.isReserved(name)
                || RESERVED.contains(name.toLowerCase(Locale.ROOT));
    }

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withLocale(Locale.ROOT);

    private OracleDialect() {}

    /**
     * Wall-clock(zone) → model zone: {@code FROM_TZ} reads the naive value as the declared zone,
     * then shift.
     */
    @Override
    public String wallClockToModelZone(
            String columnSql, WallClockEncoding enc, java.time.ZoneId modelZone) {
        String decl = "'" + enc.getZone().getId() + "'";
        String model = "'" + modelZone.getId() + "'";
        String shifted =
                "FROM_TZ(CAST(" + columnSql + " AS TIMESTAMP), " + decl + ") AT TIME ZONE " + model;
        if (CoreTypeFamily.DATE.equals(enc.family())) {
            // Oracle DATE carries a time-of-day, and CAST(<tstz> AS DATE) keeps the converted clock
            // time
            // (NY-midnight surfaces as 04:00 UTC); TRUNC drops it so a wall-clock DATE is a pure
            // calendar day.
            return "TRUNC(CAST(" + shifted + " AS DATE))";
        }
        return "CAST(" + shifted + " AS TIMESTAMP)";
    }

    @Override
    public String zoneShiftTimestamp(String valueSql, String fromZoneSql, String toZoneSql) {
        return "CAST(FROM_TZ(CAST("
                + valueSql
                + " AS TIMESTAMP), "
                + fromZoneSql
                + ") AT TIME ZONE "
                + toZoneSql
                + " AS TIMESTAMP)";
    }

    /**
     * Builds an Oracle NUMTOYMINTERVAL or NUMTODSINTERVAL expression for one Duration.Component. YM
     * family: YEAR, MONTH, QUARTAL → NUMTOYMINTERVAL DS family: DAY, HOUR, MINUTE, SECOND,
     * MILLISECOND, WEEK → NUMTODSINTERVAL
     */
    private static String oracleIntervalComponent(Duration.Component c) {
        return switch (c.unit()) {
            case YEAR -> "NUMTOYMINTERVAL(" + c.value() + ", 'YEAR')";
            case MONTH -> "NUMTOYMINTERVAL(" + c.value() + ", 'MONTH')";
            case QUARTAL -> "NUMTOYMINTERVAL(" + (c.value() * 3) + ", 'MONTH')";
            case WEEK -> "NUMTODSINTERVAL(" + (c.value() * 7) + ", 'DAY')";
            case DAY -> "NUMTODSINTERVAL(" + c.value() + ", 'DAY')";
            case HOUR -> "NUMTODSINTERVAL(" + c.value() + ", 'HOUR')";
            case MINUTE -> "NUMTODSINTERVAL(" + c.value() + ", 'MINUTE')";
            case SECOND -> "NUMTODSINTERVAL(" + c.value() + ", 'SECOND')";
            case MILLISECOND -> "NUMTODSINTERVAL(" + c.value() + " / 1000.0, 'SECOND')";
        };
    }

    private static boolean isYMUnit(Duration.Unit unit) {
        return unit == Duration.Unit.YEAR
                || unit == Duration.Unit.MONTH
                || unit == Duration.Unit.QUARTAL;
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
        String bare =
                (unit.startsWith("'") && unit.endsWith("'"))
                        ? unit.substring(1, unit.length() - 1).toUpperCase(Locale.ROOT)
                        : null;
        if (bare != null) {
            return switch (bare) {
                case "YEAR", "YEARS" -> "NUMTOYMINTERVAL(" + value + ", 'YEAR')";
                case "MONTH", "MONTHS" -> "NUMTOYMINTERVAL(" + value + ", 'MONTH')";
                case "WEEK", "WEEKS" -> "NUMTODSINTERVAL(" + value + " * 7, 'DAY')";
                case "DAY", "DAYS" -> "NUMTODSINTERVAL(" + value + ", 'DAY')";
                case "HOUR", "HOURS" -> "NUMTODSINTERVAL(" + value + ", 'HOUR')";
                case "MINUTE", "MINUTES" -> "NUMTODSINTERVAL(" + value + ", 'MINUTE')";
                case "SECOND", "SECONDS" -> "NUMTODSINTERVAL(" + value + ", 'SECOND')";
                case "MILLISECOND", "MILLISECONDS" ->
                        "NUMTODSINTERVAL(" + value + " / 1000.0, 'SECOND')";
                default -> "NUMTODSINTERVAL(" + value + ", " + unit + ")";
            };
        }
        return "NUMTODSINTERVAL(" + value + ", " + unit + ")";
    }

    /**
     * Expands the right-hand side of a temporal arithmetic expression.
     *
     * <p>For a seconds-from-midnight TIME the operand becomes integer seconds and the result is
     * wrapped mod 86400 (semantics in {@link TimeEncodings}). For DATE/TIMESTAMP it expands into
     * chained Oracle interval additions, handling: - Duration literals (e.g. 2d4h, 1y2mo15d) -
     * to_interval(value, unit) — 2-arg - to_interval(y, mo, d, h, mi, s) — 6-arg
     */
    private static String expandOracleArithmetic(
            SqlSelectRenderer renderer,
            String operator,
            String leftSql,
            TypeDescriptor leftType,
            Expression right,
            int indent) {
        java.util.Optional<String> time =
                TimeEncodings.secondsArithmetic(
                        renderer, leftSql, leftType, operator, right, indent);
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
            // Calendar parts go through ADD_MONTHS -- Oracle's DATE + NUMTOYMINTERVAL instead
            // raises ORA-01839 on an overflow such as Dec-31 + 2 months. Clock parts stay
            // NUMTODSINTERVAL.
            //
            // ADD_MONTHS does not only clamp, it *promotes*: when the source day is its month's
            // last, Oracle returns the last day of the target month even where the source day
            // exists there (2022-09-30 + 1mo gave October 31 instead of the 30th). TEMPORAL.md
            // clamps only "when the target day does not exist", and the other seven dialects do.
            // No fixture touched it -- the one with a month-end is date_date_arithmetic with
            // 2025-12-31, where clamping and promotion happen to agree.
            //
            // The correction uses that ADD_MONTHS yields day = min(source day, month end) *except*
            // under promotion, where it comes out too large; a day that is too small can never
            // arise. Subtracting the excess as whole days therefore suffices -- no CASE needed,
            // and whole days keep the time of day.
            //
            // Year, quarter and month stay ONE combined ADD_MONTHS. TEMPORAL.md asks for a step
            // per component ("one whole component at a time, largest unit first"), which for
            // 2024-02-29 + 1y1mo would give 2025-03-28 rather than 2025-03-29 -- but no dialect
            // implements that, DuckDB included: the renderer emits a single combined
            // `INTERVAL '1 year 1 month'`. Applying it here alone would make Oracle the only
            // deviating dialect, so the gap between documentation and implementation is left
            // whole rather than half-closed on one engine.
            boolean minus = "-".equals(operator);
            long ymMonths = 0;
            for (Duration.Component c : dur.getComponents()) {
                switch (c.unit()) {
                    case YEAR -> ymMonths += c.value() * 12;
                    case MONTH -> ymMonths += c.value();
                    case QUARTAL -> ymMonths += c.value() * 3;
                    default -> {}
                }
            }
            String result = leftSql;
            if (ymMonths != 0) {
                String target =
                        "ADD_MONTHS(" + leftSql + ", " + (minus ? -ymMonths : ymMonths) + ")";
                result =
                        "("
                                + target
                                + " - GREATEST(EXTRACT(DAY FROM "
                                + target
                                + ")"
                                + " - EXTRACT(DAY FROM "
                                + leftSql
                                + "), 0))";
            }
            for (Duration.Component c : dur.getComponents()) {
                switch (c.unit()) {
                    case YEAR, MONTH, QUARTAL -> {} // already applied via ADD_MONTHS
                    default -> result = result + " " + operator + " " + oracleIntervalComponent(c);
                }
            }
            return result;
        }
        if (right.getFunction() != null && "to_interval".equals(right.getFunction().getFunc())) {
            Function f = right.getFunction();
            if (f.getArguments().size() == 2) {
                String value = renderer.toSql(f.getArguments().get(0), indent);
                String unit = renderer.toSql(f.getArguments().get(1), indent);
                return leftSql + " " + operator + " " + oracleToIntervalUnit(value, unit);
            }
            if (f.getArguments().size() == 6) {
                var args = f.getArguments();
                String[] vals = new String[6];
                for (int i = 0; i < 6; i++) vals[i] = renderer.toSql(args.get(i), indent);
                StringBuilder sb = new StringBuilder(leftSql);
                // YM components first, then DS components — keeps Oracle type families separate
                if (!"0".equals(vals[0].trim()))
                    sb.append(" ")
                            .append(operator)
                            .append(" NUMTOYMINTERVAL(")
                            .append(vals[0])
                            .append(", 'YEAR')");
                if (!"0".equals(vals[1].trim()))
                    sb.append(" ")
                            .append(operator)
                            .append(" NUMTOYMINTERVAL(")
                            .append(vals[1])
                            .append(", 'MONTH')");
                if (!"0".equals(vals[2].trim()))
                    sb.append(" ")
                            .append(operator)
                            .append(" NUMTODSINTERVAL(")
                            .append(vals[2])
                            .append(", 'DAY')");
                if (!"0".equals(vals[3].trim()))
                    sb.append(" ")
                            .append(operator)
                            .append(" NUMTODSINTERVAL(")
                            .append(vals[3])
                            .append(", 'HOUR')");
                if (!"0".equals(vals[4].trim()))
                    sb.append(" ")
                            .append(operator)
                            .append(" NUMTODSINTERVAL(")
                            .append(vals[4])
                            .append(", 'MINUTE')");
                if (!"0".equals(vals[5].trim()))
                    sb.append(" ")
                            .append(operator)
                            .append(" NUMTODSINTERVAL(")
                            .append(vals[5])
                            .append(", 'SECOND')");
                return sb.toString();
            }
        }
        return leftSql + " " + operator + " " + renderer.toSql(right, indent);
    }

    // ---- nested values: selectors and lambdas -------------------------------------------------

    /**
     * Oracle's collections (VARRAY, nested table) have no subscript in SQL, only in PL/SQL, so an
     * element is a scalar subquery over {@code TABLE(list)} numbered by {@code ROWNUM}, which is
     * the order of a VARRAY. The size is {@code COUNT(*)} over the same, since {@code CARDINALITY}
     * is for nested tables only. The attributes of an object type are reached by dot notation,
     * which needs an alias in front, so the steps after an element are folded into its subquery. A
     * range is a collection again, built with {@code CAST(MULTISET(...))} to one of the generic
     * {@code SYS.ODCI*LIST} types, as Oracle cannot make a collection without a named type: only
     * lists of text, numbers and dates, which is what those types hold. A map does not exist here
     * (it is JSON); a JSON value takes one {@code JSON_QUERY}, whose path has {@code last} for a
     * position from the end.
     */
    @Override
    public NestedSupport nestedSupport() {
        return NestedSupport.full("Oracle").without(CoreTypeFamily.MAP).withoutLambdaOverJsonList();
    }

    @Override
    public String selection(String base, TypeDescriptor baseType, List<Selector> steps) {
        String sql = base;
        TypeDescriptor type = baseType;
        List<String[]> frames = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            Selector step = steps.get(i);
            var family = type.getTypeFamily();
            if (CoreTypeFamily.JSON.equals(family)) {
                sql = jsonPath(sql, steps.subList(i, steps.size()));
                break;
            } else if (CoreTypeFamily.ARRAY.equals(family)) {
                if (step.getIndex() != null) {
                    String alias = "E" + (frames.size() + 1);
                    frames.add(
                            new String[] {
                                "(" + elements(sql, type.element()) + ") " + alias,
                                alias + ".RN = " + position(sql, step.getIndex())
                            });
                    sql = alias + ".V";
                } else {
                    sql = slice(sql, type, step);
                }
            } else if (CoreTypeFamily.STRUCT.equals(family)) {
                sql = sql + "." + attribute(type, step);
            } else {
                throw new KorykiaiException(
                        "Oracle cannot select from " + type.portable() + " with a selector");
            }
            type = ai.koryki.iql.typing.Selectors.step(type, step);
        }
        for (int i = frames.size() - 1; i >= 0; i--) {
            sql =
                    "(SELECT "
                            + sql
                            + " FROM "
                            + frames.get(i)[0]
                            + " WHERE "
                            + frames.get(i)[1]
                            + ")";
        }
        return sql;
    }

    // No bind-once here, unlike the other dialects: TABLE() over a collection column of a
    // derived table that refers to the outer query answers no rows (measured, Oracle 26ai), so a
    // list expression is written wherever it is needed.

    private static String size(String list) {
        return "(SELECT COUNT(*) FROM TABLE(" + list + "))";
    }

    /** The elements of a collection with their 1-based position: object types by VALUE. */
    private static String elements(String list, TypeDescriptor element) {
        boolean object = CoreTypeFamily.STRUCT.equals(element.getTypeFamily());
        return "SELECT "
                + (object ? "VALUE(Z)" : "COLUMN_VALUE")
                + " V, ROWNUM RN FROM TABLE("
                + list
                + ") Z";
    }

    private static String position(String list, int position) {
        return position > 0 ? String.valueOf(position) : size(list) + " - " + (-position) + " + 1";
    }

    private static String slice(String list, TypeDescriptor type, Selector step) {
        List<String> where = new ArrayList<>();
        if (step.getFrom() != null) {
            where.add("E.RN >= " + position(list, step.getFrom()));
        }
        if (step.getTo() != null) {
            where.add("E.RN <= " + position(list, step.getTo()));
        }
        return nullIfNull(
                list,
                "CAST(MULTISET(SELECT E.V FROM ("
                        + elements(list, type.element())
                        + ") E WHERE "
                        + String.join(" AND ", where)
                        + " ORDER BY E.RN) AS "
                        + collectionType(type.element())
                        + ")");
    }

    /** A collection made from a NULL list is NULL, not the empty one TABLE() would make of it. */
    private static String nullIfNull(String list, String built) {
        return "CASE WHEN " + list + " IS NULL THEN NULL ELSE " + built + " END";
    }

    /**
     * Oracle cannot build a collection without a named type. The generic ones of SYS hold text,
     * numbers and dates, so a list of anything else, objects above all, cannot be made here.
     */
    private static String collectionType(TypeDescriptor element) {
        var family = element.getTypeFamily();
        if (CoreTypeFamily.TEXT.equals(family)) {
            return "SYS.ODCIVARCHAR2LIST";
        } else if (CoreTypeFamily.INTEGER.equals(family)
                || CoreTypeFamily.DECIMAL.equals(family)
                || CoreTypeFamily.FLOAT.equals(family)) {
            return "SYS.ODCINUMBERLIST";
        } else if (CoreTypeFamily.DATE.equals(family)) {
            return "SYS.ODCIDATELIST";
        }
        throw new KorykiaiException(
                "Oracle cannot build a list of "
                        + element.portable()
                        + ": only lists of text, numbers and dates, which the generic collection types hold");
    }

    private static final Pattern PLAIN_ATTRIBUTE = Pattern.compile("[a-z][a-z0-9_]*");

    private static String attribute(TypeDescriptor type, Selector step) {
        return NestedSql.identifier(NestedSql.fieldName(type, step), PLAIN_ATTRIBUTE);
    }

    /**
     * One JSON_QUERY for the whole path. Oracle's default (lax) paths treat an object as a list of
     * one and apply a name to every element of a list, so {@code $[0]} on an object answers the
     * object and {@code $.a} on a list of objects answers their {@code a}s; DuckDB and the others
     * answer NULL. Each step is therefore guarded by the {@code type()} of the value it is applied
     * to: a position needs an array, a name an object. ({@code strict} is not accepted by this
     * JSON_QUERY form.)
     */
    /**
     * {@code JSON_VALUE}: the scalar at the path, text without quotes; NULL for an object or list.
     */
    @Override
    public String selectionScalar(String base, TypeDescriptor baseType, List<Selector> steps) {
        return jsonPath(base, steps, true);
    }

    /**
     * A JSON array to a collection of the generic result types: the elements through JSON_TABLE, in
     * their order. Oracle has no boolean column type, so a list of booleans is refused.
     */
    @Override
    public String jsonList(String json, TypeDescriptor element) {
        boolean text = CoreTypeFamily.TEXT.equals(element.getTypeFamily());
        if (CoreTypeFamily.BOOLEAN.equals(element.getTypeFamily())) {
            throw new KorykiaiException("Oracle cannot read a JSON array of booleans as a list");
        }
        return "CASE WHEN JSON_VALUE("
                + json
                + ", '$.type()') = 'array' THEN CAST(MULTISET(SELECT jt.v FROM JSON_TABLE("
                + json
                + ", '$[*]' COLUMNS (ord FOR ORDINALITY, v "
                + (text ? "VARCHAR2(4000)" : "NUMBER")
                + " PATH '$')) jt ORDER BY jt.ord) AS "
                + (text ? "SYS.ODCIVARCHAR2LIST" : "SYS.ODCINUMBERLIST")
                + ") END";
    }

    @Override
    public String jsonScalar(String json) {
        return "JSON_VALUE(" + json + ", '$')";
    }

    private static String jsonPath(String sql, List<Selector> steps) {
        return jsonPath(sql, steps, false);
    }

    private static String jsonPath(String sql, List<Selector> steps, boolean scalar) {
        List<String> paths =
                NestedSql.jsonPaths(
                        steps, "$", NestedSql.FromEnd.LAST, NestedSql.Quoting.DOT, "Oracle");
        List<String> guards = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            guards.add(
                    "JSON_VALUE("
                            + sql
                            + ", "
                            + NestedSql.literal(INSTANCE, paths.get(i) + ".type()")
                            + ") = '"
                            + (steps.get(i).getIndex() != null ? "array" : "object")
                            + "'");
        }
        return "CASE WHEN "
                + String.join(" AND ", guards)
                + " THEN "
                + (scalar ? "JSON_VALUE(" : "JSON_QUERY(")
                + sql
                + ", "
                + NestedSql.literal(INSTANCE, paths.get(steps.size()))
                + (scalar ? "" : " RETURNING JSON")
                + ") END";
    }

    /**
     * Oracle has no lambda, so TRANSFORM and FILTER are a {@code CAST(MULTISET(SELECT ... FROM
     * TABLE(list)))} to a generic collection type, the element as column {@code X} and its position
     * as column {@code I} of a derived table named for the depth of the lambda. The parameters of
     * the lambda are bound to those columns, so the body is rendered with them. REDUCE is declared
     * unsupported where the functions are registered.
     */
    private static String listWithLambda(
            String name, SqlSelectRenderer renderer, Function function, int indent) {
        Expression listExpression = function.getArguments().get(0);
        noAggregateList(listExpression);
        TypeDescriptor element = renderer.resolveType(listExpression).element();
        Expression lambda = function.getArguments().get(1);
        return lambdaOver(
                name, renderer, lambda, element, renderer.toSql(listExpression, indent), indent);
    }

    /**
     * {@code list_agg}: COLLECT leaves NULLs out, answers an empty collection for no rows where the
     * contract says NULL, and takes an ORDER BY, which {@code list_sort(list_agg(x))} uses: a
     * COLLECT cannot be read through TABLE() in a subquery (ORA-00934), so it is sorted inside.
     */
    private static String collected(
            SqlSelectRenderer renderer, Expression value, int indent, Boolean descending) {
        String sql = renderer.toSql(value, indent);
        return "CASE WHEN COUNT("
                + sql
                + ") = 0 THEN NULL ELSE CAST(COLLECT("
                + sql
                + (descending == null ? "" : " ORDER BY " + sql + (descending ? " DESC" : ""))
                + ") AS "
                + collectionType(renderer.resolveType(value))
                + ") END";
    }

    /** What Oracle cannot do with a list that is the result of an aggregate. */
    private static void noAggregateList(Expression list) {
        if (ai.koryki.iql.validate.FunctionValidator.isAggregateOfColumnOrIdentity(list)) {
            throw new KorykiaiException(
                    "on Oracle the list of list_agg can be shown or sorted with list_sort and"
                            + " nothing else: TABLE() cannot read an aggregate (ORA-00934)");
        }
    }

    /**
     * {@code list_any} and {@code list_all} count the elements the test lets through, or not, so a
     * list of objects works as well as one of numbers: the lambda is bound to the element as it is
     * for a transform, and nothing has to be built.
     */
    private static String quantifier(
            boolean all,
            SqlSelectRenderer renderer,
            Expression lambda,
            TypeDescriptor element,
            String list,
            int indent) {
        refuseJsonList(all ? "list_all" : "list_any", list);
        String alias = "LA" + renderer.lambdaDepth();
        return renderer.lambda(
                lambda,
                indent,
                (parameter, position, depth) -> alias + ".X",
                (names, test) -> {
                    boolean object = CoreTypeFamily.STRUCT.equals(element.getTypeFamily());
                    String source =
                            "(SELECT "
                                    + (object ? "VALUE(Z)" : "COLUMN_VALUE")
                                    + " X FROM TABLE("
                                    + list
                                    + ") Z) "
                                    + alias;
                    String count =
                            "(SELECT COUNT(*) FROM "
                                    + source
                                    + " WHERE "
                                    + (all ? "(" + test + ") IS NOT TRUE" : test)
                                    + ")";
                    return nullIfNull(list, "(" + count + (all ? " = 0)" : " > 0)"));
                });
    }

    /**
     * The lambda functions run over a derived table that reads the list again; a list built from a
     * JSON array in the same place is a correlated subquery in a derived table, which Oracle
     * answers with an empty result and no error. Refused rather than rendered.
     */
    private static void refuseJsonList(String function, String list) {
        if (list.contains("JSON_TABLE(")) {
            throw new KorykiaiException(
                    function
                            + " cannot be applied to a JSON array on Oracle: the list is built"
                            + " inside a derived table, where Oracle returns nothing."
                            + " The list functions without a lambda work.");
        }
    }

    private static String lambdaOver(
            String name,
            SqlSelectRenderer renderer,
            Expression lambda,
            TypeDescriptor element,
            String list,
            int indent) {
        refuseJsonList(name, list);
        String alias = "LA" + renderer.lambdaDepth();
        return renderer.lambda(
                lambda,
                indent,
                (parameter, position, depth) -> alias + (position == 0 ? ".X" : ".I"),
                (names, b) -> {
                    boolean object = CoreTypeFamily.STRUCT.equals(element.getTypeFamily());
                    String source =
                            "(SELECT "
                                    + (object ? "VALUE(Z)" : "COLUMN_VALUE")
                                    + " X, ROWNUM I FROM TABLE("
                                    + list
                                    + ") Z) "
                                    + alias;
                    if ("list_transform".equals(name)) {
                        return nullIfNull(
                                list,
                                "CAST(MULTISET(SELECT "
                                        + b
                                        + " FROM "
                                        + source
                                        + " ORDER BY "
                                        + alias
                                        + ".I) AS "
                                        + collectionType(renderer.lambdaBodyType(lambda))
                                        + ")");
                    }
                    return nullIfNull(
                            list,
                            "CAST(MULTISET(SELECT "
                                    + alias
                                    + ".X FROM "
                                    + source
                                    + " WHERE "
                                    + b
                                    + " ORDER BY "
                                    + alias
                                    + ".I) AS "
                                    + collectionType(element)
                                    + ")");
                });
    }

    private static final FunctionRenderer FUNCTION_RENDERER = buildFunctionRenderer();

    @Override
    public FunctionRenderer getFunctionRenderer() {
        return FUNCTION_RENDERER;
    }

    private static FunctionRenderer buildFunctionRenderer() {
        FunctionRegistry registry = StandardFunctions.registry();

        // a fold has no form in a scalar position of SQL: declared, so that it is refused with what
        // to do instead where the query is checked, and listed as unsupported in the documentation
        registry.register(
                ListFunctions.supported("list_reduce")
                        .unsupported()
                        .unsupportedHint(
                                "Oracle has no fold over a collection; sum, min, max or listagg over"
                                        + " TABLE(list) cover the usual cases"));
        // what a list answers about itself, through TABLE(); a NULL list has no rows, so the size
        // and the membership say NULL for it explicitly, and the aggregates are NULL on their own
        registry.register(
                ListFunctions.supported("list_size")
                        .template(
                                "CASE WHEN {0} IS NULL THEN NULL ELSE (SELECT COUNT(*) FROM TABLE({0})) END"));
        registry.register(
                ListFunctions.supported("list_contains")
                        .template(
                                "CASE WHEN {0} IS NULL THEN NULL ELSE ((SELECT COUNT(*) FROM TABLE({0}) WHERE COLUMN_VALUE = {1}) > 0) END"));
        registry.register(
                ListFunctions.supported("list_sum")
                        .template("(SELECT SUM(COLUMN_VALUE) FROM TABLE({0}))"));
        registry.register(
                ListFunctions.supported("list_min")
                        .template("(SELECT MIN(COLUMN_VALUE) FROM TABLE({0}))"));
        registry.register(
                ListFunctions.supported("list_max")
                        .template("(SELECT MAX(COLUMN_VALUE) FROM TABLE({0}))"));
        registry.register(
                ListFunctions.supported("list_avg")
                        .template(
                                "(SELECT CAST(AVG(COLUMN_VALUE) AS BINARY_DOUBLE) FROM TABLE({0}))"));
        // any, all, sort, distinct and concat through TABLE(): a collection of the generic types
        // for what is a list again, a count for what is a yes or no
        for (String name : List.of("list_any", "list_all")) {
            registry.register(
                    new FunctionDefinition(ListFunctions.supported(name)) {
                        @Override
                        protected String renderBody(
                                SqlSelectRenderer renderer, Function function, int indent) {
                            Expression listExpression = function.getArguments().get(0);
                            noAggregateList(listExpression);
                            return quantifier(
                                    name.equals("list_all"),
                                    renderer,
                                    function.getArguments().get(1),
                                    renderer.resolveType(listExpression).element(),
                                    renderer.toSql(listExpression, indent),
                                    indent);
                        }
                    });
        }
        registry.register(
                new FunctionDefinition(ListFunctions.supported("list_sort")) {
                    @Override
                    protected String renderBody(
                            SqlSelectRenderer renderer, Function function, int indent) {
                        Expression listExpression = function.getArguments().get(0);
                        if (listExpression.getFunction() != null
                                && "list_agg".equals(listExpression.getFunction().getFunc())) {
                            return collected(
                                    renderer,
                                    listExpression.getFunction().getArguments().get(0),
                                    indent,
                                    ListFunctions.descending(function));
                        }
                        noAggregateList(listExpression);
                        String list = renderer.toSql(listExpression, indent);
                        return nullIfNull(
                                list,
                                "CAST(MULTISET(SELECT COLUMN_VALUE FROM TABLE("
                                        + list
                                        + ") ORDER BY COLUMN_VALUE"
                                        + (ListFunctions.descending(function) ? " DESC" : "")
                                        + " NULLS LAST) AS "
                                        + collectionType(
                                                renderer.resolveType(listExpression).element())
                                        + ")");
                    }
                });
        registry.register(
                new FunctionDefinition(ListFunctions.supported("list_distinct")) {
                    @Override
                    protected String renderBody(
                            SqlSelectRenderer renderer, Function function, int indent) {
                        Expression listExpression = function.getArguments().get(0);
                        noAggregateList(listExpression);
                        String list = renderer.toSql(listExpression, indent);
                        return nullIfNull(
                                list,
                                "CAST(MULTISET(SELECT DISTINCT COLUMN_VALUE FROM TABLE("
                                        + list
                                        + ") WHERE COLUMN_VALUE IS NOT NULL ORDER BY COLUMN_VALUE) AS "
                                        + collectionType(
                                                renderer.resolveType(listExpression).element())
                                        + ")");
                    }
                });
        registry.register(
                new FunctionDefinition(ListFunctions.supported("list_concat")) {
                    @Override
                    protected String renderBody(
                            SqlSelectRenderer renderer, Function function, int indent) {
                        Expression first = function.getArguments().get(0);
                        noAggregateList(first);
                        noAggregateList(function.getArguments().get(1));
                        String a = renderer.toSql(first, indent);
                        String b = renderer.toSql(function.getArguments().get(1), indent);
                        return "CASE WHEN "
                                + a
                                + " IS NULL AND "
                                + b
                                + " IS NULL THEN NULL ELSE CAST(MULTISET(SELECT V FROM (SELECT 1 K,"
                                + " ROWNUM R, COLUMN_VALUE V FROM TABLE("
                                + a
                                + ") UNION ALL SELECT 2, ROWNUM, COLUMN_VALUE FROM TABLE("
                                + b
                                + ")) ORDER BY K, R) AS "
                                + collectionType(renderer.resolveType(first).element())
                                + ") END";
                    }
                });
        // the generic collection types again: a constructor of the one that holds what the
        // values are, COLLECT for the rows of a group
        registry.register(
                new FunctionDefinition(ListFunctions.supported("list")) {
                    @Override
                    protected String renderBody(
                            SqlSelectRenderer renderer, Function function, int indent) {
                        TypeDescriptor first = renderer.resolveType(function.getArguments().get(0));
                        return collectionType(first)
                                + "("
                                + function.getArguments().stream()
                                        .map(a -> renderer.toSql(a, indent))
                                        .collect(Collectors.joining(", "))
                                + ")";
                    }
                });
        registry.register(
                new FunctionDefinition(ListFunctions.supported("list_agg")) {
                    @Override
                    protected String renderBody(
                            SqlSelectRenderer renderer, Function function, int indent) {
                        return collected(renderer, function.getArguments().get(0), indent, null);
                    }
                });
        // a template of the functions that read a list, with the check that what they read is not
        // an aggregate, which TABLE() in a subquery cannot hold
        for (String name :
                List.of(
                        "list_size",
                        "list_contains",
                        "list_sum",
                        "list_min",
                        "list_max",
                        "list_avg")) {
            FunctionDefinition template = registry.lookup(name);
            registry.register(
                    new FunctionDefinition(template) {
                        @Override
                        protected String renderBody(
                                SqlSelectRenderer renderer, Function function, int indent) {
                            noAggregateList(function.getArguments().get(0));
                            return super.renderBody(renderer, function, indent);
                        }
                    });
        }
        for (String name : List.of("list_transform", "list_filter")) {
            registry.register(
                    new FunctionDefinition(ListFunctions.supported(name)) {
                        @Override
                        protected String renderBody(
                                SqlSelectRenderer renderer, Function function, int indent) {
                            return listWithLambda(name, renderer, function, indent);
                        }
                    });
        }

        // to_date(value) | to_date(value, format) | to_date(ts, tz) | to_date(year, month, day)
        // CAST(text AS DATE) reads the string with NLS_DATE_FORMAT, which defaults to DD-MON-RR,
        // so the ISO text every other dialect accepts failed here with ORA-01861. A temporal source
        // still casts; text is parsed with the ISO mask explicitly.
        registry.register(
                new FunctionDefinition("to_date", ReturnTypes.DATE) {
                    @Override
                    protected String renderBody(
                            SqlSelectRenderer renderer, Function function, int indent) {
                        Expression arg = function.getArguments().get(0);
                        String sql = renderer.toSql(arg, indent);
                        TypeDescriptor t = renderer.resolveType(arg);
                        if (t != null && CoreTypeFamily.TEXT.equals(t.getTypeFamily())) {
                            return "TO_DATE(" + sql + ", 'YYYY-MM-DD')";
                        }
                        return "CAST(" + sql + " AS DATE)";
                    }
                }.args(FunctionArg.arg("value", Families.TEMPORAL_OR_TEXT)));
        registry.register(
                new FunctionDefinition("to_date", ReturnTypes.DATE) {
                    @Override
                    protected String renderBody(
                            SqlSelectRenderer renderer, Function function, int indent) {
                        String a0 = renderer.toSql(function.getArguments().get(0), indent);
                        String a1 = renderer.toSql(function.getArguments().get(1), indent);
                        TypeDescriptor t0 = renderer.resolveType(function.getArguments().get(0));
                        if (t0 != null && CoreTypeFamily.TIMESTAMP.equals(t0.getTypeFamily()))
                            // tz conversion: treat source as UTC, return local DATE
                            return "CAST(FROM_TZ(CAST("
                                    + a0
                                    + " AS TIMESTAMP), 'UTC') AT TIME ZONE "
                                    + a1
                                    + " AS DATE)";
                        // parse string with Oracle-native format mask
                        return "TO_DATE(" + a0 + ", " + a1 + ")";
                    }
                }.args(
                        FunctionArg.arg("value", Families.TEMPORAL_OR_TEXT),
                        FunctionArg.arg("format", CoreTypeFamily.TEXT)));
        registry.register(
                new FunctionDefinition("to_date", ReturnTypes.DATE)
                        .args(
                                FunctionArg.arg("year", CoreTypeFamily.INTEGER),
                                FunctionArg.arg("month", CoreTypeFamily.INTEGER),
                                FunctionArg.arg("day", CoreTypeFamily.INTEGER))
                        .template("TO_DATE({0} || '-' || {1} || '-' || {2}, 'YYYY-MM-DD')"));

        // to_time(value) | to_time(value, format) | to_time(ts, tz) | to_time(hour, minute, second)
        // Oracle has no TIME type, so all overloads render TO_CHAR(..., 'HH24:MI:SS') — a string.
        //
        // They used to declare ReturnTypes.TEXT to say so, which made to_time answer as a TEXT
        // column here and as a TIME column on the other seven. SQLite is in the same position —
        // its time() returns text too — and declares TIME, so the two dialects lacking the type
        // disagreed with each other. The family is koryki's promise about the value, not a claim
        // about how the engine stores it: TIME as text is a shape the model already names
        // (CoreTypeEncoding.TIME_FROM_STRING). The rendering is unchanged; only the declaration is.
        registry.register(
                new FunctionDefinition("to_time", ReturnTypes.TIME) {
                    @Override
                    protected String renderBody(
                            SqlSelectRenderer renderer, Function function, int indent) {
                        Expression arg = function.getArguments().getFirst();
                        TypeDescriptor src = renderer.resolveType(arg);
                        String sql = renderer.toSql(arg, indent);
                        if (src != null
                                && CoreTypeEncoding.TIME_SECONDS_FROM_MIDNIGHT.equals(
                                        src.getTypeEncoding()))
                            return "TO_CHAR(DATE '1970-01-01' + " + sql + " / 86400, 'HH24:MI:SS')";
                        return "TO_CHAR(" + sql + ", 'HH24:MI:SS')";
                    }
                }.args(FunctionArg.arg("value", Families.TEMPORAL_OR_TEXT)));
        registry.register(
                new FunctionDefinition("to_time", ReturnTypes.TIME) {
                    @Override
                    protected String renderBody(
                            SqlSelectRenderer renderer, Function function, int indent) {
                        String a0 = renderer.toSql(function.getArguments().get(0), indent);
                        String a1 = renderer.toSql(function.getArguments().get(1), indent);
                        TypeDescriptor t0 = renderer.resolveType(function.getArguments().get(0));
                        if (t0 != null && CoreTypeFamily.TIMESTAMP.equals(t0.getTypeFamily()))
                            return "TO_CHAR(FROM_TZ(CAST("
                                    + a0
                                    + " AS TIMESTAMP), 'UTC') AT TIME ZONE "
                                    + a1
                                    + ", 'HH24:MI:SS')";
                        return "TO_CHAR(TO_TIMESTAMP(" + a0 + ", " + a1 + "), 'HH24:MI:SS')";
                    }
                }.args(
                        FunctionArg.arg("value", Families.TEMPORAL_OR_TEXT),
                        FunctionArg.arg("format", CoreTypeFamily.TEXT)));
        registry.register(
                new FunctionDefinition("to_time", ReturnTypes.TIME)
                        .args(
                                FunctionArg.arg("hour", CoreTypeFamily.INTEGER),
                                FunctionArg.arg("minute", CoreTypeFamily.INTEGER),
                                FunctionArg.arg("second", Families.NUMERIC))
                        .template(
                                "LPAD({0}, 2, '0') || ':' || LPAD({1}, 2, '0') || ':' || LPAD({2}, 2, '0')"));

        // to_timestamp(value) | to_timestamp(value, format) | to_timestamp(value, format, tz)
        // | to_timestamp(year, month, day, hour, minute, second)
        // | to_timestamp(year, month, day, hour, minute, second, tz)
        registry.register(
                new FunctionDefinition("to_timestamp", ReturnTypes.TIMESTAMP)
                        .args(FunctionArg.arg("value", Families.TEMPORAL_OR_TEXT))
                        .template("CAST({0} AS TIMESTAMP)"));
        registry.register(
                new FunctionDefinition("to_timestamp", ReturnTypes.TIMESTAMP)
                        .args(
                                FunctionArg.arg("value", Families.TEMPORAL_OR_TEXT),
                                FunctionArg.arg("format", CoreTypeFamily.TEXT))
                        .template("TO_TIMESTAMP({0}, {1})"));
        // to_timestamp(str, fmt, tz): parse string as UTC, then express in tz
        registry.register(
                new FunctionDefinition("to_timestamp", ReturnTypes.TIMESTAMP)
                        .args(
                                FunctionArg.arg("value", Families.TEMPORAL_OR_TEXT),
                                FunctionArg.arg("format", CoreTypeFamily.TEXT),
                                FunctionArg.arg("tz", CoreTypeFamily.TEXT))
                        .template("FROM_TZ(TO_TIMESTAMP({0}, {1}), 'UTC') AT TIME ZONE {2}"));
        registry.register(
                new FunctionDefinition("to_timestamp", ReturnTypes.TIMESTAMP)
                        .args(
                                FunctionArg.arg("year", CoreTypeFamily.INTEGER),
                                FunctionArg.arg("month", CoreTypeFamily.INTEGER),
                                FunctionArg.arg("day", CoreTypeFamily.INTEGER),
                                FunctionArg.arg("hour", CoreTypeFamily.INTEGER),
                                FunctionArg.arg("minute", CoreTypeFamily.INTEGER),
                                FunctionArg.arg("second", Families.NUMERIC))
                        .template(
                                "TO_TIMESTAMP({0} || '-' || {1} || '-' || {2} || ' ' || {3} || ':' || {4} || ':' || {5}, 'YYYY-MM-DD HH24:MI:SS')"));
        registry.register(
                new FunctionDefinition("to_timestamp", ReturnTypes.TIMESTAMP)
                        .args(
                                FunctionArg.arg("year", CoreTypeFamily.INTEGER),
                                FunctionArg.arg("month", CoreTypeFamily.INTEGER),
                                FunctionArg.arg("day", CoreTypeFamily.INTEGER),
                                FunctionArg.arg("hour", CoreTypeFamily.INTEGER),
                                FunctionArg.arg("minute", CoreTypeFamily.INTEGER),
                                FunctionArg.arg("second", Families.NUMERIC),
                                FunctionArg.arg("tz", CoreTypeFamily.TEXT))
                        .template(
                                "FROM_TZ(TO_TIMESTAMP({0} || '-' || {1} || '-' || {2} || ' ' || {3} || ':' || {4} || ':' || {5}, 'YYYY-MM-DD HH24:MI:SS'), 'UTC') AT TIME ZONE {6}"));

        // to_interval(value, unit) | to_interval(years, months, days, hours, minutes, seconds)
        // Oracle has two incompatible interval families: YEAR-TO-MONTH and DAY-TO-SECOND.
        // The 6-arg form cannot represent a mixed YM+DS interval as a standalone value —
        // use arithmetic (see renderEncodedArithmetic) to chain intervals on a DATE instead.
        registry.register(
                new FunctionDefinition("to_interval", ReturnTypes.INTERVAL) {
                    @Override
                    protected String renderBody(
                            SqlSelectRenderer renderer, Function function, int indent) {
                        String value = renderer.toSql(function.getArguments().get(0), indent);
                        String unit = renderer.toSql(function.getArguments().get(1), indent);
                        return oracleToIntervalUnit(value, unit);
                    }
                }.args(
                        FunctionArg.arg("value", Families.NUMERIC),
                        FunctionArg.arg("unit", CoreTypeFamily.TEXT)));
        registry.register(
                new FunctionDefinition("to_interval", ReturnTypes.INTERVAL) {
                    @Override
                    protected String renderBody(
                            SqlSelectRenderer renderer, Function function, int indent) {
                        // Standalone 6-arg: only valid if all non-zero values belong to one family.
                        // For arithmetic use, renderEncodedArithmetic handles the expansion.
                        var args = function.getArguments();
                        String[] vals = new String[6];
                        for (int i = 0; i < 6; i++) vals[i] = renderer.toSql(args.get(i), indent);
                        boolean hasYM = !"0".equals(vals[0].trim()) || !"0".equals(vals[1].trim());
                        boolean hasDS =
                                !"0".equals(vals[2].trim())
                                        || !"0".equals(vals[3].trim())
                                        || !"0".equals(vals[4].trim())
                                        || !"0".equals(vals[5].trim());
                        if (hasYM && hasDS) {
                            throw new UnsupportedOperationException(
                                    "Oracle cannot represent a mixed YEAR-TO-MONTH + DAY-TO-SECOND interval as a standalone value. "
                                            + "Use date arithmetic: date + to_interval(...) instead.");
                        }
                        List<String> parts = new java.util.ArrayList<>();
                        if (!"0".equals(vals[0].trim()))
                            parts.add("NUMTOYMINTERVAL(" + vals[0] + ", 'YEAR')");
                        if (!"0".equals(vals[1].trim()))
                            parts.add("NUMTOYMINTERVAL(" + vals[1] + ", 'MONTH')");
                        if (!"0".equals(vals[2].trim()))
                            parts.add("NUMTODSINTERVAL(" + vals[2] + ", 'DAY')");
                        if (!"0".equals(vals[3].trim()))
                            parts.add("NUMTODSINTERVAL(" + vals[3] + ", 'HOUR')");
                        if (!"0".equals(vals[4].trim()))
                            parts.add("NUMTODSINTERVAL(" + vals[4] + ", 'MINUTE')");
                        if (!"0".equals(vals[5].trim()))
                            parts.add("NUMTODSINTERVAL(" + vals[5] + ", 'SECOND')");
                        if (parts.isEmpty()) parts.add("NUMTODSINTERVAL(0, 'SECOND')");
                        return String.join(" + ", parts);
                    }
                }.args(
                        FunctionArg.arg("years", CoreTypeFamily.INTEGER),
                        FunctionArg.arg("months", CoreTypeFamily.INTEGER),
                        FunctionArg.arg("days", CoreTypeFamily.INTEGER),
                        FunctionArg.arg("hours", CoreTypeFamily.INTEGER),
                        FunctionArg.arg("minutes", CoreTypeFamily.INTEGER),
                        FunctionArg.arg("seconds", Families.NUMERIC)));

        // date-part extraction
        StandardFunctions.registerExtractParts(registry);
        // Oracle has no EXTRACT for these. TO_CHAR(d,'D') would depend on NLS_TERRITORY, so ISO
        // day-of-week is computed from the ISO week start instead — same answer in any session.
        registry.override("quarter", "TO_NUMBER(TO_CHAR({0}, 'Q'))");
        registry.override("week", "TO_NUMBER(TO_CHAR({0}, 'IW'))");
        registry.override("dayofweek", "(TRUNC({0}) - TRUNC({0}, 'IW') + 1)");
        registry.override("dayofyear", "TO_NUMBER(TO_CHAR({0}, 'DDD'))");
        // EXTRACT(SECOND FROM x) is the one part Oracle answers with a fraction -- 40.75 for
        // 12:14:40.75, against the 40 every other dialect gives and the INTEGER the catalog
        // declares. TRUNC, not ROUND: 40.75 rounds up to a second that did not occur. The fixture
        // never caught it because check_temporal has no sub-second timestamp.
        registry.override("second", "TRUNC(EXTRACT(SECOND FROM {0}))");

        // No listagg/instr alias: the catalog's string_agg (rendered as LISTAGG … WITHIN GROUP
        // below) and strpos already cover both, portably. A bare listagg() would in any case have
        // emitted invalid Oracle SQL, since LISTAGG requires a WITHIN GROUP clause.

        // parse_date/time/timestamp(value, format) → TO_DATE / TO_TIMESTAMP
        // Oracle format strings are canonical in KQL — no translation needed.
        // Oracle has no TIME type; parse_time maps to TO_DATE.
        registry.register(StandardFunctions.parseTwoArg("parse_date", ReturnTypes.DATE, "TO_DATE"));
        registry.register(StandardFunctions.parseTwoArg("parse_time", ReturnTypes.TIME, "TO_DATE"));
        registry.register(
                StandardFunctions.parseTwoArg(
                        "parse_timestamp", ReturnTypes.TIMESTAMP, "TO_TIMESTAMP"));

        // PostgreSQL-chapter functions: Oracle spellings

        // Distances and Rolling (TEMPORAL.md)
        // MONTHS_BETWEEN deviates from java.time when both dates are the last
        // day of their months (Oracle returns a whole month) — conformance item.
        // ADD_MONTHS snaps to the last day when the input is a last day
        // (Feb 28 + 1mo = Mar 31; java.time says Mar 28) — conformance item.

        // PostgreSQL-chapter functions without an Oracle equivalent

        // to_text is the rolled-out, tested function: keep its dialect cast-type override.
        registry.overrideAll("to_text", "CAST({0} AS VARCHAR(4000))");
        // ...except from a number. That cast goes through Oracle's implicit conversion, which
        // formats
        // with NLS_NUMERIC_CHARACTERS -- on this server a comma, so a price came out "18,4" where
        // every other dialect said "18.4". The separator is a session setting, so the same query
        // answered differently depending on who connected. TO_CHAR with an explicit NLS argument
        // pins
        // it; TM9 is the shortest form, so no padding creeps in.
        //
        // Only the numeric overloads: to_text is registered once per source family, and a boolean
        // source must keep the cast (its BOOLEAN_FROM_INTEGER encoding is decoded on the way out,
        // so
        // it reads as "true", not "1").
        for (FunctionDefinition base : List.copyOf(registry.overloads("to_text"))) {
            if (base.getSignature() != null
                    && !base.getSignature().args().isEmpty()
                    && Families.NUMERIC.accepts(base.getSignature().args().get(0).family())) {
                registry.register(
                        new FunctionDefinition(base)
                                .template(
                                        "TO_CHAR({0}, 'TM9', 'NLS_NUMERIC_CHARACTERS = ''.,''')"));
            }
        }
        // The same trap once more, temporal: CAST(date AS VARCHAR) formats with NLS_DATE_FORMAT
        // and returned "17.05.26" where every other dialect says "2026-05-17" -- a session
        // setting again, so the same query answered differently depending on who connected.
        //
        // For TIME it was more than a format: Oracle has no TIME type, the column is a DATE, and
        // the cast showed its date part -- "01.01.70" instead of "14:30:45". Not a formatting
        // difference but the wrong part of the value.
        java.util.Map<ai.koryki.catalog.types.CoreTypeFamily, String> masks =
                java.util.Map.of(
                        ai.koryki.catalog.types.CoreTypeFamily.DATE, "YYYY-MM-DD",
                        ai.koryki.catalog.types.CoreTypeFamily.TIME, "HH24:MI:SS",
                        ai.koryki.catalog.types.CoreTypeFamily.TIMESTAMP, "YYYY-MM-DD HH24:MI:SS");
        for (FunctionDefinition base : List.copyOf(registry.overloads("to_text"))) {
            if (base.getSignature() == null || base.getSignature().args().isEmpty()) {
                continue;
            }
            String mask = masks.get(base.getSignature().args().get(0).family());
            if (mask != null) {
                registry.register(
                        new FunctionDefinition(base).template("TO_CHAR({0}, '" + mask + "')"));
            }
        }
        registry.override("months_between", "TRUNC(MONTHS_BETWEEN({1}, {0}))");
        registry.override("log10", "LOG(10, {0})");
        registry.override("days_between", "TRUNC(CAST({1} AS DATE) - CAST({0} AS DATE))");
        registry.override("month_end", "TRUNC(LAST_DAY({0}))");
        registry.override("week_begin", "TRUNC({0}, 'IW')");
        registry.override("week_end", "(TRUNC({0}, 'IW') + 6)");
        registry.override("quarter_end", "LAST_DAY(ADD_MONTHS(TRUNC({0}, 'Q'), 2))");
        registry.override("year_end", "LAST_DAY(ADD_MONTHS(TRUNC({0}, 'YYYY'), 11))");
        // Oracle has no year()/month() functions for the canonical template. A single 'YYYYMM'
        // mask is the whole computation here, and it renders the argument once instead of twice
        // -- the same shape quarter and dayofyear already use.
        registry.override("year_month", "TO_NUMBER(TO_CHAR({0}, 'YYYYMM'))");

        // string functions (Oracle spellings)
        registry.override("char_length", "LENGTH({0})");
        registry.override("bit_length", "LENGTHB({0}) * 8");
        registry.override("octet_length", "LENGTHB({0})");
        registry.override("left", "SUBSTR({0}, 1, {1})");
        // right(s, n) as substr(s, -n) is wrong at the edges: for n = 0, -0 = 0, and substr(s, 0)
        // returns the WHOLE string instead of an empty one -- measured on string_edges, oracle and
        // sqlite returned "alphabet" there while duckdb and postgresql returned "". The length
        // arithmetic covers both edges: n = 0 gives empty, n > length the whole string.
        registry.override(
                "right",
                "SUBSTR({0}, CASE WHEN {1} <= 0 THEN LENGTH({0}) + 1 ELSE GREATEST(LENGTH({0}) - {1} + 1, 1) END)");
        registry.override("position", "INSTR({1}, {0})"); // position(substring, string)
        // Oracle has no IS [NOT] DISTINCT FROM (verified: ORA-00908). DECODE treats two NULLs as
        // equal and evaluates each operand once, unlike the a=b OR (a IS NULL AND b IS NULL)
        // expansion.
        registry.override("DISTINCT", "DECODE({0}, {1}, 0, 1) = 1");
        registry.override("substring", "SUBSTR({*})");
        registry.override("starts_with", "(SUBSTR({0}, 1, LENGTH({1})) = {1})");
        // btrim(s) and btrim(s, characters) need different forms, and an optional argument is ONE
        // definition with an arity range -- an arity-specific template would replace both. Hence a
        // renderBody of its own, as DuckdbBaseDialect uses for regexp_count. Oracle's TRIM takes
        // no character set; LTRIM/RTRIM do, and the two in sequence are exactly the both-sided
        // variant. Before, the second argument was silently discarded.
        for (String name : List.of("trim")) {
            registry.register(
                    new FunctionDefinition(registry.overloads(name).get(0)) {
                        @Override
                        protected String renderBody(
                                SqlSelectRenderer renderer, Function function, int indent) {
                            var args = function.getArguments();
                            String s = renderer.toSql(args.get(0), indent);
                            if (args.size() == 1) {
                                return "TRIM(" + s + ")";
                            }
                            String chars = renderer.toSql(args.get(1), indent);
                            return "LTRIM(RTRIM(" + s + ", " + chars + "), " + chars + ")";
                        }
                    });
        }
        // ltrim and rtrim need nothing: Oracle takes a character set there out of the box.
        registry.override("repeat", "RPAD({0}, {1} * LENGTH({0}), {0})");
        // random() is documented as 0.0 <= x < 1.0 and went to every engine verbatim; measured,
        // Oracle answered ORA-00904 ("RANDOM": ungueltige ID). DBMS_RANDOM.VALUE is its form.
        registry.override("random", "DBMS_RANDOM.VALUE");
        registry.override("to_hex", "TO_CHAR({0}, 'FMXXXXXXXXXXXXXXXX')");

        // math / numeric casts
        registry.override("pi", "ACOS(-1)");
        registry.override("to_bigint", "CAST({0} AS NUMBER(19))"); // Oracle has no BIGINT
        registry.override("to_double", "CAST({0} AS BINARY_DOUBLE)"); // Oracle has no DOUBLE

        // date/time — Oracle has no TIME type; time() renders as a HH24:MI:SS string
        registry.override("minute_begin", "TRUNC(CAST({0} AS DATE), 'MI')");
        registry.override("hour_begin", "TRUNC(CAST({0} AS DATE), 'HH')");
        registry.override("day_begin", "TRUNC(CAST({0} AS DATE))");
        registry.override("month_begin", "TRUNC({0}, 'MM')");
        registry.override("year_begin", "TRUNC({0}, 'YYYY')");
        registry.override("quarter_begin", "TRUNC({0}, 'Q')");
        registry.override("day_add", "({0} + NUMTODSINTERVAL({1}, 'DAY'))");
        // ADD_MONTHS alone would be wrong: it does not only clamp, it *promotes*. When the source
        // day is its month's last, Oracle returns the last day of the target month -- even where
        // the source day exists there. month_add('2022-09-30', 1) came out as October 31, while
        // TEMPORAL.md ("clamp ... when the target day does not exist") and the other seven
        // dialects say the 30th.
        //
        // The correction uses that ADD_MONTHS yields day = min(source day, month end) *except*
        // under promotion, where it comes out too large; a day that is too small can never arise.
        // Subtracting the excess is therefore enough -- no CASE required, and subtracting whole
        // days from a DATE keeps the time of day.
        String corrected =
                "(ADD_MONTHS({0}, {1}) - GREATEST(EXTRACT(DAY FROM ADD_MONTHS({0}, {1}))"
                        + " - EXTRACT(DAY FROM {0}), 0))";
        registry.override("month_add", corrected);
        registry.override("year_add", corrected.replace("{1}", "{1} * 12"));
        registry.override("make_date", "TO_DATE({0} || '-' || {1} || '-' || {2}, 'YYYY-MM-DD')");
        registry.override(
                "make_time",
                "LPAD({0}, 2, '0') || ':' || LPAD({1}, 2, '0') || ':' || LPAD({2}, 2, '0')");
        registry.override(
                "make_timestamp",
                "TO_TIMESTAMP({0} || '-' || {1} || '-' || {2} || ' ' || {3} || ':' || {4} || ':' || {5}, "
                        + "'YYYY-MM-DD HH24:MI:SS')");
        registry.override("years_between", "TRUNC(MONTHS_BETWEEN({1}, {0}) / 12)");

        // date_trunc → TRUNC(value [, fmt])
        registry.register(
                new FunctionDefinition("date_trunc", ReturnTypes.ARG1) {
                    @Override
                    protected String renderBody(
                            SqlSelectRenderer renderer, Function function, int indent) {
                        String unit =
                                renderer.toSql(function.getArguments().get(0), indent)
                                        .replace("'", "");
                        String d = renderer.toSql(function.getArguments().get(1), indent);
                        return switch (unit) {
                            case "minute" -> "TRUNC(CAST(" + d + " AS DATE), 'MI')";
                            case "hour" -> "TRUNC(CAST(" + d + " AS DATE), 'HH')";
                            case "day" -> "TRUNC(" + d + ")";
                            case "month" -> "TRUNC(" + d + ", 'MM')";
                            case "quarter" -> "TRUNC(" + d + ", 'Q')";
                            case "year" -> "TRUNC(" + d + ", 'YYYY')";
                            default ->
                                    throw new UnsupportedOperationException(
                                            "date_trunc unit not supported by Oracle: " + unit);
                        };
                    }
                }.args(
                        FunctionArg.arg("unit", CoreTypeFamily.TEXT),
                        FunctionArg.arg("value", Families.TEMPORAL)));

        // overlay(string, replacement, start [, length]) → splice with SUBSTR (Oracle has no
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
                                        : "LENGTH(" + repl + ")";
                        return "SUBSTR("
                                + s
                                + ", 1, "
                                + start
                                + " - 1) || "
                                + repl
                                + " || SUBSTR("
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

        // LISTAGG requires WITHIN GROUP, so the two-argument form has to name a sort even
        // though KQL leaves it unspecified; ordering by the value itself is the neutral
        // choice. LISTAGG OVER () accepts only PARTITION BY, never a window ORDER.
        registry.override("string_agg", 2, "LISTAGG({0}, {1}) WITHIN GROUP (ORDER BY {0})");
        registry.override("string_agg", 3, "LISTAGG({0}, {1}) WITHIN GROUP (ORDER BY {2})");
        registry.windowUnsupported("string_agg");

        // no md5 hashing, split_part or concat_ws in core Oracle.
        //
        // reverse is different: Oracle *has* it, and it works on ASCII — but it reverses BYTES, not
        // characters, so multibyte text comes back corrupted. Measured, reverse('Königlich') gives
        // 'hcilgin��K' here against 'hcilginöK' on every other engine, with no error. A
        // function that silently returns mojibake is worse than one that is absent, so it is
        // refused rather than documented. (Oracle's REVERSE is undocumented for exactly this
        // reason.)
        for (String fn : List.of("md5", "split_part", "concat_ws", "reverse")) {
            registry.unsupported(fn);
        }

        // Measured: Oracle's COUNT(DISTINCT ...) takes exactly one expression — neither the row
        // constructor nor a comma list parses. Concatenation works and is what the hint names.
        registry.unsupportedVariadic(
                "count_distinct",
                ai.koryki.iql.functions.catalog.AggregateFunctions.COUNT_DISTINCT_HINT);
        return registry;
    }

    @Override
    public String mapSetOperator(String operator) {
        // Oracle uses MINUS natively; no mapping needed.
        return operator;
    }

    @Override
    public String timestampLiteral(LocalDateTime dateTime) {
        return "TIMESTAMP '" + SqlDialect.plainTimestamp(dateTime) + "'";
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

    /** An Oracle DATE carries a time component, so the raw difference can be fractional days. */
    @Override
    public String dateDiffDays(String leftSql, String rightSql) {
        return "TRUNC(" + leftSql + " - " + rightSql + ")";
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
        // EPOCH timestamp ± duration: materialize to a TIMESTAMP first (epoch-day stays inside
        // expand*)
        leftSql = materializeEpochTimestampLeft(leftSql, leftType);
        return expandOracleArithmetic(renderer, operator, leftSql, leftType, right, indent);
    }

    /**
     * Oracle has no {@code make_timestamp}; epoch-seconds → TIMESTAMP via NUMTODSINTERVAL on the
     * epoch base.
     */
    @Override
    public String epochToTimestamp(String expr, java.time.temporal.ChronoUnit unit) {
        return "(TIMESTAMP '1970-01-01 00:00:00' + NUMTODSINTERVAL("
                + SqlDialect.secondsFromEpoch(expr, unit)
                + ", 'SECOND'))";
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

    /**
     * Oracle casts numbers to text with {@code TO_CHAR} (plain {@code CAST(.. AS VARCHAR)} needs a
     * length).
     */
    @Override
    public String pairText(String a, String b) {
        return "(TO_CHAR(" + a + ") || ';' || TO_CHAR(" + b + "))";
    }

    @Override
    public String renderComparisonOperand(
            SqlSelectRenderer renderer,
            Expression expression,
            TypeDescriptor leftType,
            TypeDescriptor rightType,
            int indent) {
        // DATE-encoded TIME columns compare against an epoch-anchored DATE, not a seconds number
        java.util.Optional<java.time.LocalDateTime> anchored =
                TimeEncodings.timeFromDateOperand(leftType, expression);
        if (anchored.isPresent()) {
            return "TO_DATE('"
                    + anchored.get().format(TIMESTAMP_FMT)
                    + "', 'YYYY-MM-DD HH24:MI:SS')";
        }
        if (CoreTypeFamily.TIME.equals(leftType.getTypeFamily())
                && expression.getLocalTime() != null) {
            return String.valueOf(expression.getLocalTime().toSecondOfDay());
        }
        return SqlDialect.super.renderComparisonOperand(
                renderer, expression, leftType, rightType, indent);
    }

    @Override
    public String recursive(boolean recursive) {
        return "";
    }

    /**
     * Oracle has no {@code TIME} type, so a 'HH:MM:SS' text time is taken apart with {@code SUBSTR}
     * rather than cast. {@code MOD} and {@code FLOOR} are native.
     */
    @Override
    public String timeColumnAsSeconds(String columnSql, TypeDescriptor timeType) {
        var enc = timeType != null ? timeType.getTypeEncoding() : null;
        if (CoreTypeEncoding.TIME_FROM_STRING.equals(enc)) {
            return "TO_NUMBER(SUBSTR("
                    + columnSql
                    + ", 1, 2)) * 3600"
                    + " + TO_NUMBER(SUBSTR("
                    + columnSql
                    + ", 4, 2)) * 60"
                    + " + TO_NUMBER(SUBSTR("
                    + columnSql
                    + ", 7, 2))";
        }
        return SqlDialect.super.timeColumnAsSeconds(columnSql, timeType);
    }

    /** Same DECODE, testing for the other outcome — no NOT wrapper needed. */
    @Override
    public String negatedOperatorTemplate(String op) {
        return "DISTINCT".equals(op)
                ? "DECODE({0}, {1}, 0, 1) = 0"
                : SqlDialect.super.negatedOperatorTemplate(op);
    }

    /**
     * Oracle keeps INTERVAL YEAR TO MONTH and INTERVAL DAY TO SECOND apart, with no type spanning
     * both.
     */
    @Override
    public ai.koryki.iql.SqlDialect.IntervalSupport intervalSupport() {
        return ai.koryki.iql.SqlDialect.IntervalSupport.SPLIT;
    }
}
