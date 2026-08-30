package ai.koryki.presentation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Values divided by a factor and marked with its symbol: {@code SCALE:1000000:M} renders 57000000 as
 * {@code 57.00 M}.
 *
 * <p>The one presentation that cannot be concluded from the catalog. Whether a column's numbers run
 * into the millions is a property of <em>this result</em>, not of the column — so it is derived
 * after execution, from the values, by {@code Analyzer}, and only when it has seen all of them.
 *
 * <p>The symbol is an SI prefix ({@code k}, {@code M}, {@code G}) rather than a word, because the
 * derivation happens where no locale is known and "Mio." would be wrong in half the places this
 * result is read. A prefix is the same in every language.
 *
 * <p>Three significant digits, fixed: the whole point of scaling is that the lower digits stopped
 * carrying information at this magnitude.
 *
 * <p><b>This one does change what the reader sees</b> — 57000000 appears as 57. The symbol is what
 * keeps that honest, and it is the reason this presentation belongs to the business-facing formatter
 * only. The canonical path never applies a presentation at all.
 */
public final class ScalePresentation implements Presentation {

    public static final String PREFIX = "SCALE:";

    /**
     * Decimal places shown after scaling — fixed, not significant.
     *
     * <p>Every row of a scaled column shares one magnitude, so fixed places line the digits up in a
     * table and significant ones would not: 3 scaled by a million is 0.00000300 with three
     * significant digits and 0.00 with two places. At this magnitude that row <em>is</em> nothing,
     * and saying so in the same width as its neighbours is the readable answer.
     */
    private static final int DECIMALS = 2;

    private final long factor;
    private final String symbol;

    public ScalePresentation(long factor, String symbol) {
        if (factor <= 1) {
            throw new IllegalArgumentException("scale factor must be greater than 1: " + factor);
        }
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("a scale needs a symbol, or the reader cannot tell");
        }
        this.factor = factor;
        this.symbol = symbol;
    }

    public static ScalePresentation parse(String name) {
        String body = name.substring(PREFIX.length());
        int sep = body.indexOf(':');
        if (sep < 0) {
            throw new IllegalArgumentException("expected " + PREFIX + "<factor>:<symbol>, got: " + name);
        }
        return new ScalePresentation(Long.parseLong(body.substring(0, sep)), body.substring(sep + 1));
    }

    public long getFactor() {
        return factor;
    }

    public String getSymbol() {
        return symbol;
    }

    @Override
    public String name() {
        return PREFIX + factor + ":" + symbol;
    }

    @Override
    public String render(Object value, Locale locale) {
        if (!(value instanceof Number n)) {
            return null;
        }
        BigDecimal bd = n instanceof BigDecimal b ? b : new BigDecimal(n.toString());
        BigDecimal scaled = bd.divide(BigDecimal.valueOf(factor), DECIMALS, RoundingMode.HALF_UP);
        String number = locale == null ? scaled.toPlainString() : format(scaled, locale, DECIMALS);
        return number + " " + symbol;
    }

    private static String format(BigDecimal scaled, Locale locale, int fraction) {
        NumberFormat nf = NumberFormat.getNumberInstance(locale);
        nf.setMinimumFractionDigits(fraction);
        nf.setMaximumFractionDigits(fraction);
        return nf.format(scaled);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ScalePresentation other && factor == other.factor && symbol.equals(other.symbol);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(factor) * 31 + symbol.hashCode();
    }

    @Override
    public String toString() {
        return name();
    }
}
