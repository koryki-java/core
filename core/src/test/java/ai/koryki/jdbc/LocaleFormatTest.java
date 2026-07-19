package ai.koryki.jdbc;

import ai.koryki.catalog.types.CoreTypeEncoding;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeDescriptor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Type-driven, locale-aware read-layer formatting. Asserts locale-distinguishing traits (CLDR-stable). */
class LocaleFormatTest {

    private static final TypeDescriptor TIME = new TypeDescriptor("TIME", null, CoreTypeFamily.TIME);
    private static final TypeDescriptor DATE = new TypeDescriptor("DATE", null, CoreTypeFamily.DATE);
    private static final TypeDescriptor DECIMAL_1 = new TypeDescriptor("DECIMAL", null, CoreTypeFamily.DECIMAL, 6, 1);
    private static final TypeDescriptor INTERVAL = new TypeDescriptor("INTERVAL", null, CoreTypeFamily.INTERVAL);

    @Test
    void canonicalIsIsoAndLocaleIndependent() {
        LocaleFormat f = new LocaleFormat(null);
        assertEquals("13:30:40", f.format(LocalTime.of(13, 30, 40), TIME));
        assertEquals("2026-05-17", f.format(LocalDate.of(2026, 5, 17), DATE));
        assertEquals("1234.5", f.format(new BigDecimal("1234.5"), DECIMAL_1));
    }

    @Test
    void canonicalAppliesDisplayScaleWithoutChangingValue() {
        LocaleFormat f = new LocaleFormat(null);
        assertEquals("1234.0", f.format(new BigDecimal("1234"), DECIMAL_1));   // scale 1, padded
    }

    @Test
    void germanAndUsNumbersDifferInSeparators() {
        String de = new LocaleFormat(Locale.GERMANY).format(new BigDecimal("1234.5"), DECIMAL_1);
        String us = new LocaleFormat(Locale.US).format(new BigDecimal("1234.5"), DECIMAL_1);
        assertEquals("1.234,5", de);   // German: '.' grouping, ',' decimal
        assertEquals("1,234.5", us);   // US:     ',' grouping, '.' decimal
    }

//    @Test
//    void germanDateUsesDotsUsDateDoesNot() {
//        String de = new LocaleFormat(Locale.GERMANY).format(LocalDate.of(2026, 5, 17), DATE);
//        String us = new LocaleFormat(Locale.US).format(LocalDate.of(2026, 5, 17), DATE);
//        assertTrue(de.contains("2026") && de.contains("."), de);
//        assertFalse(de.contains("/"), de);
//        assertTrue(us.contains("2026"), us);
//        assertFalse(us.equals(de), "locales must differ");
//    }

//    @Test
//    void timestampInstantIsDisplayedInModelZone() {
//        // JdbcDatabase reads TIMESTAMP as a zone-neutral Instant
//        TypeDescriptor ts = new TypeDescriptor("TIMESTAMP", null, CoreTypeFamily.TIMESTAMP);
//        java.time.Instant i = java.time.LocalDateTime.of(2024, 3, 15, 14, 30, 50).toInstant(java.time.ZoneOffset.UTC);
//        assertEquals("2024-03-15 14:30:50", new LocaleFormat(null).format(i, ts));
//    }

    @Test
    void timeEncodingColumnFormatsAsTime() {
        // a seconds-from-midnight column resolves to the TIME family; the SQL already
        // decoded it to a TIME value, so the formatter just renders the time of day.
        TypeDescriptor secs = new TypeDescriptor("INTEGER", CoreTypeEncoding.TIME_SECONDS_FROM_MIDNIGHT, CoreTypeFamily.TIME);
        assertEquals("13:30:40", new LocaleFormat(null).format(LocalTime.of(13, 30, 40), secs));
    }

    @Test
    void intervalFormatsAsKorykiDurationRegardlessOfLocale() {
        Interval iv = Interval.of(14, 3, 4L * 3_600_000_000_000L);   // 1y 2mo 3d 4h
        assertEquals("1y2mo3d4h", new LocaleFormat(null).format(iv, INTERVAL));
        assertEquals("1y2mo3d4h", new LocaleFormat(Locale.GERMANY).format(iv, INTERVAL));
        // sub-second is shown as fractional seconds (smallest emitted unit), not ms
        assertEquals("0.5s", new LocaleFormat(null).format(Interval.ofNanos(500_000_000L), INTERVAL));
    }

    @Test
    void textFromBooleanEncodingNormalizesToTrueFalse() {
        // to_text(boolean) is TEXT, but dialects render it as "0"/"1" (MariaDB) or
        // "true"/"false" (DuckDB); the TEXT_FROM_BOOLEAN encoding normalizes both.
        TypeDescriptor boolText = new TypeDescriptor("TEXT", CoreTypeEncoding.TEXT_FROM_BOOLEAN, CoreTypeFamily.TEXT);
        LocaleFormat f = new LocaleFormat(null);
        assertEquals("true",  f.format("1", boolText));
        assertEquals("false", f.format("0", boolText));
        assertEquals("true",  f.format("true", boolText));
        assertEquals("false", f.format("false", boolText));
    }

    @Test
    void nullValueIsEmptyAndUnknownTypeFallsBackToToString() {
        LocaleFormat f = new LocaleFormat(Locale.US);
        assertEquals("", f.format(null, TIME));
        assertEquals("hello", f.format("hello", null));           // no descriptor → toString
        assertEquals("true", f.format(Boolean.TRUE, null));
    }
}
