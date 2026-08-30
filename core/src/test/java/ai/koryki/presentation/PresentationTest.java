package ai.koryki.presentation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What a presentation is and how it renders — the half of the question that needs no query.
 *
 * <p>Who <em>concludes</em> which presentation a column gets is the job of {@code ai.koryki.result},
 * and its tests live in that project. What stays here is what this package owns on its own: that a
 * presentation survives the trip through its own name, and that each one renders what it promises.
 *
 * <p>The round trip is the load-bearing one. It is the promise the catalog annotation rests on —
 * without it, {@code "presentation": "DECIMALS:2"} in a {@code db.json} is a string nobody checks.
 */
public class PresentationTest {

    // ---- the registry --------------------------------------------------------------------

    @Test
    void everyPresentationRoundTripsThroughItsName() {
        for (Presentation p : List.of(new DecimalsPresentation(0), new DecimalsPresentation(2),
                new SignificantPresentation(3), new SignificantPresentation(4),
                new ScalePresentation(1_000_000L, "M"), PlainPresentation.INSTANCE)) {
            assertEquals(p, PresentationRegistry.ofNullable(p.name()), p.name());
        }
    }

    /** An unknown name is an error, not a silent null: a typo must not read as "none wanted". */
    @Test
    void anUnknownNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PresentationRegistry.ofNullable("NONSENSE:9"));
        assertNull(PresentationRegistry.ofNullable(null));
    }

    // ---- the rendering -------------------------------------------------------------------

    /** Canonical mode (null locale) is plain and deterministic; a locale adds its separators. */
    @Test
    void decimalsRenderToAFixedNumberOfPlaces() {
        Presentation p = new DecimalsPresentation(2);
        assertEquals("1234.50", p.render(new BigDecimal("1234.5"), null));
        assertEquals("1234.57", p.render(new BigDecimal("1234.5678"), null));
        assertEquals("1.234,57", p.render(new BigDecimal("1234.5678"), Locale.GERMANY));
        assertEquals("57", new DecimalsPresentation(0).render(new BigDecimal("56.7"), null));
    }

    /**
     * Significant digits keep the accuracy wherever the decimal point sits — the reason measured
     * quantities are not rounded to fixed places, which would wipe the second case out to 0.00.
     */
    @Test
    void significantDigitsSurviveTheDecimalPoint() {
        Presentation p = new SignificantPresentation(4);
        assertEquals("1235", p.render(new BigDecimal("1234.5678"), null));
        assertEquals("0.001235", p.render(new BigDecimal("0.00123456"), null));
        assertEquals("0.150", new SignificantPresentation(3).render(new BigDecimal("0.15"), null));
    }

    /** A scaled number reads short and says which scale it is on. */
    @Test
    void aScaleShortensAndNamesItself() {
        ScalePresentation p = new ScalePresentation(1_000_000L, "M");
        assertEquals("57.00 M", p.render(new BigDecimal("57000000"), null));
        assertEquals("1.23 M", p.render(new BigDecimal("1234567"), null));
        assertEquals("0.00 M", p.render(new BigDecimal("3"), null));
        assertEquals("1,23 M", p.render(new BigDecimal("1234567"), Locale.GERMANY));
    }

    /** A number that is a name gets no separator, and reads the same in every locale. */
    @Test
    void aPlainNumberIsNotGrouped() {
        assertEquals("10248", PlainPresentation.INSTANCE.render(10248, Locale.GERMANY));
        assertEquals("10248", PlainPresentation.INSTANCE.render(10248, null));
    }

    /** A rule that cannot apply declines, so the formatter falls back instead of failing. */
    @Test
    void aPresentationDeclinesWhatItCannotRender() {
        assertNull(new DecimalsPresentation(2).render("hello", null));
        assertNull(new SignificantPresentation(4).render(LocalDate.of(2023, 1, 31), null));
        assertNull(new ScalePresentation(1_000_000L, "M").render("hello", null));
    }
}
