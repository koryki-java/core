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

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.types.*;
import java.sql.SQLException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CoreDecoder implements TypeDecoder {

    /** The model zone instants and epoch values are presented in (docs/TEMPORAL.md). */
    private final ZoneId zone;

    public CoreDecoder(ZoneId zone) {
        this.zone = zone;
    }

    @Override
    public Object decode(Object v, ColumnInfo info) {
        TypeDescriptor td = info != null ? info.getTypeDescriptor() : null;
        TypeEncoding enc = td != null ? td.getTypeEncoding() : null;

        // A container: ARRAY, STRUCT and MAP come off the driver as java.sql.Array, java.sql.Struct
        // or a Map, each holding driver values. Read into plain java.util values, and decode every
        // leaf by the type it has in the tree, so a nested DECIMAL, DATE or INSTANT is what the
        // same
        // column would be at the top level. The value that comes back is a List, or a Map keyed by
        // field name for a STRUCT, whatever the driver.
        if (td != null && td.isComplex() && v != null) {
            return decodeComplex(v, td);
        }

        // INTERVAL family: materialize the portable forms (already-decoded Interval, a
        // java.time amount, a numeric count, or an ISO/clock string) into a koryki
        // Interval. Vendor-native interval objects/strings (PGInterval, oracle.sql.*,
        // DuckDB's verbose string) are converted by the per-dialect decoder before super.
        if (td != null && CoreTypeFamily.INTERVAL.equals(td.getTypeFamily())) {
            if (v instanceof Interval) return v;
            if (v instanceof Period per)
                return Interval.of(per.getYears() * 12 + per.getMonths(), per.getDays(), 0L);
            if (v instanceof Duration d) return Interval.of(0, 0, d.toNanos());
            if (enc instanceof IntervalTypeEncoding it && v instanceof Number n)
                return intervalAmount(it.getUnit(), n.longValue());
            if ((CoreTypeEncoding.INTERVAL_FROM_STRING.equals(enc)
                            || enc instanceof IntervalStringEncoding)
                    && v instanceof String s) return parseIntervalString(s);
            if (CoreTypeEncoding.CALENDAR_DISTANCE.equals(enc) && v instanceof String s)
                return calendarDistance(s);
            return v;
        }

        // DATE family: a calendar day, no time. Some drivers (notably Oracle, whose DATE
        // carries a time-of-day) return a date-time; truncate it so the read boundary yields a
        // canonical java.time.LocalDate uniformly. A numeric (DATE_FROM_EPOCH_DAY) value is not
        // a date-time, so it falls through to its encoding branch below.
        if (td != null && CoreTypeFamily.DATE.equals(td.getTypeFamily())) {
            if (v instanceof LocalDate) return v;
            if (v instanceof java.sql.Date d) return d.toLocalDate();
            if (v instanceof java.sql.Timestamp t) return t.toLocalDateTime().toLocalDate();
            if (v instanceof LocalDateTime dt) return dt.toLocalDate();
        }

        // BOOLEAN family: an engine with no boolean type answers a predicate with 0/1, so a
        // function
        // the catalog declares BOOLEAN can arrive as a Number — `starts_with` is native on
        // PostgreSQL but emulated as `(LEFT(s, n) = p)` on MariaDB and `(substr(...) = p)` on
        // SQLite, and both hand back an integer. The declared family is the authority, so it reads
        // back as a real Boolean everywhere rather than "0.0" on the engines that emulate it.
        if (td != null && CoreTypeFamily.BOOLEAN.equals(td.getTypeFamily())) {
            if (v instanceof Boolean) return v;
            if (v instanceof Number n) return n.longValue() != 0;
        }

        if (enc == null) {
            return v;
        }
        // INSTANT: readInstant() already produced the absolute point in time; present it as a
        // wall-clock LocalDateTime in the model zone (the single source of truth for display).
        if (CoreTypeEncoding.INSTANT.equals(enc)) {
            if (v instanceof Instant inst) return LocalDateTime.ofInstant(inst, zone);
            return v;
        }
        if (CoreTypeEncoding.TIME_SECONDS_FROM_MIDNIGHT.equals(enc) && v instanceof Number n) {
            return LocalTime.ofSecondOfDay(Math.floorMod(n.longValue(), 86400L));
        }
        if (CoreTypeEncoding.TIME_FROM_INTEGER.equals(enc) && v instanceof Number n) {
            long hhmmss = n.longValue();
            return LocalTime.of(
                    (int) (hhmmss / 10000), (int) ((hhmmss / 100) % 100), (int) (hhmmss % 100));
        }
        if (CoreTypeEncoding.TIME_FROM_STRING.equals(enc) && v instanceof String s) {
            return LocalTime.parse(s);
        }
        if (CoreTypeEncoding.TIME_FROM_TIMESTAMP.equals(enc)
                || CoreTypeEncoding.TIME_FROM_DATE.equals(enc)) {
            if (v instanceof LocalDateTime dt) return dt.toLocalTime();
            if (v instanceof LocalDate) return LocalTime.MIDNIGHT; // a pure date has no time-of-day
        }
        if (CoreTypeEncoding.DATE_FROM_EPOCH_DAY.equals(enc) && v instanceof Number n) {
            return LocalDate.ofEpochDay(n.longValue());
        }
        // A boolean kept in a column that has no boolean type. Both forms decode to a real Boolean,
        // so a stored 0/1 or 'Y'/'N' reads back the same as a native boolean everywhere.
        if (CoreTypeEncoding.BOOLEAN_FROM_INTEGER.equals(enc) && v instanceof Number n) {
            return n.longValue() != 0;
        }
        if (CoreTypeEncoding.BOOLEAN_FROM_TEXT.equals(enc) && v instanceof String s) {
            // Y/N is the declared convention; T/true/1 are accepted because schemas in the wild mix
            // them.
            String t = s.trim();
            return t.equalsIgnoreCase("Y")
                    || t.equalsIgnoreCase("T")
                    || t.equalsIgnoreCase("true")
                    || t.equals("1");
        }
        if (enc instanceof EpochTypeEncoding epoch && v instanceof Number n) {
            return LocalDateTime.ofInstant(epochInstant(epoch.getUnit(), n.longValue()), zone);
        }
        return v;
    }

    private Object decodeComplex(Object v, TypeDescriptor td) {
        try {
            if (CoreTypeFamily.ARRAY.equals(td.getTypeFamily())) {
                List<Object> out = new ArrayList<>();
                for (Object e : elements(v)) {
                    out.add(leaf(e, td.element()));
                }
                return Collections.unmodifiableList(out);
            } else if (CoreTypeFamily.STRUCT.equals(td.getTypeFamily())) {
                Object[] attributes = attributes(v);
                if (attributes.length != td.children().size()) {
                    throw new IllegalArgumentException(
                            "a struct of "
                                    + attributes.length
                                    + " fields where "
                                    + td.portable()
                                    + " has "
                                    + td.children().size());
                }
                Map<String, Object> out = new LinkedHashMap<>();
                for (int i = 0; i < attributes.length; i++) {
                    out.put(td.fieldNames().get(i), leaf(attributes[i], td.children().get(i)));
                }
                return Collections.unmodifiableMap(out);
            } else if (v instanceof Map<?, ?> map) {
                Map<Object, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    out.put(leaf(e.getKey(), td.key()), leaf(e.getValue(), td.value()));
                }
                return Collections.unmodifiableMap(out);
            }
            throw new IllegalArgumentException(
                    "a " + v.getClass().getName() + " is not a value of " + td.portable());
        } catch (SQLException e) {
            throw new KorykiaiException(e);
        }
    }

    private static List<?> elements(Object v) throws SQLException {
        if (v instanceof java.sql.Array a) {
            return elementsOf(a.getArray());
        } else if (v instanceof List<?> list) {
            return list;
        }
        return elementsOf(v);
    }

    private static List<?> elementsOf(Object array) {
        if (array instanceof Object[] objects) {
            return Arrays.asList(objects);
        } else if (array != null && array.getClass().isArray()) {
            int n = java.lang.reflect.Array.getLength(array);
            List<Object> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                out.add(java.lang.reflect.Array.get(array, i));
            }
            return out;
        }
        throw new IllegalArgumentException(
                "a " + (array == null ? "null" : array.getClass().getName()) + " is not an array");
    }

    private static Object[] attributes(Object v) throws SQLException {
        if (v instanceof java.sql.Struct s) {
            return s.getAttributes();
        } else if (v instanceof Map<?, ?> map) {
            return map.values().toArray();
        } else if (v instanceof Object[] objects) {
            return objects;
        }
        throw new IllegalArgumentException("a " + v.getClass().getName() + " is not a struct");
    }

    /** One element, field, key or value: to canonical java.time, then decoded by its own type. */
    private Object leaf(Object v, TypeDescriptor td) {
        if (v == null) {
            return null;
        }
        if (v instanceof java.sql.Timestamp ts) {
            v = ts.toLocalDateTime();
        } else if (v instanceof java.sql.Date d) {
            v = d.toLocalDate();
        } else if (v instanceof java.sql.Time t) {
            v = t.toLocalTime();
        } else if (v instanceof OffsetDateTime odt
                && CoreTypeEncoding.INSTANT.equals(td.getTypeEncoding())) {
            v = odt.toInstant();
        }
        return decode(v, new LeafInfo(td));
    }

    /** The column description of a leaf inside a container: nothing but its type. */
    private record LeafInfo(TypeDescriptor type) implements ColumnInfo {
        @Override
        public void setHeader(String header) {}

        @Override
        public TypeDescriptor getTypeDescriptor() {
            return type;
        }
    }

    private static Instant epochInstant(ChronoUnit unit, long value) {
        return switch (unit) {
            case MILLIS -> Instant.ofEpochMilli(value);
            case MICROS -> Instant.EPOCH.plus(value, ChronoUnit.MICROS);
            case NANOS -> Instant.EPOCH.plusNanos(value);
            default -> Instant.ofEpochSecond(value); // SECONDS
        };
    }

    /** A duration in a numeric column (count of {@code unit}) → the canonical {@link Interval}. */
    private static Interval intervalAmount(ChronoUnit unit, long value) {
        return switch (unit) {
            case YEARS -> Interval.ofMonths(Math.toIntExact(value * 12));
            case MONTHS -> Interval.ofMonths(Math.toIntExact(value));
            case WEEKS -> Interval.ofDays(Math.toIntExact(value * 7));
            case DAYS -> Interval.ofDays(Math.toIntExact(value));
            case HOURS -> Interval.ofNanos(value * 3_600_000_000_000L);
            case MINUTES -> Interval.ofNanos(value * 60_000_000_000L);
            case MILLIS -> Interval.ofNanos(value * 1_000_000L);
            case MICROS -> Interval.ofNanos(value * 1_000L);
            case NANOS -> Interval.ofNanos(value);
            default -> Interval.ofNanos(value * 1_000_000_000L); // SECONDS
        };
    }

    /**
     * A clock-time ({@code "01:00:00"} → 1h) or ISO-8601 ({@code "P1Y2M3DT4H"}) interval string →
     * {@link Interval}.
     */
    /**
     * Decode a {@code calendar_distance} wire value {@code "startEpoch;endEpoch"} (two
     * epoch-seconds) into a calendar {@link Interval} (months/days/time). The whole decomposition
     * is done here so it is uniform across dialects — SQL only supplies the two instants. Reference
     * semantic is {@link Period#between}: whole calendar months/days (month-end clamped) plus the
     * time-of-day remainder (borrowing a day when the end time-of-day is earlier than the start's).
     * Negative spans return the negated forward distance.
     */
    private static Interval calendarDistance(String wire) {
        int sep = wire.indexOf(';');
        // A NULL operand: on most dialects `x || NULL` is NULL, so the whole cell is NULL and never
        // reaches here — but Oracle's || treats NULL as '', yielding a half-empty "epoch;" wire.
        // Either missing half means one instant was NULL, so the span is undefined.
        if (sep <= 0 || sep >= wire.length() - 1) {
            return null;
        }
        long startEpoch = parseEpochSeconds(wire.substring(0, sep));
        long endEpoch = parseEpochSeconds(wire.substring(sep + 1));

        boolean negative = endEpoch < startEpoch;
        LocalDateTime a =
                LocalDateTime.ofEpochSecond(Math.min(startEpoch, endEpoch), 0, ZoneOffset.UTC);
        LocalDateTime b =
                LocalDateTime.ofEpochSecond(Math.max(startEpoch, endEpoch), 0, ZoneOffset.UTC);

        LocalDate aDate = a.toLocalDate();
        LocalDate bDate = b.toLocalDate();
        int seconds = b.toLocalTime().toSecondOfDay() - a.toLocalTime().toSecondOfDay();
        if (seconds < 0) { // end time-of-day earlier than start's: borrow a calendar day
            seconds += 86400;
            bDate = bDate.minusDays(1);
        }
        Period p = Period.between(aDate, bDate);
        Interval iv =
                Interval.of(
                        p.getYears() * 12 + p.getMonths(), p.getDays(), seconds * 1_000_000_000L);
        return negative ? iv.negated() : iv;
    }

    /**
     * Epoch-seconds from the wire, tolerating a fractional part (e.g. MySQL UNIX_TIMESTAMP →
     * "...000000").
     */
    private static long parseEpochSeconds(String s) {
        s = s.trim();
        int dot = s.indexOf('.');
        return Long.parseLong(dot < 0 ? s : s.substring(0, dot));
    }

    public static Object parseIntervalString(String s) {
        s = s.trim();
        int p = (s.startsWith("-") || s.startsWith("+")) ? 1 : 0;
        if (s.regionMatches(true, p, "P", 0, 1)) {
            int t = s.indexOf('T');
            String date = t < 0 ? s : s.substring(0, t);
            String time = t < 0 ? "" : s.substring(t);
            Period per = date.length() > p + 1 ? Period.parse(date) : Period.ZERO;
            long nanos =
                    time.isEmpty()
                            ? 0L
                            : Duration.parse((p == 1 ? s.substring(0, 1) : "") + "P" + time)
                                    .toNanos();
            return Interval.of(per.getYears() * 12 + per.getMonths(), per.getDays(), nanos);
        }
        String[] hms = s.split(":");
        if (hms.length == 3) {
            long secs =
                    Long.parseLong(hms[0]) * 3600
                            + Long.parseLong(hms[1]) * 60
                            + Long.parseLong(hms[2]);
            return Interval.ofNanos(secs * 1_000_000_000L);
        }
        return s; // unrecognized — leave as the raw string
    }
}
