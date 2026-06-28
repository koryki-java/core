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
import ai.koryki.catalog.schema.types.IntervalTypeEncoding;
import ai.koryki.catalog.schema.types.TypeDescriptor;
import ai.koryki.catalog.schema.types.TypeEncoding;
import ai.koryki.iql.query.Duration;
import ai.koryki.iql.query.Expression;
import ai.koryki.jdbc.Interval;

import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Rendering support for column-level INTERVAL encodings — the comparison/arithmetic
 * dual of the read-side {@link ai.koryki.jdbc.CoreDecoder}. When a duration literal
 * is compared with an INTERVAL column, it must be rendered in the column's own
 * storage encoding so both operands share one physical representation:
 * <ul>
 *   <li>{@code INTERVAL:<unit>} (numeric count) → an integer count of the unit
 *       ({@code 1h2min3s} against a SECONDS column → {@code 3723});</li>
 *   <li>{@code INTERVAL_CHAR} / {@code INTERVAL_FROM_STRING} (text) → an ISO-8601
 *       string literal ({@code 'PT1H2M3S'});</li>
 *   <li>native INTERVAL (no encoding) → empty: the caller renders the duration as a
 *       native interval value.</li>
 * </ul>
 * Like {@link TimeEncodings}, the encoding is a property of the column, not the
 * dialect; dialects opt in by consulting this helper in their comparison hook.
 */
public final class IntervalEncodings {

    private IntervalEncodings() {
    }

    /**
     * Renders {@code operand} (a duration literal) in {@code columnType}'s INTERVAL
     * storage encoding, or empty when it does not apply (not an INTERVAL column, not a
     * duration literal, or a native interval the caller renders itself).
     */
    public static Optional<String> durationOperand(TypeDescriptor columnType, Expression operand) {
        if (columnType == null
                || operand.getDuration() == null
                || !CoreTypeFamily.INTERVAL.equals(columnType.getTypeFamily())) {
            return Optional.empty();
        }
        Interval iv = toInterval(operand.getDuration());
        TypeEncoding enc = columnType.getTypeEncoding();
        if (enc instanceof IntervalTypeEncoding count) {
            return Optional.of(Long.toString(inUnit(iv, count.getUnit())));
        }
        if (CoreTypeEncoding.INTERVAL_FROM_STRING.equals(enc)) {
            return Optional.of("'" + iv + "'");   // ISO-8601, e.g. 'PT1H2M3S'
        }
        return Optional.empty();   // native INTERVAL — caller renders it natively
    }

    /** The koryki duration AST → a materialized {@link Interval} (months, days, nanos). */
    public static Interval toInterval(Duration dur) {
        int months = 0;
        int days = 0;
        long nanos = 0L;
        for (Duration.Component c : dur.getComponents()) {
            switch (c.unit()) {
                case YEAR        -> months += c.value() * 12;
                case QUARTAL     -> months += c.value() * 3;
                case MONTH       -> months += c.value();
                case WEEK        -> days   += c.value() * 7;
                case DAY         -> days   += c.value();
                case HOUR        -> nanos  += c.value() * 3_600_000_000_000L;
                case MINUTE      -> nanos  += c.value() *     60_000_000_000L;
                case SECOND      -> nanos  += c.value() *      1_000_000_000L;
                case MILLISECOND -> nanos  += c.value() *          1_000_000L;
            }
        }
        return Interval.of(months, days, nanos);
    }

    /**
     * A whole count of {@code unit} for an interval expressible in that unit (else throws).
     * The three fields never cross: month-family units read {@code months}, day-family
     * units read {@code days}, fixed clock units read {@code nanos}. A day is a variable
     * (calendar) unit — it is <em>not</em> 86_400s — so it never reduces to or from a
     * clock unit (docs/TEMPORAL.md, "Daylight saving time").
     */
    static long inUnit(Interval iv, ChronoUnit unit) {
        if (unit == ChronoUnit.YEARS || unit == ChronoUnit.MONTHS) {
            if (iv.getDays() != 0 || iv.getNanos() != 0) {
                throw new UnsupportedOperationException(
                        "an INTERVAL:" + unit + " column cannot hold a day or time component");
            }
            return unit == ChronoUnit.YEARS ? iv.getMonths() / 12 : iv.getMonths();
        }
        if (unit == ChronoUnit.WEEKS || unit == ChronoUnit.DAYS) {
            if (iv.getMonths() != 0 || iv.getNanos() != 0) {
                throw new UnsupportedOperationException(
                        "an INTERVAL:" + unit + " column cannot hold a month or time component");
            }
            if (unit == ChronoUnit.WEEKS) {
                if (iv.getDays() % 7 != 0) {
                    throw new UnsupportedOperationException("duration is not a whole number of weeks");
                }
                return iv.getDays() / 7;
            }
            return iv.getDays();
        }
        // fixed clock units: only the nanos field reduces — day/month are variable-length
        if (iv.getMonths() != 0 || iv.getDays() != 0) {
            throw new UnsupportedOperationException(
                    "an INTERVAL:" + unit + " column cannot hold a calendar (day/month/year) component");
        }
        long per = nanosPer(unit);
        if (iv.getNanos() % per != 0) {
            throw new UnsupportedOperationException(
                    "duration is not a whole multiple of the column unit INTERVAL:" + unit);
        }
        return iv.getNanos() / per;
    }

    private static long nanosPer(ChronoUnit unit) {
        return switch (unit) {
            case HOURS   -> 3_600_000_000_000L;
            case MINUTES -> 60_000_000_000L;
            case SECONDS -> 1_000_000_000L;
            case MILLIS  -> 1_000_000L;
            case MICROS  -> 1_000L;
            case NANOS   -> 1L;
            default      -> throw new UnsupportedOperationException("unsupported clock unit " + unit);
        };
    }
}
