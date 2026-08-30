package ai.koryki.iql.validate;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Suggest} on its own — no catalog, no database.
 *
 * <p>The interesting cases are all about what must <em>not</em> be suggested. A wrong suggestion is
 * worse than none, because it reads as authoritative.
 */
class SuggestTest {

    private static final List<String> ORDERS =
            List.of("order_id", "order_date", "required_date", "shipped_date", "freight",
                    "ship_name", "ship_city", "ship_country");

    /**
     * The reason for Damerau over plain Levenshtein: two adjacent keys struck in the wrong order is
     * one mistake, not two. Plain Levenshtein charges 2 and the name drops past the threshold.
     */
    @Test
    void aTranspositionCostsOneEditNotTwo() {
        assertEquals(1, Suggest.distance("nmae", "name"));
        assertEquals(1, Suggest.distance("oredr_id", "order_id"));
        assertEquals(List.of("order_id"), Suggest.closest("oredr_id", ORDERS));
    }

    /** A substitution, an insertion and a deletion still cost what they always did. */
    @Test
    void theOrdinaryEditsAreUnchanged() {
        assertEquals(0, Suggest.distance("name", "name"));
        assertEquals(1, Suggest.distance("name", "nome"));
        assertEquals(1, Suggest.distance("nae", "name"));
        assertEquals(1, Suggest.distance("namee", "name"));
        assertEquals(4, Suggest.distance("", "name"));
    }

    /**
     * Case and separators are spelling, not identity: every convention for the same name measures
     * as the same name, so the model's spelling is always the first thing offered.
     */
    @Test
    void spellingConventionsCollapseToDistanceZero() {
        for (String written : List.of("orderId", "OrderID", "ORDER_ID", "order-id", "orderid")) {
            assertEquals("orderid", Suggest.normalize(written), written);
            assertEquals(List.of("order_id"), Suggest.closest(written, ORDERS), written);
        }
    }

    /**
     * The defect in the bound this replaces: it floored at two, and no two 2-character strings are
     * more than two edits apart — so every short name matched every other short name.
     */
    @Test
    void aShortNameDoesNotMatchAnUnrelatedShortName() {
        assertEquals(2, Suggest.distance("id", "no"));
        assertEquals(List.of(), Suggest.closest("id", List.of("no", "ok", "up")));
        // one edit is still a typo at that length
        assertEquals(List.of("no"), Suggest.closest("nx", List.of("no", "ok", "up")));
    }

    /** A name that resembles nothing gets nothing — silence beats a confident wrong answer. */
    @Test
    void anUnrelatedNameSuggestsNothing() {
        assertEquals(List.of(), Suggest.closest("quantity", ORDERS));
        assertEquals(List.of(), Suggest.closest("zzzzzzzz", ORDERS));
    }

    /** Nearest first, ties by name so a golden pinned to the message cannot flicker. */
    @Test
    void suggestionsAreNearestFirstAndCappedAtThree() {
        List<String> many = List.of("ship_city", "ship_name", "ship_country", "ship_region", "ship_via");
        List<String> got = Suggest.closest("ship_cty", many);
        assertEquals("ship_city", got.get(0));
        assertTrue(got.size() <= 3, () -> "at most three: " + got);

        // equal distance, alphabetical
        assertEquals(List.of("aaa_x", "aaa_y"), Suggest.closest("aaa_z", List.of("aaa_y", "aaa_x")));
    }

    @Test
    void nothingToCompareGivesNoSuggestions() {
        assertEquals(List.of(), Suggest.closest(null, ORDERS));
        assertEquals(List.of(), Suggest.closest("  ", ORDERS));
        assertEquals(List.of(), Suggest.closest("___", ORDERS));
        assertEquals(List.of(), Suggest.closest("order_id", null));
        assertEquals(List.of(), Suggest.closest("order_id", List.of()));
    }

    /** The hint carries its own separator, so a caller with nothing to say appends nothing. */
    @Test
    void theHintReadsAsASentenceOrIsAbsent() {
        assertEquals("", Suggest.hint(List.of()));
        assertEquals("", Suggest.hint(null));
        assertEquals(" — did you mean 'order_id'?", Suggest.hint(List.of("order_id")));
        assertEquals(" — did you mean 'a' or 'b'?", Suggest.hint(List.of("a", "b")));
        assertEquals(" — did you mean 'a', 'b' or 'c'?", Suggest.hint(List.of("a", "b", "c")));
    }
}
