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
package ai.koryki.iql.functions;

import ai.koryki.catalog.schema.types.*;
import ai.koryki.iql.query.Duration;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.types.TimeEncodings;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BinaryOperator;

public class ReturnTypes {

    public static final ReturnTypeInference DATE      = b -> TypeDescriptor.DATE;
    public static final ReturnTypeInference TIME      = b -> TypeDescriptor.TIME;
    public static final ReturnTypeInference TIMESTAMP = b -> TypeDescriptor.TIMESTAMP;
    public static final ReturnTypeInference TEXT      = b -> TypeDescriptor.TEXT;
    public static final ReturnTypeInference BOOLEAN   = b -> TypeDescriptor.BOOLEAN;
    public static final ReturnTypeInference INTEGER   = b -> TypeDescriptor.INTEGER;
    public static final ReturnTypeInference BIGINT    = b -> TypeDescriptor.BIGINT;
    public static final ReturnTypeInference SMALLINT  = b -> TypeDescriptor.SMALLINT;
    public static final ReturnTypeInference FLOAT     = b -> TypeDescriptor.FLOAT;
    public static final ReturnTypeInference DOUBLE    = b -> TypeDescriptor.DOUBLE;
    public static final ReturnTypeInference DECIMAL   = b -> TypeDescriptor.DECIMAL;
    public static final ReturnTypeInference ARG0      = b -> b.getOperandType(0);
    public static final ReturnTypeInference ARG1      = b -> b.getOperandType(1);
    public static final ReturnTypeInference ARG2      = b -> b.getOperandType(2);
    public static final ReturnTypeInference INTERVAL  = b -> TypeDescriptor.INTERVAL;

    /** calendar_distance: an INTERVAL whose "startEpoch;endEpoch" wire form is decoded to a calendar span. */
    public static final ReturnTypeInference CALENDAR_DISTANCE = b ->
            new TypeDescriptor(TypeNames.TYPE_INTERVAL, CoreTypeEncoding.CALENDAR_DISTANCE, CoreTypeFamily.INTERVAL);

    /** TIME as integer seconds-from-midnight — the canonical domain a TIME±duration result lands in. */
    private static final TypeDescriptor TIME_SECONDS =
            new TypeDescriptor(TypeNames.TYPE_TIME, CoreTypeEncoding.TIME_SECONDS_FROM_MIDNIGHT, CoreTypeFamily.TIME);

    /** TIMESTAMP − TIMESTAMP lands here: an exact elapsed span counted in seconds (no calendar fields). */
    private static final TypeDescriptor INTERVAL_SECONDS =
            new TypeDescriptor(TypeNames.TYPE_INTERVAL, new IntervalTypeEncoding(ChronoUnit.SECONDS), CoreTypeFamily.INTERVAL);

    /** Conditional result: reconcile every operand to one common type (coalesce). See {@link ConditionalReconciler}. */
    public static final ReturnTypeInference RECONCILE = binding -> {
        List<TypeDescriptor> types = new ArrayList<>();
        for (int i = 0; i < binding.getOperandCount(); i++) {
            types.add(binding.getOperandType(i));
        }
        return ConditionalReconciler.reconcile(types).target();
    };

    public static final ReturnTypeInference LEAST_RESTRICTIVE = binding -> {
        TypeDescriptor result = binding.getOperandType(0);
        for (int i = 1; i < binding.getOperandCount(); i++) {
            TypeDescriptor next = binding.getOperandType(i);
            if (numericRank(next) > numericRank(result)) {
                result = next;
            }
        }
        return result;
    };

    private static int numericRank(TypeDescriptor t) {
        if (t == null) return -1;
        TypeFamily f = t.getTypeFamily();
        if (CoreTypeFamily.INTEGER.equals(f)) return 1;
        if (CoreTypeFamily.DECIMAL.equals(f)) return 2;
        if (CoreTypeFamily.FLOAT.equals(f))   return 3;
        return 0;
    }

