package ai.koryki.databases.cases;

import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeDescriptor;
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
            return isFloat(type) ? formatFloat(num) : formatNumber(num);
        }

        // Values are already decoded to canonical java.time at the read boundary; render the
        // test-stable, whole-second form (space-separated datetime, HH:mm:ss time).
        if (o instanceof LocalDateTime dt) return dt.format(CANON_DATETIME);
        if (o instanceof LocalDate d)      return d.format(CANON_DATE);
        if (o instanceof LocalTime t)      return t.format(CANON_TIME);

        if (o instanceof String s) {
            // Golden tolerance for to_text(...) output, whose text differs across dialects:
            //  - numeric-with-fraction: trailing zeros ("1.50" vs "1.5000") and float
            //    precision ("0.1" vs "0.10000001") -> normalize like a Number.
            //  - temporal: fractional seconds ("14:30:45" vs "14:30:45.0000000") and the
            //    date/time separator -> re-format to canonical whole-second ISO.
            // The guards leave integers ("123"), booleans ("1"/"true") and non-numeric,
            // non-temporal text untouched. Test-only blindness (sub-1e-6 / sub-second);
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

    /**
     * Six decimals, then trailing zeros dropped — so an integral value comes out exact.
     *
     * <p>The scale is golden tolerance, not presentation: the engines return the same value in
     * different shapes ({@code 1.50} vs {@code 1.5000}) and a double carries noise around its eighth
     * digit. Dropping trailing zeros settles the first, six decimals absorb the second.
     *
     * <p>It used to be <strong>one</strong> decimal, which normalised far more than the engines ever
     * disagreed by: an integer read as {@code "830.0"}, {@code pi()} as {@code "3.1"}, and 0.14 and
     * 0.15 were the same golden — so every cross-dialect difference below 0.1 was invisible, which
     * is most of what a price, a discount, an average or a division can differ by.
     *
     * <p>Measured, 17 fixtures of ~394 actually differ beyond 1e-6, and they split cleanly: real
     * semantic differences (to_decimal on SQLite, to_float's 32- vs 64-bit width) and sums whose
     * last digits depend on the order the engine added them in. The latter are pinned in the
     * fixture with an explicit {@code round(...)} rather than blurred by the harness — an
     * expectation the query states is worth more than one the formatter hides. Never let any of
     * this reach {@link LocaleFormat}, whose output is a real query result.
     */
    /** Whether the declared type is a floating-point one — the only family rounded to significance. */
    private static boolean isFloat(TypeDescriptor type) {
        return type != null && CoreTypeFamily.FLOAT.equals(type.getTypeFamily());
    }

    /**
     * Six <em>significant</em> digits, not six decimals — the precision a 32-bit float actually
     * carries.
     *
     * <p>The engines disagree on how many digits to print for one: measured, the same REAL column
     * came back as {@code 1.618034} on five dialects and {@code 1.61803} on MariaDB and Trino, and
     * {@code to_float(12345.6789)} as {@code 12345.678711}, {@code 12345.679} and {@code 12345.7}.
     * Those are the same value written to different widths, not different values, and no shared
     * golden can hold all three.
     *
     * <p>Rounding by decimals cannot fix that — the difference sits in the fourth to seventh
     * significant digit, wherever the decimal point happens to be. Rounding by significance does,
     * and it stays coarse enough to leave a genuine difference visible: SQLite's {@code divide}
     * result differs from the other seven in the sixth significant digit and still shows.
     *
     * <p>Only the FLOAT family. A DECIMAL keeps every digit it was given — that is the point of the
     * type — and an integer is untouched.
     */
    public static String formatFloat(Number number) {
        BigDecimal bd = (number instanceof BigDecimal)
                ? (BigDecimal) number
                : new BigDecimal(number.toString());
        bd = bd.round(new java.math.MathContext(6, RoundingMode.HALF_UP)).stripTrailingZeros();
        return (bd.scale() < 0 ? bd.setScale(0) : bd).toPlainString();
    }

    public static String formatNumber(Number number) {

        BigDecimal bd = (number instanceof BigDecimal)
                ? (BigDecimal) number
                : new BigDecimal(number.toString());
        bd = bd.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
        return (bd.scale() < 0 ? bd.setScale(0) : bd).toPlainString();
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
