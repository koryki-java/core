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

import ai.koryki.catalog.types.*;
import ai.koryki.iql.functions.Fixity;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionRenderer;
import ai.koryki.iql.functions.StandardFunctions;
import ai.koryki.iql.query.Duration;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
import ai.koryki.iql.typing.EpochEncodings;
import ai.koryki.iql.typing.InstantEncodings;
import ai.koryki.iql.typing.IntervalEncodings;
import ai.koryki.iql.typing.TimeEncodings;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public interface SqlDialect {

    /**
     * The function renderer for this dialect. The default is the shared, dialect-neutral
     * canonical set; dialects that add or replace functions override this to return their
     * own {@code static final} renderer (assembled once at class load).
     */
    default FunctionRenderer getFunctionRenderer() {
        return StandardFunctions.canonical();
    }

    default String timeLiteral(LocalTime time) {
        return "TIME '" + time + "'";
    }

    default String dateLiteral(LocalDate date) {
        return "DATE '" + date + "'";
    }

    default String timestampLiteral(LocalDateTime dateTime) {
        return "TIMESTAMP '" + dateTime + "'";
    }

    /**
     * Renders {@code instant} as this dialect's instant literal, for an {@code INSTANT} ({@code timestamptz})
     * column compared against a literal (docs/TEMPORAL.md). The default presents it as the model-zone
     * wall-clock value (correct where the session zone is pinned to the model zone, or where the INSTANT
     * column surfaces naive); dialects whose instant columns need an explicit offset (e.g. SQL Server
     * {@code DATETIMEOFFSET}) override it.
     */
    default String instantLiteral(java.time.Instant instant, java.time.ZoneId modelZone) {
        return timestampLiteral(java.time.LocalDateTime.ofInstant(instant, modelZone));
    }

    default String durationLiteral(Duration duration) {
        return duration.getComponents().stream()
                .map(c -> {
                    String unit = switch (c.unit()) {
                        case MILLISECOND -> "MILLISECOND";
                        case SECOND      -> "SECOND";
                        case MINUTE      -> "MINUTE";
                        case HOUR        -> "HOUR";
                        case DAY         -> "DAY";
                        case WEEK        -> "WEEK";
                        case MONTH       -> "MONTH";
                        case QUARTAL     -> "QUARTER";
                        case YEAR        -> "YEAR";
                    };
                    return "INTERVAL '" + c.value() + "' " + unit;
                })
                .collect(Collectors.joining(" + "));
    }

    /**
     * One combined verbose INTERVAL literal — {@code INTERVAL '1 year 2 month 3 day'} — for
     * dialects that accept all components in a single literal (DuckDB/Postgres use {@code " "},
     * Snowflake {@code ", "}). Weeks fold to days, quarters to months.
     */
    static String combinedInterval(Duration duration, String separator) {
        String parts = duration.getComponents().stream()
                .map(c -> switch (c.unit()) {
                    case YEAR        -> c.value() + " year";
                    case QUARTAL     -> (c.value() * 3) + " month";
                    case MONTH       -> c.value() + " month";
                    case WEEK        -> (c.value() * 7) + " day";
                    case DAY         -> c.value() + " day";
                    case HOUR        -> c.value() + " hour";
                    case MINUTE      -> c.value() + " minute";
                    case SECOND      -> c.value() + " second";
                    case MILLISECOND -> c.value() + " millisecond";
                })
                .collect(Collectors.joining(separator));
        return "INTERVAL '" + parts + "'";
    }

    default String mapSetOperator(String operator) {
        return operator;
    }

    default String recursive(boolean recursive) {
        return recursive ? "RECURSIVE " : "";
    }

    /** Text inserted right after the GROUP BY keyword when ROLLUP is requested. */
    default String rollupPrefix() {
        return " ROLLUP (";
    }

    /** Text appended after the grouping columns when ROLLUP is requested. */
    default String rollupSuffix() {
        return ")";
    }

    /**
     * Renders the row-limit clause (a full line incl. indentation and line separator).
     * Default is ANSI {@code FETCH FIRST n ROWS ONLY}; {@code hasOrderBy} lets dialects
     * that require an ORDER BY for paging (e.g. T-SQL OFFSET/FETCH) react accordingly.
     */
    default String limitClause(int limit, boolean hasOrderBy, int indent) {
        return Identifier.indent(indent) + "FETCH FIRST " + limit + " ROWS ONLY" + System.lineSeparator();
    }

    /**
     * Renders a column type for DDL (CREATE TABLE) — the write-side dual of
     * {@link TypeDescriptorParser}.
     *
     * <p>The default echoes the parsed physical type verbatim (a faithful round-trip
     * for the same dialect). Dialects override to map a canonical {@link TypeDescriptor}
     * (family + precision/scale + encoding) onto their own physical type — e.g.
     * TEXT-&gt;VARCHAR2 (Oracle), BOOLEAN-&gt;BIT and TIMESTAMP-&gt;DATETIME2 (T-SQL),
     * or the TIME_SECONDS_FROM_MIDNIGHT encoding -&gt; INTEGER.
     */
    default String renderType(TypeDescriptor type) {
        return type.getPhysicalTypeName();
    }

    default String renderFunction(SqlSelectRenderer renderer, Function function, int indent) {
        return null;
    }

    default String renderEncodedArithmetic(SqlSelectRenderer renderer, String operator,
            Expression left, TypeDescriptor leftType,
            Expression right, TypeDescriptor rightType,
            int indent) {
        return renderEncodedArithmetic(renderer, operator, renderer.toSql(left, indent), leftType, right, rightType, indent);
    }

    /**
     * SQL converting a wall-clock(zone) column from its declared storage zone to the model zone
     * (docs/TEMPORAL.md). Applied at <em>every</em> reference to such a column — bare, in arithmetic, in
     * a comparison — so the value is a model-zone wall-clock value before any operation. The conversion
     * must precede arithmetic: a stored wall-clock value is naive/local, so add-then-convert would be
     * wrong across a DST transition (a clock unit must not absorb the offset shift).
     *
     * <p>The default rejects: a dialect that has not wired named-zone conversion does not support
     * wall-clock(zone) storage. Wall-clock columns therefore appear only in fixtures that {@code ignore}
     * such dialects (e.g. SQLite, which has no time-zone database at all). See docs/TEMPORAL.md.
     */
    default String wallClockToModelZone(String columnSql,
                                        WallClockEncoding enc, java.time.ZoneId modelZone) {
        throw new ai.koryki.antlr.KorykiaiException(
                "wall-clock(zone) storage (" + enc.name() + ") is not supported by this dialect");
    }

    /**
     * The ANSI {@code AT TIME ZONE} two-step, for engines whose operator flips a naive timestamp to an
     * instant and back (DuckDB, PostgreSQL): read the stored value as declared-zone local, then render it
     * as model-zone local. A {@code DATE_WALLCLOCK} converts at start-of-day and is taken back to a date.
     */
    static String atTimeZoneToModelZone(String columnSql,
                                        WallClockEncoding enc, java.time.ZoneId modelZone) {
        String declared = enc.getZone().getId();
        String model = modelZone.getId();
        if (CoreTypeFamily.DATE.equals(enc.family())) {
            return "CAST(((CAST(" + columnSql + " AS TIMESTAMP) AT TIME ZONE '" + declared
                    + "') AT TIME ZONE '" + model + "') AS DATE)";
        }
        return "((" + columnSql + " AT TIME ZONE '" + declared + "') AT TIME ZONE '" + model + "')";
    }

    /**
     * SQL shifting a TIMESTAMP value — read as a wall-clock in {@code fromZoneSql} — to its wall-clock in
     * {@code toZoneSql} (both already SQL-quoted, e.g. {@code 'UTC'}). Backs the explicit {@code at_zone()}
     * / {@code to_utc()} functions (docs/TEMPORAL.md). The default rejects — a dialect without named-zone
     * conversion does not support explicit zone crossing (the same engines that reject wall-clock(zone)).
     */
    default String zoneShiftTimestamp(String valueSql, String fromZoneSql, String toZoneSql) {
        throw new ai.koryki.antlr.KorykiaiException(
                "explicit zone conversion (at_zone / to_utc) is not supported by this dialect");
    }

    /** The ANSI {@code AT TIME ZONE} two-step shift, shared by DuckDB and PostgreSQL. */
    static String atTimeZoneShift(String valueSql, String fromZoneSql, String toZoneSql) {
        return "((" + valueSql + " AT TIME ZONE " + fromZoneSql + ") AT TIME ZONE " + toZoneSql + ")";
    }

    default String renderEncodedArithmetic(SqlSelectRenderer renderer, String operator,
            String leftSql, TypeDescriptor leftType,
            Expression right, TypeDescriptor rightType,
            int indent) {
        String diff = renderTimestampDiff(renderer, operator, leftSql, leftType, right, rightType, indent);
        if (diff != null) {
            return diff;
        }
        return TimeEncodings
                .secondsArithmetic(renderer, leftSql, leftType, operator, right, indent)
                .orElseGet(() -> materializeTemporalLeft(leftSql, leftType)
                        + " " + operator + " " + parenthesizeDuration(renderer, operator, right, indent));
    }

    /**
     * Materialize an integer-encoded temporal column to a real DATE/TIMESTAMP before a duration is
     * applied: a {@code DATE_FROM_EPOCH_DAY} becomes a DATE, an {@code EPOCH:<unit>} timestamp becomes a
     * TIMESTAMP (via {@link #epochToTimestamp}). Other columns pass through unchanged. Dialects that
     * override {@link #renderEncodedArithmetic} call this (or their own equivalent) for the same reason.
     */
    default String materializeTemporalLeft(String leftSql, TypeDescriptor leftType) {
        if (leftType != null
                && CoreTypeEncoding.DATE_FROM_EPOCH_DAY.equals(leftType.getTypeEncoding())) {
            return "(DATE '1970-01-01' + " + leftSql + ")";
        }
        return materializeEpochTimestampLeft(leftSql, leftType);
    }

    /**
     * Materialize an {@code EPOCH:<unit>} timestamp column to a TIMESTAMP via {@link #epochToTimestamp}
     * (else pass through). The dialects that override {@link #renderEncodedArithmetic} call this for the
     * epoch case — they keep their own DATE_FROM_EPOCH_DAY handling.
     */
    default String materializeEpochTimestampLeft(String leftSql, TypeDescriptor leftType) {
        if (leftType != null && leftType.getTypeEncoding() instanceof EpochTypeEncoding e) {
            return epochToTimestamp(leftSql, e.getUnit());
        }
        return leftSql;
    }

    /**
     * Inverse of {@link #timestampToEpochSeconds}: an epoch count (in {@code unit}) → a TIMESTAMP, so an
     * EPOCH-encoded column can be shifted by a duration. Default is DuckDB {@code make_timestamp}
     * (microseconds); dialects without it override.
     */
    default String epochToTimestamp(String expr, java.time.temporal.ChronoUnit unit) {
        String micros = switch (unit) {
            case MILLIS -> "(" + expr + " * 1000)";
            case MICROS -> expr;
            case NANOS  -> "(" + expr + " / 1000)";
            default     -> "(" + expr + " * 1000000)";   // SECONDS
        };
        return "make_timestamp(" + micros + ")";
    }

    /** An epoch count in {@code unit} → an integer-seconds expression — a helper for the seconds-based
     *  {@link #epochToTimestamp} overrides (whole-second epochs; sub-second is not preserved). */
    static String secondsFromEpoch(String expr, java.time.temporal.ChronoUnit unit) {
        return switch (unit) {
            case MILLIS -> "(" + expr + " / 1000)";
            case MICROS -> "(" + expr + " / 1000000)";
            case NANOS  -> "(" + expr + " / 1000000000)";
            default     -> expr;   // SECONDS
        };
    }

    /**
     * TIMESTAMP − TIMESTAMP → the exact elapsed span as integer epoch-seconds (decoded as a fixed
     * INTERVAL:SECONDS, so a span over 24h renders 50:00:00, never calendar days); {@code null} when the
     * operands are not a timestamp difference. Each side reduces to epoch-seconds per its own encoding,
     * which also reconciles mixed encodings: the same instant stored as INSTANT, EPOCH:SECONDS or
     * EPOCH:MILLIS yields the same seconds, so their difference is zero.
     *
     * <p>Shared so the semantics stay uniform across dialects: dialects that override
     * {@link #renderEncodedArithmetic} call this first, and only vary {@link #epochSeconds}.
     */
    default String renderTimestampDiff(SqlSelectRenderer renderer, String operator,
            String leftSql, TypeDescriptor leftType, Expression right, TypeDescriptor rightType, int indent) {
        if ("-".equals(operator) && isTimestamp(leftType) && isTimestamp(rightType)) {
            return epochSeconds(leftSql, leftType) + " - " + epochSeconds(renderer.toSql(right, indent), rightType);
        }
        return null;
    }

    private static boolean isTimestamp(TypeDescriptor t) {
        return t != null && CoreTypeFamily.TIMESTAMP.equals(t.getTypeFamily());
    }

    /**
     * Reduce a TIMESTAMP-family expression to integer epoch-seconds, honoring its storage encoding so
     * that a difference is exact regardless of how each operand happens to be stored: an EPOCH:SECONDS
     * column is already seconds, EPOCH:MILLIS is divided by 1000, and an INSTANT / wall-clock TIMESTAMP
     * goes through {@code EXTRACT(EPOCH ...)}. Dialects without {@code EXTRACT(EPOCH ...)} override the
     * timestamp branch via {@link #timestampToEpochSeconds}.
     */
    default String epochSeconds(String expr, TypeDescriptor type) {
        var enc = type != null ? type.getTypeEncoding() : null;
        if (enc instanceof EpochTypeEncoding e) {
            return switch (e.getUnit()) {
                case MILLIS -> "(" + expr + " / 1000)";
                case MICROS -> "(" + expr + " / 1000000)";
                case NANOS  -> "(" + expr + " / 1000000000)";
                default     -> expr;   // SECONDS: already epoch-seconds
            };
        }
        return timestampToEpochSeconds(unwrapOuterParens(expr), isInstant(enc));
    }

    // Strip one matching outer layer of parentheses so that function-call wrappers
    // (UNIX_TIMESTAMP, EXTRACT EPOCH) don't produce double parens around already-
    // parenthesized KQL sub-expressions like (c.ts - 3h20min30s).
    private static String unwrapOuterParens(String expr) {
        if (expr.length() < 2 || expr.charAt(0) != '(') return expr;
        int depth = 0;
        for (int i = 0; i < expr.length(); i++) {
            if (expr.charAt(i) == '(') depth++;
            else if (expr.charAt(i) == ')' && --depth == 0) {
                return i == expr.length() - 1 ? expr.substring(1, expr.length() - 1) : expr;
            }
        }
        return expr;
    }

    private static boolean isInstant(TypeEncoding enc) {
        return CoreTypeEncoding.INSTANT.equals(enc);
    }

    /**
     * A real timestamp/datetime expression → integer epoch-seconds. {@code instant} is {@code true} for a
     * zone-aware (INSTANT) operand and {@code false} for a wall-clock TIMESTAMP; dialects whose epoch
     * conversion differs for the two (e.g. an offset/text suffix needing normalization) use the flag.
     * Default is the SQL-standard {@code EXTRACT(EPOCH ...)} (duckdb/postgres); others override.
     */
    default String timestampToEpochSeconds(String expr, boolean instant) {
        String wrapped = expr.startsWith("(") ? expr : "(" + expr + ")";
        return "CAST(EXTRACT(EPOCH FROM " + wrapped + ") AS BIGINT)";
    }

    /**
     * Combine two numeric expressions into the {@code "a;b"} text pair that {@code calendar_distance}
     * emits (decoded to a calendar Interval in Java). Default uses ANSI {@code ||} + CAST; dialects whose
     * concatenation or integer-to-text cast differ (MySQL/T-SQL {@code CONCAT}, Oracle {@code TO_CHAR})
     * override.
     */
    default String pairText(String a, String b) {
        return "(CAST(" + a + " AS VARCHAR) || ';' || CAST(" + b + " AS VARCHAR))";
    }


    /**
     * Subtracting a multi-component duration must parenthesize it so the {@code -} negates the
     * whole amount, not just the first term ({@code d - (a + b)}, not {@code d - a + b}). A
     * single component or addition needs no parens.
     */
    private static String parenthesizeDuration(SqlSelectRenderer renderer, String operator, Expression right, int indent) {
        String sql = renderer.toSql(right, indent);
        // Only an additive sum (e.g. a dialect that renders to_days(1) + to_hours(1)) needs wrapping;
        // a single native INTERVAL literal is already atomic.
        boolean additiveSum = right.getDuration() != null && sql.contains(" + ");
        return "-".equals(operator) && additiveSum ? "(" + sql + ")" : sql;
    }

    /**
     * Render a TIME-encoded column as integer seconds-of-day, for TIME ± duration arithmetic
     * (computed in the seconds domain, then floor-mod-decoded to a LocalTime at the read boundary,
     * which is how midnight wraps). Only the {@link TimeEncodings#secondsConvertible
     * convertible} encodings reach here.
     *
     * <p>The default handles {@code TIME_FROM_INTEGER} (HHMMSS packed integer) and
     * {@code TIME_FROM_STRING} ('HH:MM:SS' text) with portable {@code CAST}/{@code MOD}/{@code EXTRACT};
     * a seconds-from-midnight column is already the seconds value. Dialects whose integer division,
     * modulo or time-cast differ (e.g. no {@code EXTRACT(EPOCH ...)}) override this hook.
     */
    default String timeColumnAsSeconds(String columnSql, TypeDescriptor timeType) {
        var enc = timeType != null ? timeType.getTypeEncoding() : null;
        if (CoreTypeEncoding.TIME_FROM_INTEGER.equals(enc)) {
            // HHMMSS -> seconds: hh*3600 + mm*60 + ss. FLOOR before CAST: a bare CAST(<fraction> AS
            // INTEGER) rounds (not truncates) in some dialects (e.g. DuckDB: 23.5959 -> 24).
            return "CAST(FLOOR(" + columnSql + " / 10000.0) AS INTEGER) * 3600"
                 + " + MOD(CAST(FLOOR(" + columnSql + " / 100.0) AS INTEGER), 100) * 60"
                 + " + MOD(" + columnSql + ", 100)";
        }
        if (CoreTypeEncoding.TIME_FROM_STRING.equals(enc)) {
            // 'HH:MM:SS' text -> seconds-of-day
            return "CAST(EXTRACT(EPOCH FROM CAST(" + columnSql + " AS TIME)) AS INTEGER)";
        }
        return columnSql;   // TIME_SECONDS_FROM_MIDNIGHT: already the seconds value
    }

    default String mapOperator(String op) {
        if ("ISNULL".equalsIgnoreCase(op)) return "IS NULL";
        return op;
    }

    default String renderComparison(SqlSelectRenderer renderer,
            Expression left, TypeDescriptor leftType,
            String op, List<Expression> right, int indent) {
        String leftSql = renderer.toSql(left, indent);

        // Catalog-driven: resolve the operator by its surface text and render
        // through its (possibly dialect-overridden) template. Operands still
        // flow through operand(...) so comparison encoding reconciliation applies.
        // Argument-family constraints (e.g. LIKE requires TEXT) are enforced by
        // FunctionValidator, not here.
        FunctionDefinition operator = operatorDefinition(renderer, op);
        if (operator != null && operator.getTemplate() != null) {
            List<String> operands = new ArrayList<>();
            operands.add(leftSql);
            for (Expression e : right) {
                operands.add(operand(renderer, e, leftType, indent));
            }
            return operator.getTemplate().fill(operands);
        }

        // Fallback for operators not in the catalog (e.g. a custom `op=ID` from
        // the grammar): the legacy structural rendering.
        String mappedOp = mapOperator(op);
        if (SqlSelectRenderer.isInterval(op)) {
            return leftSql + " " + mappedOp + " "
                    + operand(renderer, right.get(0), leftType, indent)
                    + " AND "
                    + operand(renderer, right.get(1), leftType, indent);
        } else if (SqlSelectRenderer.isSet(op)) {
            return leftSql + " " + mappedOp + " ("
                    + right.stream()
                           .map(e -> operand(renderer, e, leftType, indent))
                           .collect(Collectors.joining(", ")) + ")";
        } else if (right.isEmpty()) {
            return leftSql + " " + mappedOp;
        } else {
            return leftSql + " " + mappedOp + " "
                    + right.stream()
                           .map(e -> operand(renderer, e, leftType, indent))
                           .collect(Collectors.joining(" "));
        }
    }

    /**
     * The operator definition for {@code op} (resolved by surface text, e.g.
     * {@code "="}, {@code "BETWEEN"}), or {@code null} for an unregistered or
     * custom operator. Only operator-fixity entries with a template qualify.
     */
    private FunctionDefinition operatorDefinition(SqlSelectRenderer renderer, String op) {
        for (FunctionDefinition d : renderer.getFunctionRenderer().overloads(op)) {
            if (d.getFixity() != Fixity.PREFIX && d.getTemplate() != null) {
                return d;
            }
        }
        return null;
    }

    /**
     * Resolves the operand's own type once and hands both sides to {@link #renderComparisonOperand}.
     * The type is a best-effort hint for encoding reconciliation, so an operand that can't be typed
     * (subquery, identity, …) passes {@code null} rather than failing the render.
     */
    private String operand(SqlSelectRenderer renderer, Expression e, TypeDescriptor leftType, int indent) {
        TypeDescriptor rightType;
        try {
            rightType = renderer.resolveType(e);
        } catch (RuntimeException unresolved) {
            rightType = null;
        }
        return renderComparisonOperand(renderer, e, leftType, rightType, indent);
    }

    /**
     * One operand of a comparison, given the types of <em>both</em> sides. The
     * ANSI default just renders the expression; dialects whose schemas may carry
     * encoded columns opt in via {@link TimeEncodings} —
     * having {@code rightType} lets them reconcile two operands stored under
     * different encodings (e.g. TIME_FROM_INTEGER vs TIME_FROM_STRING), not just
     * encode a literal to match the left.
     */
    default String renderComparisonOperand(SqlSelectRenderer renderer,
            Expression expression, TypeDescriptor leftType, TypeDescriptor rightType, int indent) {
        // INTERVAL column vs duration literal: render the duration in the column's encoding
        // (numeric count / ISO string), so both operands share one physical representation.
        java.util.Optional<String> interval =
                IntervalEncodings.durationOperand(leftType, expression);
        if (interval.isPresent()) {
            return interval.get();
        }
        // EPOCH / epoch-day column vs a date/timestamp literal: render the literal as the matching
        // integer count, so the bare (index-friendly) column compares against an integer, not a literal.
        java.util.Optional<String> epoch =
                EpochEncodings.literalOperand(leftType, expression, renderer.getModelZone());
        if (epoch.isPresent()) {
            return epoch.get();
        }
        // INSTANT (timestamptz) column vs a date/timestamp literal: render the literal as the matching
        // absolute instant (taken in the model zone), so the comparison does not lean on the engine's
        // implicit coercion of a bare naive string (which fails on SQL Server / Trino).
        java.util.Optional<java.time.Instant> instant =
                InstantEncodings.literalInstant(leftType, expression, renderer.getModelZone());
        if (instant.isPresent()) {
            return instantLiteral(instant.get(), renderer.getModelZone());
        }
        return renderer.toSql(expression, indent);
    }
}
