package ai.koryki.kql;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code BETWEEN} with a temporal literal upper bound is rewritten to a half-open interval so a
 * range ending on a day covers that whole day (docs/TEMPORAL.md). A DATE names a day, so that is
 * always right for one. A TIMESTAMP names an <em>instant</em>, and it used to be treated as a day
 * anyway — the time was discarded and the bound moved to the next midnight, which turned
 * {@code AND "…17:00:00"} into a range reaching seven hours further than written.
 *
 * <p>The rewrite now applies to a TIMESTAMP only where the written time is the last instant of its
 * day that {@code TIMESTAMP_STRING} can express: {@code HH:MI:SS} with optional milliseconds gives
 * exactly {@code 23:59:59.999}, {@code 23:59:59} and {@code 23:59:00}.
 */
class BetweenRewriteTest {

    private static LinkResolver resolver;

    @BeforeAll
    static void init() throws IOException {
        resolver = NorthwindService.resolver();
    }

    private static String where(String upper) {
        String kql = "FIND orders o FILTER o.delivered_date BETWEEN \"1996-07-01 00:00:00\" AND \""
                + upper + "\" FETCH o.order_id";
        return KQLTranspiler.builder(kql, resolver).build()
                .getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")))
                .replaceAll("\\s+", " ");
    }

    /** Half-open: the bound is replaced by the next midnight and the comparison becomes strict. */
    private static void assertWidenedToNextMidnight(String upper) {
        String sql = where(upper);
        assertTrue(sql.contains("< TIMESTAMP '1997-01-01 00:00:00'"),
                "expected the day to be covered half-open, got: " + sql);
    }

    /** Literal: the BETWEEN survives, so the upper bound stays inclusive and keeps its time. */
    private static void assertKeptLiteral(String upper) {
        String sql = where(upper);
        assertTrue(sql.contains("BETWEEN") && sql.contains("TIMESTAMP '1996-12-31 " + upper.substring(11) + "'"),
                "expected the written time to survive, got: " + sql);
    }

    @Test
    void theThreeEndOfDaySpellingsCoverTheWholeDay() {
        assertWidenedToNextMidnight("1996-12-31 23:59:59.999");   // last millisecond
        assertWidenedToNextMidnight("1996-12-31 23:59:59");       // last second
        assertWidenedToNextMidnight("1996-12-31 23:59:00");       // last minute — not writable as 23:59
    }

    @Test
    void aTimeTheAuthorChoseIsTakenLiterally() {
        assertKeptLiteral("1996-12-31 23:59:58");                 // one second short of the end
        assertKeptLiteral("1996-12-31 17:00:00");
        assertKeptLiteral("1996-12-31 00:00:00");                 // midnight is the START of a day
    }

    /** A DATE names a day, so its whole day is covered however the rewrite treats timestamps. */
    @Test
    void aDateUpperBoundStillCoversItsWholeDay() {
        String kql = "FIND orders o FILTER o.delivered_date BETWEEN \"1996-07-01\" AND \"1996-12-31\""
                + " FETCH o.order_id";
        String sql = KQLTranspiler.builder(kql, resolver).build()
                .getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")))
                .replaceAll("\\s+", " ");
        assertTrue(sql.contains("< DATE '1997-01-01'"), sql);
    }
}
