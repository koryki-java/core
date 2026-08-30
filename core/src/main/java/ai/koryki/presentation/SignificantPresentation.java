package ai.koryki.presentation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * A fixed number of significant digits: {@code SIGNIFICANT:4}.
 *
 * <p>For measured quantities, where the accuracy matters and the position of the decimal point does
 * not: 1234.5 m and 0.0012345 m carry the same information, and fixed decimal places would wipe the
 * second one out to 0.00. Shares are measured this way too — 0.0342 deserves the digits 0.150 gets.
 */
public final class SignificantPresentation implements Presentation {

    public static final String PREFIX = "SIGNIFICANT:";

    private final int digits;

    public SignificantPresentation(int digits) {
        if (digits < 1) {
            throw new IllegalArgumentException("significant digits must be at least 1: " + digits);
        }
        this.digits = digits;
    }

    public static SignificantPresentation parse(String name) {
        return new SignificantPresentation(Integer.parseInt(name.substring(PREFIX.length())));
    }

    public int getDigits() {
        return digits;
    }

    @Override
    public String name() {
        return PREFIX + digits;
    }

    @Override
    public String render(Object value, Locale locale) {
        if (!(value instanceof Number n)) {
            return null;
        }
        BigDecimal bd = n instanceof BigDecimal b ? b : new BigDecimal(n.toString());
        BigDecimal rounded = bd.round(new MathContext(digits, RoundingMode.HALF_UP));
        // Rounding alone does not show the promised number of digits: 0.15 rounded to three
        // significant digits is still 0.15, and a reader cannot tell "we know two digits" from
        // "we know three". Padding to the declared precision is the statement being made.
        int integerDigits = rounded.precision() - rounded.scale();
        int fraction = Math.max(digits - integerDigits, 0);
        rounded = rounded.setScale(fraction, RoundingMode.HALF_UP);
        if (locale == null) {
            return rounded.toPlainString();
        }
        NumberFormat nf = NumberFormat.getNumberInstance(locale);
        nf.setMinimumFractionDigits(fraction);
        nf.setMaximumFractionDigits(fraction);
        return nf.format(rounded);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SignificantPresentation other && digits == other.digits;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(digits);
    }

    @Override
    public String toString() {
        return name();
    }
}
