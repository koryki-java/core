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
package ai.koryki.jdbc;

import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalUnit;
import java.util.List;

/**
 * Materialized INTERVAL value — the read-side counterpart of the query-AST
 * {@code ai.koryki.iql.query.Duration} (which is never a value; it renders to
 * dialect SQL). The boundary decoder ({@link CoreDecoder} and the dialect
 * decoders) produces this from a native interval object or an encoded column.
 *
 * <p>Holds the three independent fields every supported dialect stores —
 * <b>months, days, nanoseconds</b> — kept separate (not normalized) because
 * months are variable-length and days vary under DST. This losslessly covers
 * Postgres/DuckDB (months+days+µs), Oracle YEAR&nbsp;TO&nbsp;MONTH (months) and
 * Oracle DAY&nbsp;TO&nbsp;SECOND (days+nanos), and the mixed KQL duration
 * {@code 1y2mo3d4h}. Maps to {@code java.time.Period} when it is months/days
 * only, to {@code Duration} when nanos only, and stays a true mixed amount
 * otherwise. Implements {@link TemporalAmount} so it can be added to a
 * {@code Temporal}; {@code LocaleFormat} renders it for display.
 */
public final class Interval implements TemporalAmount {

    public static final Interval ZERO = new Interval(0, 0, 0L);

    private static final List<TemporalUnit> UNITS =
            List.of(ChronoUnit.MONTHS, ChronoUnit.DAYS, ChronoUnit.NANOS);

    private final int months;
    private final int days;
    private final long nanos;

    private Interval(int months, int days, long nanos) {
        this.months = months;
        this.days = days;
        this.nanos = nanos;
    }

    public static Interval of(int months, int days, long nanos) {
        if (months == 0 && days == 0 && nanos == 0L) {
            return ZERO;
        }
        return new Interval(months, days, nanos);
    }

    public static Interval ofMonths(int months) { return of(months, 0, 0L); }
    public static Interval ofDays(int days)     { return of(0, days, 0L); }
    public static Interval ofNanos(long nanos)  { return of(0, 0, nanos); }

    public int getMonths() { return months; }
    public int getDays()   { return days; }
    public long getNanos() { return nanos; }

    public boolean isZero() { return months == 0 && days == 0 && nanos == 0L; }

    public Interval negated() { return of(-months, -days, -nanos); }

    // --- TemporalAmount -----------------------------------------------------

    @Override
    public long get(TemporalUnit unit) {
        if (unit == ChronoUnit.MONTHS) return months;
        if (unit == ChronoUnit.DAYS)   return days;
        if (unit == ChronoUnit.NANOS)  return nanos;
        throw new java.time.temporal.UnsupportedTemporalTypeException("Unsupported unit: " + unit);
    }

    @Override
    public List<TemporalUnit> getUnits() {
        return UNITS;
    }

    @Override
    public Temporal addTo(Temporal temporal) {
        if (months != 0) temporal = temporal.plus(months, ChronoUnit.MONTHS);
        if (days != 0)   temporal = temporal.plus(days, ChronoUnit.DAYS);
        if (nanos != 0)  temporal = temporal.plus(nanos, ChronoUnit.NANOS);
        return temporal;
    }

    @Override
    public Temporal subtractFrom(Temporal temporal) {
        if (months != 0) temporal = temporal.minus(months, ChronoUnit.MONTHS);
        if (days != 0)   temporal = temporal.minus(days, ChronoUnit.DAYS);
        if (nanos != 0)  temporal = temporal.minus(nanos, ChronoUnit.NANOS);
        return temporal;
    }

    // --- value semantics ----------------------------------------------------

    @Override
    public boolean equals(Object o) {
        return o instanceof Interval other
                && months == other.months && days == other.days && nanos == other.nanos;
    }

    @Override
    public int hashCode() {
        return (months * 31 + days) * 31 + Long.hashCode(nanos);
    }

    /** ISO-8601: {@code P[nY][nM][nD][T[nH][nM][n[.f]S]]} (e.g. {@code P1Y2M3DT4H}). */
    @Override
    public String toString() {
        if (isZero()) {
            return "PT0S";
        }
        StringBuilder b = new StringBuilder("P");
        int years = months / 12;
        int mo = months % 12;
        if (years != 0) b.append(years).append('Y');
        if (mo != 0)    b.append(mo).append('M');
        if (days != 0)  b.append(days).append('D');
        if (nanos != 0) {
            b.append('T');
            long n = nanos;
            long hours = n / 3_600_000_000_000L; n %= 3_600_000_000_000L;
            long mins  = n / 60_000_000_000L;    n %= 60_000_000_000L;
            long secs  = n / 1_000_000_000L;
            long frac  = n % 1_000_000_000L;
            if (hours != 0) b.append(hours).append('H');
            if (mins != 0)  b.append(mins).append('M');
            if (secs != 0 || frac != 0) {
                b.append(secs);
                if (frac != 0) {
                    String f = String.format("%09d", Math.abs(frac));
                    int end = f.length();
                    while (end > 0 && f.charAt(end - 1) == '0') end--;
                    b.append('.').append(f, 0, end);
                }
                b.append('S');
            }
        }
        return b.toString();
    }

    /**
     * koryki duration notation: {@code [nY][nMO][nD][nH][nMIN][n[.f]S]} (e.g. {@code 1y2mo3d4h}).
     * The presentation form (see {@code LocaleFormat}): round-trips with the KQL duration literal and
     * avoids ISO-8601's {@code M} month/minute ambiguity. The smallest emitted unit is the (possibly
     * fractional) second, so a sub-second amount shows as {@code 0.5s}, not {@code 500ms}. Distinct
     * from {@link #toString()}, which stays ISO-8601 for interchange/debug.
     */
    public String toKql() {
        if (isZero()) {
            return "0s";
        }
        StringBuilder b = new StringBuilder();
        int years = months / 12;
        int mo = months % 12;
        if (years != 0) b.append(years).append('y');
        if (mo != 0)    b.append(mo).append("mo");
        if (days != 0)  b.append(days).append('d');
        if (nanos != 0) {
            long n = nanos;
            long hours = n / 3_600_000_000_000L; n %= 3_600_000_000_000L;
            long mins  = n / 60_000_000_000L;    n %= 60_000_000_000L;
            long secs  = n / 1_000_000_000L;
            long frac  = n % 1_000_000_000L;
            if (hours != 0) b.append(hours).append('h');
            if (mins != 0)  b.append(mins).append("min");
            if (secs != 0 || frac != 0) {
                b.append(secs);
                if (frac != 0) {
                    String f = String.format("%09d", Math.abs(frac));
                    int end = f.length();
                    while (end > 0 && f.charAt(end - 1) == '0') end--;
                    b.append('.').append(f, 0, end);
                }
                b.append('s');
            }
        }
        return b.toString();
    }
}
