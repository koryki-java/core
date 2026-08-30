package ai.koryki.duckdb;

import ai.koryki.databases.cases.StableFormat;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Golden tolerance: StableFormat normalizes dialect-specific to_text(...) string output. */
class StableFormatTest {

    private final StableFormat f = new StableFormat(Locale.ROOT);

    /**
     * The tolerance is trailing zeros and float noise — not precision. It used to round to one
     * decimal, which made pi() read as 3.1 and every cross-dialect difference below 0.1 invisible.
     */
    @Test
    void decimalStringsKeepTheirValueAndLoseOnlyPaddingAndNoise() {
        assertEquals("12345.6789", f.format("12345.6789", null));     // precision is kept
        assertEquals("1.618034",   f.format("1.618033988749", null)); // cut at six decimals
        assertEquals("39",         f.format("39.00", null));          // trailing zeros dropped
        assertEquals("830",        f.format("830.0", null));          // a row count, not 830.0
        assertEquals("0.1",        f.format("0.10000001", null));     // float noise absorbed
        assertEquals("0.15",       f.format("0.15", null));           // distinct from 0.14
        assertEquals("0.14",       f.format("0.14", null));
    }

    @Test
    void integersAndNonNumericTextAreUntouched() {
        assertEquals("123",           f.format("123", null));            // no '.', not rounded
        assertEquals("Hallo VARCHAR", f.format("Hallo VARCHAR", null));
        assertEquals("550e8400-e29b-41d4-a716-446655440000",
                f.format("550e8400-e29b-41d4-a716-446655440000", null)); // UUID, not a date
    }

    @Test
    void temporalStringsNormalizeToWholeSecondIso() {
        // MSSQL DATETIME2/TIME add fractional seconds; DuckDB doesn't — normalize both.
        assertEquals("14:30:45",            f.format("14:30:45.0000000", null));
        assertEquals("14:30:45",            f.format("14:30:45", null));
        assertEquals("2026-05-17 14:30:45", f.format("2026-05-17 14:30:45.0000000", null));
        assertEquals("2026-05-17 14:30:45", f.format("2026-05-17 14:30:45", null));
        assertEquals("2026-05-17",          f.format("2026-05-17", null));
    }
}