    // --- DECIMAL precision/scale propagation (T-SQL-style formulas; clamped per dialect in renderType) ---
    // Each folds left across operands and falls back to LEAST_RESTRICTIVE unless every operand is a DECIMAL
    // carrying precision and scale, so integer/float arithmetic is unaffected.

    /** DECIMAL(p1,s1) * DECIMAL(p2,s2) -> DECIMAL(p1+p2+1, s1+s2). */
    public static final ReturnTypeInference DECIMAL_MULTIPLY = decimalFold((a, b) ->
            new int[]{a[0] + b[0] + 1, a[1] + b[1]});

    /**
     * DURATION × integer (either order) → DURATION (the algebra's component-wise scale); any other
     * operand mix falls back to the numeric {@link #DECIMAL_MULTIPLY}.
     */
    public static final ReturnTypeInference MULTIPLY = binding -> {
        if (binding.getOperandCount() >= 2) {
            TypeFamily a = family(binding.getOperandType(0));
            TypeFamily b = family(binding.getOperandType(1));
            boolean aIv = CoreTypeFamily.INTERVAL.equals(a), bIv = CoreTypeFamily.INTERVAL.equals(b);
            boolean aInt = CoreTypeFamily.INTEGER.equals(a), bInt = CoreTypeFamily.INTEGER.equals(b);
            if ((aIv && bInt) || (aInt && bIv)) {
                return TypeDescriptor.INTERVAL;
            }
        }
        return DECIMAL_MULTIPLY.infer(binding);
    };

    /** DECIMAL(p1,s1) +/- DECIMAL(p2,s2) -> DECIMAL(max(p1-s1, p2-s2) + max(s1,s2) + 1, max(s1,s2)). */
    public static final ReturnTypeInference DECIMAL_ADD = decimalFold((a, b) -> {
        int scale = Math.max(a[1], b[1]);
        int precision = Math.max(a[0] - a[1], b[0] - b[1]) + scale + 1;
        return new int[]{precision, scale};
    });

    /** DECIMAL division (approximate; guarantees >= 6 fractional digits). Clamped per dialect in renderType. */
    public static final ReturnTypeInference DECIMAL_DIVIDE = decimalFold((a, b) -> {
        int scale = Math.max(Math.max(a[1], b[1]), 6);
        int precision = (a[0] - a[1]) + b[1] + scale;
        return new int[]{precision, scale};
    });

    /** to_decimal(value, precision, scale): read the literal precision/scale args -> DECIMAL(p,s). */
    public static final ReturnTypeInference DECIMAL_CAST = binding -> {
        var args = binding.getFunction().getArguments();
        if (args.size() >= 3 && args.get(1).getNumber() != null && args.get(2).getNumber() != null) {
            return new TypeDescriptor(TypeNames.TYPE_DECIMAL, null, CoreTypeFamily.DECIMAL,
                    args.get(1).getNumber().intValue(), args.get(2).getNumber().intValue());
        }
        return TypeDescriptor.DECIMAL;
    };

    /** Folds a 2-operand DECIMAL (precision,scale) combiner left across all operands. */
    private static ReturnTypeInference decimalFold(BinaryOperator<int[]> combine) {
        return binding -> {
            TypeDescriptor acc = binding.getOperandType(0);
            if (!isDecimalWithPrecisionScale(acc)) {
                return LEAST_RESTRICTIVE.infer(binding);
            }
            int[] ps = {acc.getPrecision(), acc.getScale()};
            for (int i = 1; i < binding.getOperandCount(); i++) {
                TypeDescriptor next = binding.getOperandType(i);
                if (!isDecimalWithPrecisionScale(next)) {
                    return LEAST_RESTRICTIVE.infer(binding);
                }
                ps = combine.apply(ps, new int[]{next.getPrecision(), next.getScale()});
            }
            return new TypeDescriptor(TypeNames.TYPE_DECIMAL, null, CoreTypeFamily.DECIMAL, ps[0], ps[1]);
        };
    }

