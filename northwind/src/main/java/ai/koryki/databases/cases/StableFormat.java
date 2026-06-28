package ai.koryki.databases.cases;

import ai.koryki.catalog.schema.types.TypeDescriptor;
import ai.koryki.jdbc.LocaleFormat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public class StableFormat extends LocaleFormat {
    /**
     * @param locale presentation locale, or {@code null} for canonical (ISO) output
     */
    public StableFormat(Locale locale) {
        super(locale);
    }

    @Override
    public String format(Object o, TypeDescriptor type) {

        if (o instanceof Number num) {
            return formatNumber(num);
        }

        // Values are already decoded to canonical java.time at the read boundary; render the
        // test-stable, whole-second form (space-separated datetime, HH:mm:ss time).
        if (o instanceof LocalDateTime dt) return dt.format(CANON_DATETIME);
        if (o instanceof LocalDate d)      return d.format(CANON_DATE);
        if (o instanceof LocalTime t)      return t.format(CANON_TIME);

        if (o instanceof String s) {
            // Golden tolerance for to_text(...) output, whose text differs across dialects:
            //  - numeric-with-fraction: trailing zeros ("1.50" vs "1.5000") and float
            //    precision ("0.1" vs "0.10000001") -> round to scale 1, like a Number.
            //  - temporal: fractional seconds ("14:30:45" vs "14:30:45.0000000") and the
            //    date/time separator -> re-format to canonical whole-second ISO.
            // The guards leave integers ("123"), booleans ("1"/"true") and non-numeric,
            // non-temporal text untouched. Test-only blindness (sub-scale-1 / sub-second);
            // never bleed this into LocaleFormat — real query output must not be rounded.
            if (s.indexOf('.') >= 0) {
                BigDecimal bd = tryDecimal(s);
                if (bd != null) {
                    return formatNumber(bd);
                }
            }
            String temporal = tryTemporal(s);
            if (temporal != null) {
                return temporal;
            }
        }

        return super.format(o, type);
    }

    public static String formatNumber(Number number) {

        BigDecimal bd = (number instanceof BigDecimal)
                ? (BigDecimal) number
                : new BigDecimal(number.toString());
        return bd.setScale(1, RoundingMode.HALF_DOWN).toString();
    }

    /** A string that is a plain decimal, or {@code null} if it isn't one (dates, versions, text). */
    private static BigDecimal tryDecimal(String s) {
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException notADecimal) {
            return null;
        }
    }

    private static final DateTimeFormatter CANON_DATE     = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter CANON_TIME     = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter CANON_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Canonical whole-second ISO rendering of an ISO-ish temporal string (drops fractional
     * seconds, normalizes the date/time separator to a space), or {@code null} if the string
     * isn't a date/time/timestamp. Only strict ISO forms parse, so non-temporal text is left
     * untouched. Zoned timestamps are out of scope (offsets won't parse here).
     */
    private static String tryTemporal(String s) {
        s = s.trim();
        try {
            return LocalDateTime.parse(s.replace(' ', 'T')).format(CANON_DATETIME);
        } catch (DateTimeParseException notDateTime) {
            // fall through
        }
        try {
            return LocalTime.parse(s).format(CANON_TIME);
        } catch (DateTimeParseException notTime) {
            // fall through
        }
        try {
            return LocalDate.parse(s).format(CANON_DATE);
        } catch (DateTimeParseException notDate) {
            return null;
        }
    }

}
