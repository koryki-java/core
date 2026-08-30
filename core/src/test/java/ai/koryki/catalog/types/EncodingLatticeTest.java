package ai.koryki.catalog.types;

import org.junit.jupiter.api.Test;

import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lattice's arithmetic, tested directly. Two of these entries have no live-database coverage —
 * {@code SCALED:n} because the fixtures hold only one scaled column, and the negative interval cases
 * because a query that would exercise them is rejected before it reaches the lattice — so this is
 * where they are pinned down.
 */
class EncodingLatticeTest {

    private static IntervalTypeEncoding iv(ChronoUnit u) {
        return new IntervalTypeEncoding(u);
    }

    @Test
    void intervalUnitsScaleWithinTheClockClass() {
        assertTrue(EncodingLattice.losslessTargets(iv(ChronoUnit.SECONDS)).contains(iv(ChronoUnit.MILLIS)));
        assertEquals("(x) * 1000",
                EncodingLattice.convertSql("x", iv(ChronoUnit.SECONDS), iv(ChronoUnit.MILLIS)));
        assertEquals("(x) * 3600",
                EncodingLattice.convertSql("x", iv(ChronoUnit.HOURS), iv(ChronoUnit.SECONDS)));
    }

    @Test
    void intervalUnitsScaleWithinTheDayAndMonthClasses() {
        assertEquals("(x) * 7", EncodingLattice.convertSql("x", iv(ChronoUnit.WEEKS), iv(ChronoUnit.DAYS)));
        assertEquals("(x) * 12", EncodingLattice.convertSql("x", iv(ChronoUnit.YEARS), iv(ChronoUnit.MONTHS)));
    }

    /**
     * The case the class gate exists for. DAYS -> HOURS is an exact x24 by
     * {@link ChronoUnit#getDuration()}, so arithmetic alone would accept it — but a calendar day is
     * not always 24 hours, and {@code FunctionValidator.checkIntervalComparison} refuses to order
     * across that boundary. The lattice must agree, or the two would contradict each other.
     */
    @Test
    void intervalUnitsDoNotScaleAcrossClasses() {
        assertFalse(EncodingLattice.losslessTargets(iv(ChronoUnit.DAYS)).contains(iv(ChronoUnit.HOURS)),
                "a calendar day is not 24 hours — DAYS must not reach the clock class");
        assertFalse(EncodingLattice.losslessTargets(iv(ChronoUnit.MONTHS)).contains(iv(ChronoUnit.DAYS)),
                "a month has no fixed number of days");
        assertThrows(IllegalArgumentException.class,
                () -> EncodingLattice.convertSql("x", iv(ChronoUnit.DAYS), iv(ChronoUnit.HOURS)));
    }

    /** Coarser to finer only: the reverse would truncate. */
    @Test
    void intervalUnitsDoNotScaleToACoarserUnit() {
        assertFalse(EncodingLattice.losslessTargets(iv(ChronoUnit.MILLIS)).contains(iv(ChronoUnit.SECONDS)));
    }

    @Test
    void scaledEncodingsAlignOnTheFinerScale() {
        ScaledTypeEncoding cents = new ScaledTypeEncoding(2);
        ScaledTypeEncoding tenThousandths = new ScaledTypeEncoding(4);
        assertTrue(EncodingLattice.losslessTargets(cents).contains(tenThousandths));
        assertFalse(EncodingLattice.losslessTargets(tenThousandths).contains(cents),
                "reducing the scale drops digits");
        assertEquals("(x) * 100", EncodingLattice.convertSql("x", cents, tenThousandths));
        assertThrows(IllegalArgumentException.class,
                () -> EncodingLattice.convertSql("x", tenThousandths, cents));
    }
}
