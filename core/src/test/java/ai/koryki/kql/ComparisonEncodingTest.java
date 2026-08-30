package ai.koryki.kql;

import ai.koryki.databases.temporal.duckdb.TemporalService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.validate.Violation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comparing two columns of one family that are <em>stored differently</em>.
 *
 * <p>Three outcomes, and the difference between them is the point: encodings with a lossless common
 * form are reconciled silently by the renderer; encodings without one are a warning, because nothing
 * can be done and the author needs to know; operands from different family-groups are an error,
 * because no reading of the query makes them meaningful.
 */
public class ComparisonEncodingTest {

    private static LinkResolver resolver;

    @BeforeAll
    static void setUp() throws IOException {
        resolver = TemporalService.resolver();
    }

    /**
     * EPOCH:SECONDS vs EPOCH:MILLIS. The lossless direction is seconds→millis (an exact ×1000);
     * millis→seconds would truncate. So the SECONDS side is scaled up — and note it is the
     * <em>left</em> operand that moves, which the per-operand hook alone could never do.
     */
    @Test
    void epochEncodingsReconcileOntoTheFinerUnit() {
        String sql = sql("FIND check_temporal c FILTER c.timestamp_unix_epoche = c.timestamp_java_epoche "
                + "FETCH c.nr");
        assertTrue(sql.contains("(c.timestamp_unix_epoche) * 1000 = c.timestamp_java_epoche"),
                "seconds must scale up to millis, not millis truncate to seconds:\n" + sql);
    }

    /** Same comparison, no diagnostic: reconciliation handled it. */
    @Test
    void reconcilableEncodingsAreNotReported() {
        assertTrue(violations("FIND check_temporal c FILTER c.timestamp_unix_epoche = c.timestamp_java_epoche "
                + "FETCH c.nr").isEmpty(), "a reconcilable comparison must not warn");
    }

    /**
     * TIME_FROM_INTEGER (HHMMSS) vs TIME_FROM_STRING ('HH:MM:SS') — the pair the old
     * {@code TimeEncodings.reconcile} stub was written for. Both hold a whole-second time of day, so
     * both convert to seconds-of-day and the comparison happens there. Neither side is privileged:
     * the packed integer is unpacked and the text is parsed.
     */
    @Test
    void timeEncodingsReconcileOntoSecondsOfDay() {
        String sql = sql("FIND check_temporal c FILTER c.time_from_integer = c.time_from_string FETCH c.nr");
        assertTrue(sql.contains("CAST(FLOOR(c.time_from_integer / 10000.0) AS INTEGER) * 3600"),
                "the HHMMSS side must be unpacked to seconds:\n" + sql);
        assertTrue(sql.contains("EXTRACT(EPOCH FROM CAST(c.time_from_string AS TIME))"),
                "the text side must be parsed to seconds:\n" + sql);
        assertTrue(violations("FIND check_temporal c FILTER c.time_from_integer = c.time_from_string "
                + "FETCH c.nr").isEmpty(), "a reconcilable TIME comparison must not warn");
    }

    /**
     * INTERVAL:SECONDS vs INTERVAL:MILLIS — the same exact-integer-scale shape as the epoch case,
     * and legal because both units are clock-class. Units of different classes never scale into
     * each other; {@code EncodingLatticeTest} pins that boundary down.
     */
    @Test
    void intervalUnitsReconcileOntoTheFinerUnit() {
        String sql = sql("FIND check_temporal c FILTER c.interval_seconds = c.interval_millis FETCH c.nr");
        assertTrue(sql.contains("(c.interval_seconds) * 1000 = c.interval_millis"),
                "seconds must scale up to millis:\n" + sql);
        assertTrue(violations("FIND check_temporal c FILTER c.interval_seconds = c.interval_millis "
                + "FETCH c.nr").isEmpty(), "a reconcilable interval comparison must not warn");
    }

    /**
     * A pair with genuinely no common ground: a numeric INTERVAL count and an ISO-8601 text
     * interval. Same family, but no lossless conversion exists — {@code "P1Y2M"} has no fixed
     * second count at all — so nothing can be rendered and the author is told rather than handed
     * silently wrong SQL. Advisory, not fatal — see {@link #familyGroupMismatchIsAnError}.
     */
    @Test
    void unreconcilableEncodingsWarn() {
        List<Violation> v = violations("FIND check_temporal c FILTER c.interval_seconds = c.interval_char "
                + "FETCH c.nr");
        assertEquals(1, v.size(), "expected exactly one diagnostic, got " + v);
        assertFalse(v.get(0).isError(), "an encoding mismatch is advisory, not fatal: " + v.get(0));
        assertTrue(v.get(0).getMessage().contains("lossless common encoding"), v.get(0).getMessage());
    }

    /** Different family-groups — TIME against TEXT — is the class that has no valid reading. */
    @Test
    void familyGroupMismatchIsAnError() {
        List<Violation> v = violations("FIND check_temporal c FILTER c.time_time = c.uuid_string FETCH c.nr");
        assertEquals(1, v.size(), "expected exactly one diagnostic, got " + v);
        assertTrue(v.get(0).isError(), "a cross-family-group comparison must be fatal: " + v.get(0));
        assertTrue(v.get(0).getMessage().contains("family-groups"), v.get(0).getMessage());
    }

    private static String sql(String kql) {
        return KQLTranspiler.builder(kql, resolver).build()
                .getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")));
    }

    private static List<Violation> violations(String kql) {
        return KQLTranspiler.builder(kql, resolver)
                .functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer()).build().violations();
    }
}
