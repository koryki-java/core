package ai.koryki.iql;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A timestamp literal keeps its milliseconds. {@code TIMESTAMP_STRING} ends in an optional
 * {@code '.' DIGIT DIGIT DIGIT} and both mappers parse it, but every dialect held its own
 * {@code ofPattern("yyyy-MM-dd HH:mm:ss")} — no fractional part — so the value was parsed, carried,
 * and then dropped at the last step: {@code "…17:00:00.500"} reached the database as
 * {@code '…17:00:00'}, half a second early and silently.
 */
class TimestampLiteralMillisTest {

    private static final LocalDateTime WITH_MS = LocalDateTime.parse("1996-12-31T17:00:00.500");
    private static final LocalDateTime WHOLE   = LocalDateTime.parse("1996-12-31T17:00:00");

    @Test
    void millisecondsSurviveAndWholeSecondsGainNoFraction() {
        assertEquals("1996-12-31 17:00:00.500", SqlDialect.plainTimestamp(WITH_MS));
        assertEquals("1996-12-31 17:00:00",     SqlDialect.plainTimestamp(WHOLE));
        assertEquals("17:00:00.500", SqlDialect.plainTime(LocalTime.parse("17:00:00.500")));
        assertEquals("17:00:00",     SqlDialect.plainTime(LocalTime.parse("17:00:00")));
    }

    /**
     * Three digits or none — never {@code .5}. The lexer's fraction is exactly three digits, so a
     * shortened one would render SQL that KQL could not read back on the IQL round-trip.
     */
    @Test
    void theFractionIsAlwaysThreeDigits() {
        assertEquals("1996-12-31 17:00:00.500", SqlDialect.plainTimestamp(
                LocalDateTime.parse("1996-12-31T17:00:00.5")));
        assertEquals("1996-12-31 17:00:00.050", SqlDialect.plainTimestamp(
                LocalDateTime.parse("1996-12-31T17:00:00.05")));
        assertEquals("1996-12-31 17:00:00.007", SqlDialect.plainTimestamp(
                LocalDateTime.parse("1996-12-31T17:00:00.007")));
    }

    /** Every dialect that renders its own timestamp literal now carries the fraction through. */
    @Test
    void everyDialectKeepsTheFraction() {
        assertEquals("TIMESTAMP '1996-12-31 17:00:00.500'",
                DuckdbBaseDialect.INSTANCE.timestampLiteral(WITH_MS));
        assertEquals("TIME '17:00:00.500'",
                DuckdbBaseDialect.INSTANCE.timeLiteral(LocalTime.parse("17:00:00.500")));
    }
}
