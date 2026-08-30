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
package ai.koryki.catalog.types;

import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Lossless conversion lattice over the {@link TypeEncoding}s of a <em>single</em> {@link TypeFamily}.
 * Reconciling the branches of a conditional (CASE / COALESCE / IF) picks one common encoding that
 * every branch reaches without loss; this lattice answers which targets are reachable
 * ({@link #losslessTargets}), how expensive each is ({@link #cost}), and how to render the
 * conversion ({@link #convertSql}).
 *
 * <p>Two families are filled in.
 *
 * <p><b>TIMESTAMP epoch integers:</b> a coarser unit converts to any finer unit by an exact integer
 * scale (EPOCH:SECONDS &rarr; EPOCH:MILLIS is {@code * 1000}); the reverse is lossy and excluded.
 *
 * <p><b>TIME:</b> the three second-resolution encodings — {@code TIME_SECONDS_FROM_MIDNIGHT},
 * {@code TIME_FROM_INTEGER} (HHMMSS) and {@code TIME_FROM_STRING} ('HH:MM:SS') — all convert
 * losslessly to seconds-of-day, so that is their common target. The set matches
 * {@code TimeEncodings.secondsConvertible}, which the arithmetic path already keys off: seconds is
 * where TIME arithmetic happens, so comparison and arithmetic meet in the same domain. A
 * <em>native</em> TIME column is deliberately excluded (it keeps the dialect-native path), as are
 * {@code TIME_FROM_DATE} and {@code TIME_FROM_TIMESTAMP}, which carry a date part.
 *
 * <p>Every other encoding is a lattice singleton — only itself — so unlike encodings in those
 * families have no common target and reconciliation hard-errors until their conversions are added
 * here. That is the extension point: adding a pair makes both conditional branches and comparisons
 * work for it, on every dialect at once.
 */
public final class EncodingLattice {

    /** The epoch units koryki decodes (see {@code CoreDecoder}); all powers of ten apart. */
    private static final ChronoUnit[] EPOCH_UNITS =
            { ChronoUnit.SECONDS, ChronoUnit.MILLIS, ChronoUnit.MICROS, ChronoUnit.NANOS };

    /**
     * Largest {@code SCALED:n} considered as a conversion target. A bound is needed because the
     * scale is an open integer rather than an enumerated unit; 18 is the digits a signed 64-bit
     * minor-unit column can hold, so nothing beyond it is representable anyway.
     */
    private static final int MAX_SCALE = 18;

    private EncodingLattice() {}

    /** TIME encodings that hold a whole-second time of day and so meet on seconds-from-midnight. */
    private static boolean isSecondOfDay(TypeEncoding enc) {
        return CoreTypeEncoding.TIME_SECONDS_FROM_MIDNIGHT.equals(enc)
                || CoreTypeEncoding.TIME_FROM_INTEGER.equals(enc)
                || CoreTypeEncoding.TIME_FROM_STRING.equals(enc);
    }

    /** Interval units worth scaling between, grouped by {@link IntervalUnitClass}. */
    private static final ChronoUnit[] INTERVAL_UNITS = {
            ChronoUnit.NANOS, ChronoUnit.MICROS, ChronoUnit.MILLIS, ChronoUnit.SECONDS,
            ChronoUnit.MINUTES, ChronoUnit.HOURS,
            ChronoUnit.DAYS, ChronoUnit.WEEKS,
            ChronoUnit.MONTHS, ChronoUnit.YEARS };

    /**
     * The encodings {@code enc} converts to without loss, including {@code enc} itself.
     *
     * <p>An encoding not listed here is an island, reachable only from itself — which is what
     * decides whether a comparison of two differently-encoded columns can be reconciled (see
     * {@code SqlDialect.renderComparison}) or has to be reported instead. Adding a pair is the
     * extension point; it serves conditional branches and comparisons alike.
     */
    public static Set<TypeEncoding> losslessTargets(TypeEncoding enc) {
        Set<TypeEncoding> targets = new LinkedHashSet<>();
        targets.add(enc);
        if (enc instanceof EpochTypeEncoding epoch) {
            for (ChronoUnit u : EPOCH_UNITS) {
                if (isFinerOrEqual(epoch.getUnit(), u)) {
                    targets.add(new EpochTypeEncoding(u));
                }
            }
        }
        if (enc instanceof IntervalTypeEncoding interval) {
            // Only within one unit class. DAYS -> HOURS is an exact x24 by ChronoUnit duration and
            // would pass isFinerOrEqual, but a calendar day is not always 24 hours — the very
            // conversion FunctionValidator.checkIntervalComparison refuses to order across.
            IntervalUnitClass cls = IntervalUnitClass.of(interval.getUnit());
            for (ChronoUnit u : INTERVAL_UNITS) {
                if (IntervalUnitClass.of(u) == cls && isFinerOrEqual(interval.getUnit(), u)) {
                    targets.add(new IntervalTypeEncoding(u));
                }
            }
        }
        if (enc instanceof ScaledTypeEncoding scaled) {
            // A larger scale is finer: SCALED:2 (cents) -> SCALED:4 is an exact x100.
            for (int s = scaled.getScale(); s <= MAX_SCALE; s++) {
                targets.add(new ScaledTypeEncoding(s));
            }
        }
        if (isSecondOfDay(enc)) {
            targets.add(CoreTypeEncoding.TIME_SECONDS_FROM_MIDNIGHT);
        }
        return targets;
    }

    /** Conversion cost {@code from -> to}; {@code to} must be a {@link #losslessTargets} of {@code from}. */
    public static int cost(TypeEncoding from, TypeEncoding to) {
        if (from.equals(to)) return 0;
        if (from instanceof EpochTypeEncoding && to instanceof EpochTypeEncoding) {
            return 1;   // a single scalar multiply
        }
        if (from instanceof IntervalTypeEncoding && to instanceof IntervalTypeEncoding) {
            return 1;   // likewise
        }
        if (from instanceof ScaledTypeEncoding && to instanceof ScaledTypeEncoding) {
            return 1;   // likewise
        }
        if (CoreTypeEncoding.TIME_FROM_INTEGER.equals(from) && isSecondOfDay(to)) {
            return 2;   // integer div/mod arithmetic
        }
        if (CoreTypeEncoding.TIME_FROM_STRING.equals(from) && isSecondOfDay(to)) {
            return 3;   // parse the text, then extract
        }
        throw new IllegalArgumentException("no lossless conversion " + from + " -> " + to);
    }

    /**
     * Dialect-neutral SQL converting {@code expr} from {@code from} to {@code to}; {@code expr} if
     * equal. The TIME conversions are also what {@code SqlDialect.timeColumnAsSeconds} renders for
     * TIME arithmetic — one definition, so a comparison and an arithmetic expression can never
     * disagree about what an encoded TIME column means.
     */
    public static String convertSql(String expr, TypeEncoding from, TypeEncoding to) {
        if (from.equals(to)) return expr;
        if (from instanceof EpochTypeEncoding ef && to instanceof EpochTypeEncoding et) {
            long factor = ratio(ef.getUnit(), et.getUnit());   // to is finer => factor >= 1
            return factor == 1 ? expr : "(" + expr + ") * " + factor;
        }
        if (from instanceof IntervalTypeEncoding fi && to instanceof IntervalTypeEncoding ti) {
            if (IntervalUnitClass.of(fi.getUnit()) != IntervalUnitClass.of(ti.getUnit())) {
                throw new IllegalArgumentException("no anchor-independent conversion across interval "
                        + "unit classes: " + from + " -> " + to);
            }
            long factor = ratio(fi.getUnit(), ti.getUnit());
            return factor == 1 ? expr : "(" + expr + ") * " + factor;
        }
        if (from instanceof ScaledTypeEncoding fs && to instanceof ScaledTypeEncoding ts) {
            if (ts.getScale() < fs.getScale()) {
                throw new IllegalArgumentException("lossy scale reduction " + from + " -> " + to);
            }
            long factor = pow10(ts.getScale() - fs.getScale());
            return factor == 1 ? expr : "(" + expr + ") * " + factor;
        }
        if (isSecondOfDay(to)) {
            if (CoreTypeEncoding.TIME_FROM_INTEGER.equals(from)) {
                // HHMMSS -> seconds: hh*3600 + mm*60 + ss. FLOOR before CAST: a bare
                // CAST(<fraction> AS INTEGER) rounds rather than truncates in some dialects
                // (DuckDB: 23.5959 -> 24).
                return "CAST(FLOOR(" + expr + " / 10000.0) AS INTEGER) * 3600"
                     + " + MOD(CAST(FLOOR(" + expr + " / 100.0) AS INTEGER), 100) * 60"
                     + " + MOD(" + expr + ", 100)";
            }
            if (CoreTypeEncoding.TIME_FROM_STRING.equals(from)) {
                return "CAST(EXTRACT(EPOCH FROM CAST(" + expr + " AS TIME)) AS INTEGER)";
            }
        }
        throw new IllegalArgumentException("no lossless conversion " + from + " -> " + to);
    }

    /** {@code to} is the same or a finer unit than {@code from} (so from-&gt;to is an exact integer scale). */
    private static boolean isFinerOrEqual(ChronoUnit from, ChronoUnit to) {
        long f = from.getDuration().toNanos();
        long t = to.getDuration().toNanos();
        return t <= f && f % t == 0;
    }

    /** from.duration / to.duration (exact when {@code to} is finer-or-equal). */
    private static long ratio(ChronoUnit from, ChronoUnit to) {
        return from.getDuration().toNanos() / to.getDuration().toNanos();
    }

    /** {@code 10^n} for a scale difference. */
    private static long pow10(int n) {
        long f = 1;
        for (int i = 0; i < n; i++) {
            f *= 10;
        }
        return f;
    }
}
