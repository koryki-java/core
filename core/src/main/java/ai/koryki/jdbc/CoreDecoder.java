package ai.koryki.jdbc;

import ai.koryki.catalog.types.*;

import java.time.*;
import java.time.temporal.ChronoUnit;

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

        // INTERVAL family: materialize the portable forms (already-decoded Interval, a
        // java.time amount, a numeric count, or an ISO/clock string) into a koryki
        // Interval. Vendor-native interval objects/strings (PGInterval, oracle.sql.*,
        // DuckDB's verbose string) are converted by the per-dialect decoder before super.
        if (td != null && CoreTypeFamily.INTERVAL.equals(td.getTypeFamily())) {
            if (v instanceof Interval)   return v;
            if (v instanceof Period per) return Interval.of(per.getYears() * 12 + per.getMonths(), per.getDays(), 0L);
            if (v instanceof Duration d) return Interval.of(0, 0, d.toNanos());
            if (enc instanceof IntervalTypeEncoding it && v instanceof Number n)
                return intervalAmount(it.getUnit(), n.longValue());
            if ((CoreTypeEncoding.INTERVAL_FROM_STRING.equals(enc) || enc instanceof IntervalStringEncoding)
                    && v instanceof String s)
                return parseIntervalString(s);
            if (CoreTypeEncoding.CALENDAR_DISTANCE.equals(enc) && v instanceof String s)
                return calendarDistance(s);
            return v;
        }

        // DATE family: a calendar day, no time. Some drivers (notably Oracle, whose DATE
        // carries a time-of-day) return a date-time; truncate it so the read boundary yields a
        // canonical java.time.LocalDate uniformly. A numeric (DATE_FROM_EPOCH_DAY) value is not
        // a date-time, so it falls through to its encoding branch below.
        if (td != null && CoreTypeFamily.DATE.equals(td.getTypeFamily())) {
            if (v instanceof LocalDate)              return v;
            if (v instanceof java.sql.Date d)        return d.toLocalDate();
            if (v instanceof java.sql.Timestamp t)   return t.toLocalDateTime().toLocalDate();
            if (v instanceof LocalDateTime dt)       return dt.toLocalDate();
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
            return LocalTime.of((int) (hhmmss / 10000), (int) ((hhmmss / 100) % 100), (int) (hhmmss % 100));
        }
        if (CoreTypeEncoding.TIME_FROM_STRING.equals(enc) && v instanceof String s) {
            return LocalTime.parse(s);
        }
        if (CoreTypeEncoding.TIME_FROM_TIMESTAMP.equals(enc) || CoreTypeEncoding.TIME_FROM_DATE.equals(enc)) {
            if (v instanceof LocalDateTime dt) return dt.toLocalTime();
            if (v instanceof LocalDate)        return LocalTime.MIDNIGHT;   // a pure date has no time-of-day
        }
        if (CoreTypeEncoding.DATE_FROM_EPOCH_DAY.equals(enc) && v instanceof Number n) {
            return LocalDate.ofEpochDay(n.longValue());
        }
        if (enc instanceof EpochTypeEncoding epoch && v instanceof Number n) {
            return LocalDateTime.ofInstant(epochInstant(epoch.getUnit(), n.longValue()), zone);
        }
        return v;
    }
    private static Instant epochInstant(ChronoUnit unit, long value) {
        return switch (unit) {
            case MILLIS -> Instant.ofEpochMilli(value);
            case MICROS -> Instant.EPOCH.plus(value, ChronoUnit.MICROS);
            case NANOS  -> Instant.EPOCH.plusNanos(value);
            default     -> Instant.ofEpochSecond(value);   // SECONDS
        };
    }
    /** A duration in a numeric column (count of {@code unit}) → the canonical {@link Interval}. */
    private static Interval intervalAmount(ChronoUnit unit, long value) {
        return switch (unit) {
            case YEARS   -> Interval.ofMonths(Math.toIntExact(value * 12));
            case MONTHS  -> Interval.ofMonths(Math.toIntExact(value));
            case WEEKS   -> Interval.ofDays(Math.toIntExact(value * 7));
            case DAYS    -> Interval.ofDays(Math.toIntExact(value));
            case HOURS   -> Interval.ofNanos(value * 3_600_000_000_000L);
            case MINUTES -> Interval.ofNanos(value * 60_000_000_000L);
            case MILLIS  -> Interval.ofNanos(value * 1_000_000L);
            case MICROS  -> Interval.ofNanos(value * 1_000L);
            case NANOS   -> Interval.ofNanos(value);
            default      -> Interval.ofNanos(value * 1_000_000_000L);  // SECONDS
        };
    }

    /** A clock-time ({@code "01:00:00"} → 1h) or ISO-8601 ({@code "P1Y2M3DT4H"}) interval string → {@link Interval}. */
    /**
     * Decode a {@code calendar_distance} wire value {@code "startEpoch;endEpoch"} (two epoch-seconds) into
     * a calendar {@link Interval} (months/days/time). The whole decomposition is done here so it is uniform
     * across dialects — SQL only supplies the two instants. Reference semantic is {@link Period#between}:
     * whole calendar months/days (month-end clamped) plus the time-of-day remainder (borrowing a day when
     * the end time-of-day is earlier than the start's). Negative spans return the negated forward distance.
     */
    private static Interval calendarDistance(String wire) {
        int sep = wire.indexOf(';');
        long startEpoch = parseEpochSeconds(wire.substring(0, sep));
        long endEpoch   = parseEpochSeconds(wire.substring(sep + 1));

        boolean negative = endEpoch < startEpoch;
        LocalDateTime a = LocalDateTime.ofEpochSecond(Math.min(startEpoch, endEpoch), 0, ZoneOffset.UTC);
        LocalDateTime b = LocalDateTime.ofEpochSecond(Math.max(startEpoch, endEpoch), 0, ZoneOffset.UTC);

        LocalDate aDate = a.toLocalDate();
        LocalDate bDate = b.toLocalDate();
        int seconds = b.toLocalTime().toSecondOfDay() - a.toLocalTime().toSecondOfDay();
        if (seconds < 0) {                 // end time-of-day earlier than start's: borrow a calendar day
            seconds += 86400;
            bDate = bDate.minusDays(1);
        }
        Period p = Period.between(aDate, bDate);
        Interval iv = Interval.of(p.getYears() * 12 + p.getMonths(), p.getDays(), seconds * 1_000_000_000L);
        return negative ? iv.negated() : iv;
    }

    /** Epoch-seconds from the wire, tolerating a fractional part (e.g. MySQL UNIX_TIMESTAMP → "...000000"). */
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
            long nanos = time.isEmpty() ? 0L
                    : Duration.parse((p == 1 ? s.substring(0, 1) : "") + "P" + time).toNanos();
            return Interval.of(per.getYears() * 12 + per.getMonths(), per.getDays(), nanos);
        }
        String[] hms = s.split(":");
        if (hms.length == 3) {
            long secs = Long.parseLong(hms[0]) * 3600 + Long.parseLong(hms[1]) * 60 + Long.parseLong(hms[2]);
            return Interval.ofNanos(secs * 1_000_000_000L);
        }
        return s;   // unrecognized — leave as the raw string
    }
}
