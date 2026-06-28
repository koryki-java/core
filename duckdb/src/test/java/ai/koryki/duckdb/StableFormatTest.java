package ai.koryki.duckdb;

import ai.koryki.databases.cases.StableFormat;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Golden tolerance: StableFormat normalizes dialect-specific to_text(...) string output. */
class StableFormatTest {

    private final StableFormat f = new StableFormat(Locale.ROOT);

    @Test
    void decimalStringsRoundToScaleOne() {
        assertEquals("12345.7", f.format("12345.6789", null));      // DECIMAL trailing digits
        assertEquals("1.6",     f.format("1.618033988749", null));  // float precision
        assertEquals("39.0",    f.format("39.00", null));           // trailing zeros
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
