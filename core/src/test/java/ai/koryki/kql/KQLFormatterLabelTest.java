package ai.koryki.kql;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.IQLSerializer;
import ai.koryki.iql.LinkResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code FETCH} label must survive being written back out.
 *
 * <p>{@code fetchItem : expression (h=ID (label=STRING)?)?} — the alias names the column, the label
 * is the display string for a UI. {@code KQLFormatter.toOut} rendered the expression, the alias and
 * the sort direction, and dropped the label.
 *
 * <p>Nothing caught it, and the reason is worth keeping: the round-trip in
 * {@code KqlTranspilerTest} re-formats the query, re-transpiles it and compares the <em>SQL</em>. A
 * label does not reach SQL, so losing it changes nothing the comparison looks at. No fixture used a
 * label either, so the whole feature was carried along unread and unchecked.
 *
 * <p>The IQL side did keep it ({@code IQLSerializer}), which is what makes the gap a bug rather than
 * a decision: the two serialisers disagreed about the same query.
 */
public class KQLFormatterLabelTest {

    private static LinkResolver resolver;

    @BeforeAll
    public static void readNorthwindDB() {
        resolver = NorthwindService.resolver();
    }

    private static String reformat(String kql) {
        KQLTranspiler t = KQLTranspiler.builder(kql, resolver).build();
        return new KQLFormatter(t.getCtx(), t.getDescription()).format();
    }

    /** The label is part of the query; writing the query back out must not lose it. */
    @Test
    void aLabelSurvivesTheKqlRoundTrip() {
        String kql = "FIND orders o\nFETCH o.freight cost \"Shipping cost\"";
        assertTrue(reformat(kql).contains("\"Shipping cost\""),
                () -> "the label was dropped:\n" + reformat(kql));
    }

    /** And the second pass must produce the same text as the first — otherwise it is not stable. */
    @Test
    void reformattingIsStable() {
        String once = reformat("FIND orders o\nFETCH o.freight cost \"Shipping cost\"");
        assertEquals(once, reformat(once));
    }

    /** A label rides along with a sort direction, which is rendered after it. */
    @Test
    void aLabelSurvivesNextToASortDirection() {
        String out = reformat("FIND orders o\nFETCH o.freight cost \"Shipping cost\" DESC");
        assertTrue(out.contains("\"Shipping cost\""), () -> out);
        assertTrue(out.contains("DESC"), () -> out);
    }

    /**
     * A label may contain a quote, and the reader must not see the escape that carried it.
     *
     * <p>{@code STRING : '"' ('\\"' | .)*? '"'} allows it, and the mapper used to strip only the
     * outer pair - so the backslashes travelled into the column heading the label exists to
     * provide. Unescaping alone would have been worse than the bug: the serialiser wraps the value
     * in quotes without escaping, so a bare quote closed the string early and the query stopped
     * parsing. Both halves move together, and the two assertions below are the two halves.
     */
    @Test
    void anEscapedQuoteInALabelIsUnescapedForTheReaderAndReescapedForTheSource() {
        String kql = "FIND orders o\nFETCH o.freight cost \"The \\\"best\\\" seller\"";
        KQLTranspiler t = KQLTranspiler.builder(kql, resolver).build();

        // THE assertion. The serialised forms below look identical before and after the fix -
        // an escape that is never removed and never added round-trips just as well as one that is
        // removed and put back. What tells them apart is the value in between, which is the one a
        // column heading is drawn from.
        assertEquals("The \"best\" seller",
                t.getQuery().getSet().getSelect().getStart().getOut().get(0).getLabel());
        assertTrue(new IQLSerializer(t.getQuery()).toString().contains("\"The \\\"best\\\" seller\""),
                () -> "IQL lost the escape:\n" + new IQLSerializer(t.getQuery()));
        assertTrue(reformat(kql).contains("\"The \\\"best\\\" seller\""),
                () -> "KQL lost the escape:\n" + reformat(kql));

        // and it is still stable, which is what makes the pair a pair
        assertEquals(reformat(kql), reformat(reformat(kql)));
    }

    /** The IQL serialiser already kept it; both directions must agree on the same query. */
    @Test
    void bothSerialisersKeepIt() {
        KQLTranspiler t = KQLTranspiler.builder(
                "FIND orders o\nFETCH o.freight cost \"Shipping cost\"", resolver).build();
        assertTrue(new IQLSerializer(t.getQuery()).toString().contains("\"Shipping cost\""),
                "IQL already kept it");
        assertTrue(new KQLFormatter(t.getCtx(), t.getDescription()).format().contains("\"Shipping cost\""),
                "KQL must keep it too");
    }
}
