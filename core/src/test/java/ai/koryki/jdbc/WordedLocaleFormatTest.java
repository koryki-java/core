package ai.koryki.jdbc;

import ai.koryki.catalog.schema.types.TypeDescriptor;
import ai.koryki.jdbc.WordedLocaleFormat.Width;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Business duration rendering: calendar fields as words (WIDE/SHORT/NARROW) + the fixed clock part as
 * HH:MM:SS, with an optional maxUnits cap. Base {@link LocaleFormat} stays canonical (toKql).
 */
class WordedLocaleFormatTest {

    private static final TypeDescriptor IV = TypeDescriptor.INTERVAL;

    private static long clock(long h, long m, long s) {
        return ((h * 3600) + (m * 60) + s) * 1_000_000_000L;
    }

    private final WordedLocaleFormat wide = WordedLocaleFormat.wide(Locale.ENGLISH);
    private final WordedLocaleFormat compact = WordedLocaleFormat.compact(Locale.ENGLISH);
    private final WordedLocaleFormat narrow = new WordedLocaleFormat(Locale.ENGLISH, Width.NARROW);

    @Test
    void pureClockIsHHMMSS() {
        assertEquals("01:02:03", wide.format(Interval.ofNanos(clock(1, 2, 3)), IV));
        assertEquals("25:00:00", wide.format(Interval.ofNanos(clock(25, 0, 0)), IV));   // no 24h rollup
    }

    @Test
    void widthVariants() {
        Interval iv = Interval.ofMonths(14);   // 1 year 2 months
        assertEquals("1 year 2 months", wide.format(iv, IV));
        assertEquals("1 yr 2 mos", compact.format(iv, IV));
        assertEquals("1y 2mo", narrow.format(iv, IV));
    }

    @Test
    void singularVsPlural() {
        assertEquals("1 year", wide.format(Interval.ofMonths(12), IV));
        assertEquals("1 month", wide.format(Interval.ofMonths(1), IV));
        assertEquals("1 day", wide.format(Interval.ofDays(1), IV));
        assertEquals("2 days", wide.format(Interval.ofDays(2), IV));
    }

    @Test
    void mixedIsWordsPlusClock() {
        assertEquals("1 year 2 months 1 day 01:02:03", wide.format(Interval.of(14, 1, clock(1, 2, 3)), IV));
        assertEquals("1 yr 2 mos 1 day 01:02:03", compact.format(Interval.of(14, 1, clock(1, 2, 3)), IV));
    }

    @Test
    void zeroAndNegative() {
        assertEquals("00:00:00", wide.format(Interval.ZERO, IV));
        assertEquals("-1 day 01:02:03", wide.format(Interval.of(0, -1, -clock(1, 2, 3)), IV));
    }

    @Test
    void maxUnitsCapsToMostSignificant() {
        Interval full = Interval.of(14, 1, clock(1, 2, 3));   // 1 year 2 months 1 day 01:02:03
        assertEquals("1 year", new WordedLocaleFormat(Locale.ENGLISH, Width.WIDE, 1).format(full, IV));
        assertEquals("1 year 2 months", new WordedLocaleFormat(Locale.ENGLISH, Width.WIDE, 2).format(full, IV));
        assertEquals("1 year 2 months 1 day", new WordedLocaleFormat(Locale.ENGLISH, Width.WIDE, 3).format(full, IV));
        assertEquals("1 year 2 months 1 day 01:02:03", new WordedLocaleFormat(Locale.ENGLISH, Width.WIDE, 0).format(full, IV));
    }

    @Test
    void baseFormatStaysCanonical() {
        Interval iv = Interval.of(14, 1, clock(1, 2, 3));
        assertEquals(iv.toKql(), new LocaleFormat(Locale.ENGLISH).format(iv, IV));
    }
}
