package ai.koryki.presentation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * A fixed number of decimal places: {@code DECIMALS:2}.
 *
 * <p>For quantities whose precision is a convention rather than a measurement — money in EUR always
 * shows two places, a count none — regardless of how large the value is.
 *
 * <p>Rounding here is a display decision, the one {@code LocaleFormat} already permits ("no rounding
 * beyond display scale"). The stored value is untouched.
 */
public final class DecimalsPresentation implements Presentation {

    public static final String PREFIX = "DECIMALS:";

    private final int decimals;

    public DecimalsPresentation(int decimals) {
        if (decimals < 0) {
            throw new IllegalArgumentException("decimals must not be negative: " + decimals);
        }
        this.decimals = decimals;
    }

    public static DecimalsPresentation parse(String name) {
        return new DecimalsPresentation(Integer.parseInt(name.substring(PREFIX.length())));
    }

    public int getDecimals() {
        return decimals;
    }

    @Override
    public String name() {
        return PREFIX + decimals;
    }

    @Override
    public String render(Object value, Locale locale) {
        if (!(value instanceof Number n)) {
            return null;
        }
        BigDecimal bd = n instanceof BigDecimal b ? b : new BigDecimal(n.toString());
        if (locale == null) {
            return bd.setScale(decimals, RoundingMode.HALF_UP).toPlainString();
        }
        NumberFormat nf = NumberFormat.getNumberInstance(locale);
        nf.setMinimumFractionDigits(decimals);
        nf.setMaximumFractionDigits(decimals);
        return nf.format(bd);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DecimalsPresentation other && decimals == other.decimals;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(decimals);
    }

    @Override
    public String toString() {
        return name();
    }
}