    private static boolean isDecimalWithPrecisionScale(TypeDescriptor t) {
        return t != null
                && CoreTypeFamily.DECIMAL.equals(t.getTypeFamily())
                && t.getPrecision() >= 0
                && t.getScale() >= 0;
    }

    /**
     * Temporal {@code +}/{@code -} per docs/TEMPORAL.md "Result-type algebra"; falls back to
     * {@link #DECIMAL_ADD} for numeric operands. The result <em>strips the storage encoding</em> —
     * a computed DATE is a plain DATE, not the epoch-day integer (etc.) the source column was —
     * and a DATE plus a duration that has any clock component becomes a TIMESTAMP.
     */
    public static final ReturnTypeInference ADD_SUB = binding -> {
        if (binding.getOperandCount() >= 2) {
            TypeFamily lf = family(binding.getOperandType(0));
            TypeFamily rf = family(binding.getOperandType(1));
            boolean minus = "minus".equals(binding.getFunction().getFunc());
            boolean rightInterval = CoreTypeFamily.INTERVAL.equals(rf);

            if (rightInterval && CoreTypeFamily.TIMESTAMP.equals(lf)) return TypeDescriptor.TIMESTAMP;
            // TIME ± duration is computed in the seconds-of-day domain, so a convertible encoding
            // (seconds-from-midnight, HHMMSS integer, 'HH:MM:SS' text) yields a seconds-from-midnight
            // result (floor-mod-decoded to a LocalTime). A native TIME column keeps its own type
            // (rendered as the dialect-native TIME + INTERVAL).
            if (rightInterval && CoreTypeFamily.TIME.equals(lf)) {
                return TimeEncodings.secondsConvertible(binding.getOperandType(0))
                        ? TIME_SECONDS : binding.getOperandType(0);
            }
            if (rightInterval && CoreTypeFamily.DATE.equals(lf)) {
                return durationHasClock(binding.getFunction().getArguments().get(1))
                        ? TypeDescriptor.TIMESTAMP : TypeDescriptor.DATE;
            }
            if (!minus && CoreTypeFamily.DATE.equals(lf) && CoreTypeFamily.TIME.equals(rf)) return TypeDescriptor.TIMESTAMP;
            if (minus && isTemporal(lf) && lf.equals(rf)) {
                // TIMESTAMP − TIMESTAMP is an exact elapsed span → fixed seconds (rendered HH:MM:SS,
                // never calendar days, so >24h shows 50:00:00). DATE−DATE / TIME−TIME stay a plain
                // INTERVAL (their natural day / clock amount).
                return CoreTypeFamily.TIMESTAMP.equals(lf) ? INTERVAL_SECONDS : TypeDescriptor.INTERVAL;
            }
            if (CoreTypeFamily.INTERVAL.equals(lf) && rightInterval) return TypeDescriptor.INTERVAL;
        }
        return DECIMAL_ADD.infer(binding);
    };

    private static TypeFamily family(TypeDescriptor t) {
        return t != null ? t.getTypeFamily() : null;
    }

    private static boolean isTemporal(TypeFamily f) {
        return CoreTypeFamily.DATE.equals(f) || CoreTypeFamily.TIME.equals(f) || CoreTypeFamily.TIMESTAMP.equals(f);
    }

    /** A DATE plus a duration carrying any clock unit (h/m/s/ms) yields a TIMESTAMP, not a DATE. */
    private static boolean durationHasClock(Expression operand) {
        Duration d = operand.getDuration();
        if (d == null) {
            return true;   // a non-literal interval (column/expr) — assume a clock part (TIMESTAMP)
        }
        return d.getComponents().stream().anyMatch(c ->
                c.unit() == Duration.Unit.HOUR || c.unit() == Duration.Unit.MINUTE
                        || c.unit() == Duration.Unit.SECOND || c.unit() == Duration.Unit.MILLISECOND);
    }

    public static final ReturnTypeInference TEXT_FROM_BOOLEAN = b ->
            new TypeDescriptor(TypeNames.TYPE_TEXT, CoreTypeEncoding.TEXT_FROM_BOOLEAN, CoreTypeFamily.TEXT);

}
