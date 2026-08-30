package ai.koryki.antlr;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.validate.Violation;
import ai.koryki.kql.KQLTranspiler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which character a position points at.
 *
 * <p>The convention was never written down and never checked: line came from ANTLR 1-based, column
 * 0-based, and the mixture travelled all the way to the user. It cost nothing to get wrong, because
 * every consumer compared positions only against other positions — and comparisons survive a uniform
 * shift. The first consumer to place a position <em>in text the user reads</em> is where it breaks,
 * and that is what this pins down.
 *
 * <p>The multi-line case is the one worth having: it is the only place the column is computed rather
 * than taken from the token, so it is the only place the offset could be applied twice or not at all.
 */
public class PositionTest {

    private static LinkResolver resolver;

    @BeforeAll
    public static void readNorthwindDB() throws IOException {
        resolver = NorthwindService.resolver();
    }

    private static Violation firstViolation(String kql) {
        List<Violation> v = KQLTranspiler.builder(kql, resolver)
                .functions(DuckdbBaseDialect.INSTANCE.getFunctionRenderer())
                .build().violations();
        assertTrue(!v.isEmpty(), "the fixture must produce a violation, or it tests nothing: " + kql);
        return v.get(0);
    }

    /**
     * Counted by hand against the query text: {@code nonsense(} begins at the seventh character of
     * line 1, so the column is 7 — not the 6 an ANTLR offset would give.
     */
    @Test
    void theColumnIsOneBased() {
        //            1234567890123456789012345
        String kql = "FIND customers c FETCH nonsense(1) x";
        Range r = firstViolation(kql).getRange();

        assertEquals(1, r.getStart().getLine(), "single-line query");
        assertEquals(24, r.getStart().getPos(), "column counts from 1, like an editor");
    }

    /** A later line keeps its own column count, and the line itself was already 1-based. */
    @Test
    void aLaterLineCountsItsColumnsFromOneAgain() {
        String kql = "FIND customers c\n"
                //    1234567
                + "FETCH nonsense(1) x";
        Range r = firstViolation(kql).getRange();

        assertEquals(2, r.getStart().getLine());
        assertEquals(7, r.getStart().getPos());
    }

    /**
     * The stop is exclusive: start + length, so the range covers exactly the token and the next
     * character is already outside it. That is the shape an editor marker expects.
     */
    @Test
    void theStopIsExclusive() {
        String kql = "FIND customers c FETCH nonsense(1) x";
        Range r = firstViolation(kql).getRange();

        int length = "nonsense(1)".length();
        assertEquals(r.getStart().getPos() + length, r.getStop().getPos(),
                "stop is one past the last character");
    }

    /**
     * A raw ANTLR column must not reach a Position. Nothing in the codebase constructs one that way
     * today; the guard is here so that the next caller who tries finds out immediately rather than
     * shipping a column that is one too far left.
     */
    @Test
    void aZeroBasedColumnIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Position(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Position(0, 1));
    }
}
