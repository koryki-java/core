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
package ai.koryki.iql.types;

import ai.koryki.catalog.schema.types.CoreTypeEncoding;
import ai.koryki.catalog.schema.types.CoreTypeFamily;
import ai.koryki.catalog.schema.types.TypeDescriptor;
import ai.koryki.iql.SqlSelectRenderer;
import ai.koryki.iql.query.Duration;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Rendering support for column-level TIME encodings. The encoding is a
 * property of the column (declared in the schema's type descriptors), not of
 * the dialect — any dialect whose schemas may carry such columns opts in by
 * consulting this helper in its rendering hooks.
 */
public final class TimeEncodings {

    private TimeEncodings() {
    }

    /**
     * If the compared column stores TIME as seconds from midnight and the
     * operand is a time literal, the literal renders as its second-of-day
     * value (e.g. {@code TIME '14:30:00'} → {@code 52200}).
     *
     * @return the seconds literal, or empty if the encoding does not apply
     */
    public static Optional<String> secondsFromMidnightLiteral(TypeDescriptor columnType, Expression operand) {
        if (columnType != null
                && CoreTypeEncoding.TIME_SECONDS_FROM_MIDNIGHT.equals(columnType.getTypeEncoding())
                && operand.getLocalTime() != null) {
            return Optional.of(String.valueOf(operand.getLocalTime().toSecondOfDay()));
        }
        return Optional.empty();
    }

    /**
     * Reconciles a comparison operand when the two sides are the same family but stored under
     * <em>different</em> encodings — e.g. a {@code TIME_FROM_INTEGER} column ({@code "123050"})
     * compared against a {@code TIME_FROM_STRING} value ({@code "12:30:50"}). With both sides'
     * encodings known, the operand can be brought into the left column's encoding (or both
     * decoded to a common TIME); empty means no cross-encoding reconciliation is needed and the
     * caller falls back to its normal rendering.
     *
     * <p>STUB: the conversion policy (which side to convert, and the per-encoding decode/encode
     * SQL) is not yet decided. Returns {@link Optional#empty()} so behavior is unchanged until
     * filled in.
     */
    public static Optional<String> reconcile(SqlSelectRenderer renderer, Expression operand,
            TypeDescriptor leftType, TypeDescriptor rightType, int indent) {
        if (leftType == null || rightType == null) {
            return Optional.empty();
        }
        if (java.util.Objects.equals(leftType.getTypeEncoding(), rightType.getTypeEncoding())) {
            return Optional.empty();   // same encoding (incl. both null) — nothing to reconcile
        }
        // TODO: both operands carry TIME encodings that differ — choose a canonical side and
        // render the decode/encode (e.g. TIME_FROM_STRING "12:30:50" -> 123050 to match a
        // TIME_FROM_INTEGER column). Until decided, leave rendering unchanged.
        return Optional.empty();
    }

    /**
     * Anchor day for encodings that embed a time of day in a DATE/TIMESTAMP
     * column — must match the {@code DATE '1970-01-01'} anchor used when
     * rendering such columns for output.
     */
    public static final LocalDate EPOCH_DAY = LocalDate.of(1970, 1, 1);

    /**
     * If the compared column stores TIME truncated from a DATE
     * ({@link CoreTypeEncoding#TIME_FROM_DATE}) and the operand is a time
     * literal, the operand becomes a date-time on the anchor day
     * (e.g. {@code TIME '14:30:00'} → {@code 1970-01-01T14:30:00}).
     * The literal syntax for that value is the dialect's decision
     * (e.g. {@code TO_DATE(...)} on Oracle, {@code TIMESTAMP '...'} ANSI).
     *
     * @return the epoch-anchored date-time, or empty if the encoding does not apply
     */
    public static Optional<LocalDateTime> timeFromDateOperand(TypeDescriptor columnType, Expression operand) {
        if (columnType != null
                && CoreTypeEncoding.TIME_FROM_DATE.equals(columnType.getTypeEncoding())
                && operand.getLocalTime() != null) {
            return Optional.of(LocalDateTime.of(EPOCH_DAY, operand.getLocalTime()));
        }
        return Optional.empty();
    }

    /** Column stores TIME as integer seconds from midnight. */
    public static boolean isSecondsFromMidnight(TypeDescriptor type) {
        return type != null
                && CoreTypeFamily.TIME.equals(type.getTypeFamily())
                && CoreTypeEncoding.TIME_SECONDS_FROM_MIDNIGHT.equals(type.getTypeEncoding());
    }

    /**
     * A TIME column whose encoding can be rendered as integer seconds-of-day for arithmetic:
     * seconds-from-midnight (already seconds), HHMMSS integer, or 'HH:MM:SS' text. A <em>native</em>
     * TIME column (no encoding) is NOT included — it keeps the dialect-native {@code TIME + INTERVAL}
     * path. Both the result-type ({@code ReturnTypes.ADD_SUB}) and the rendering
     * ({@link #secondsArithmetic}) key off this single predicate so they always agree.
     */
    public static boolean secondsConvertible(TypeDescriptor type) {
        if (type == null || !CoreTypeFamily.TIME.equals(type.getTypeFamily())) {
            return false;
        }
        var enc = type.getTypeEncoding();
        return CoreTypeEncoding.TIME_SECONDS_FROM_MIDNIGHT.equals(enc)
                || CoreTypeEncoding.TIME_FROM_INTEGER.equals(enc)
                || CoreTypeEncoding.TIME_FROM_STRING.equals(enc);
    }

    /** Operand that denotes an amount of time: a duration literal or a to_interval(...) call. */
    public static boolean isDurationOperand(Expression right) {
        return right.getDuration() != null
                || (right.getFunction() != null && "to_interval".equals(right.getFunction().getFunc()));
    }

    /**
     * Wraps a seconds-of-day arithmetic result back into [0, 86400) so that
     * crossing midnight in either direction stays a valid time-of-day
     * (23:30 + 2h → 01:30, 00:30 - 1h → 23:30). MOD keeps the dividend's sign
     * in every supported dialect, so the second MOD after adding 86400 folds a
     * negative (pre-midnight) result back into range. Requires a MOD(a, b)
     * function (Oracle, PostgreSQL, DuckDB, Snowflake).
     */
    public static String wrapSecondsOfDay(String secondsExpr) {
        return "MOD(MOD(" + secondsExpr + ", 86400) + 86400, 86400)";
    }

    /**
     * TIME ± duration on a seconds-from-midnight column: the operand becomes integer seconds
     * added to/subtracted from the raw column. The result is emitted as plain integer
     * arithmetic — wrapping into [0, 86400) ("wraps around midnight", docs/TEMPORAL.md) happens
     * at the read boundary ({@code JdbcDatabase#read} decodes the integer to a LocalTime with
     * floorMod), so the SQL needs no MOD and works on every dialect.
     *
     * @return the raw seconds expression, or empty if the encoding/operand shape does not apply
     */
    public static Optional<String> secondsArithmetic(SqlSelectRenderer renderer, String leftSql,
            TypeDescriptor leftType, String operator, Expression right, int indent) {
        if (!secondsConvertible(leftType) || !isDurationOperand(right)) {
            return Optional.empty();
        }
        // Decode the column to integer seconds-of-day (dialect-specific for HHMMSS / text), then do
        // plain integer arithmetic; the result is seconds-from-midnight, floor-mod-decoded on read.
        String leftSeconds = renderer.getDialect().timeColumnAsSeconds(leftSql, leftType);
        return Optional.of(leftSeconds + " " + operator + " " + durationSeconds(renderer, right, indent));
    }

    /**
     * Renders a duration / to_interval operand as a total-seconds expression,
     * for adding to or subtracting from a seconds-from-midnight TIME. Calendar
     * units (YEAR/MONTH/QUARTAL) and sub-second precision are rejected —
     * neither survives an integer seconds-of-day encoding.
     */
    public static String durationSeconds(SqlSelectRenderer renderer, Expression right, int indent) {
        Duration dur = right.getDuration();
        if (dur != null) {
            long total = 0;
            for (Duration.Component c : dur.getComponents()) total += componentSeconds(c);
            return Long.toString(total);
        }
        Function f = right.getFunction();   // guaranteed to_interval by isDurationOperand
        if (f.getArguments().size() == 2) {
            String value = renderer.toSql(f.getArguments().get(0), indent);
            String unit  = renderer.toSql(f.getArguments().get(1), indent);
            return intervalUnitSeconds(value, unit);
        }
        if (f.getArguments().size() == 6) {
            var args = f.getArguments();
            String[] v = new String[6];
            for (int i = 0; i < 6; i++) v[i] = renderer.toSql(args.get(i), indent);
            if (!"0".equals(v[0].trim()) || !"0".equals(v[1].trim()) || !"0".equals(v[2].trim())) {
                throw new UnsupportedOperationException(
                        "cannot add a calendar unit (year/month/day) to a TIME value — a time-of-day has no calendar context.");
            }
            List<String> terms = new ArrayList<>();
            if (!"0".equals(v[3].trim())) terms.add(v[3] + " * 3600");
            if (!"0".equals(v[4].trim())) terms.add(v[4] + " * 60");
            if (!"0".equals(v[5].trim())) terms.add(v[5]);
            // parenthesise the sum so a leading '-' operator negates the whole delta, not just term 0
            return terms.isEmpty() ? "0" : "(" + String.join(" + ", terms) + ")";
        }
        throw new IllegalArgumentException("to_interval requires 2 or 6 arguments");
    }

    private static long componentSeconds(Duration.Component c) {
        return switch (c.unit()) {
            case HOUR   -> c.value() * 3600L;
            case MINUTE -> c.value() * 60L;
            case SECOND -> c.value();
            case MILLISECOND -> throw new UnsupportedOperationException(
                    "millisecond precision is not representable in a seconds-from-midnight TIME.");
            // DAY and WEEK are calendar (variable-length) units — not 86_400s — so they are
            // rejected on a time-of-day too (FunctionValidator rejects this earlier with a position).
            case DAY, WEEK, YEAR, MONTH, QUARTAL -> throw new UnsupportedOperationException(
                    "cannot add " + c.unit() + " to a TIME value — only fixed clock units (h m s) apply to a time-of-day.");
        };
    }

    private static String intervalUnitSeconds(String value, String unit) {
        String bare = (unit.startsWith("'") && unit.endsWith("'"))
                ? unit.substring(1, unit.length() - 1).toUpperCase(Locale.ROOT)
                : null;
        if (bare == null) {
            throw new UnsupportedOperationException("to_interval unit must be a literal for TIME arithmetic");
        }
        return switch (bare) {
            case "HOUR",   "HOURS"   -> value + " * 3600";
            case "MINUTE", "MINUTES" -> value + " * 60";
            case "SECOND", "SECONDS" -> value;
            case "DAY", "DAYS", "WEEK", "WEEKS", "YEAR", "YEARS", "MONTH", "MONTHS", "QUARTAL", "QUARTALS" ->
                    throw new UnsupportedOperationException(
                            "cannot add " + bare + " to a TIME value — only fixed clock units (h m s) apply to a time-of-day.");
            case "MILLISECOND", "MILLISECONDS" -> throw new UnsupportedOperationException(
                    "millisecond precision is not representable in a seconds-from-midnight TIME.");
            default -> throw new IllegalArgumentException("Unknown interval unit: " + unit);
        };
    }
}
