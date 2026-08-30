package ai.koryki.kql;

import ai.koryki.antlr.RangeException;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code CheckOuterJoinFilterRule} — the guard that keeps an optional join optional.
 *
 * <p>A filter naming exactly one source is moved into the join's ON clause by
 * {@code PushLogicalExpressionRule}. One spanning two sources has no single table to move to, so it
 * would stay in the WHERE, where a predicate on the null-extended side of a LEFT JOIN silently
 * discards the unmatched rows — turning the optional join into a required one. That is refused.
 *
 * <p>The rule had no test before this one; only its message was ever observed, and only by hitting it.
 */
public class OuterJoinFilterTest {

    private static LinkResolver resolver;

    @BeforeAll
    static void readNorthwindDB() throws IOException {
        resolver = NorthwindService.resolver();
    }

    private static String sql(String kql) {
        return KQLTranspiler.builder(kql, resolver).build()
                .getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC")));
    }

    @Test
    void crossEntityPredicateOnAnOptionalJoinIsRejected() {
        String kql = "FIND customers c, c+orders o FILTER c.city = o.ship_city FETCH c.company_name";
        RangeException e = assertThrows(RangeException.class, () -> sql(kql));

        // names the offending condition, not just the alias
        assertTrue(e.getMessage().contains("c.city = o.ship_city"), e.getMessage());
        // names the table in the query's own vocabulary
        assertTrue(e.getMessage().contains("orders o"), e.getMessage());
        // says what to do about it
        assertTrue(e.getMessage().contains("make the join required"), e.getMessage());
        // and points at the condition rather than the whole query
        assertNotNull(e.getRange(), "the failure must carry a position");
        // Columns are 1-based (Position); indexOf is 0-based, hence the +1. The query is one line,
        // so the offset into it is the column.
        assertEquals(kql.indexOf("c.city") + 1, e.getRange().getStart().getPos());
    }

    /** With several conjuncts, the position must be the offending one — not the first, nor the whole filter. */
    @Test
    void thePositionIsTheOffendingConjunct() {
        String kql = "FIND customers c, c+orders o "
                + "FILTER o.freight > 10 AND c.city = o.ship_city FETCH c.company_name";
        RangeException e = assertThrows(RangeException.class, () -> sql(kql));

        // Position.getPos() is the 1-based column, indexOf the 0-based offset — hence the +1
        assertEquals(kql.indexOf("c.city = o.ship_city") + 1, e.getRange().getStart().getPos(),
                e.getMessage());
    }

    /** Operands that are function calls are named too, so the quoted condition stays recognisable. */
    @Test
    void functionOperandsAreNamedInTheMessage() {
        RangeException e = assertThrows(RangeException.class, () -> sql(
                "FIND customers c, c+orders o FILTER length(o.ship_city) > length(c.city) "
                        + "FETCH c.company_name"));
        assertTrue(e.getMessage().contains("length(o.ship_city) > length(c.city)"), e.getMessage());
    }

    /** The single-alias case is what the rule protects: it must still reach the ON clause. */
    @Test
    void singleAliasPredicateIsPushedIntoTheJoin() {
        String sql = sql("FIND customers c, c+orders o FILTER o.freight > 10 "
                + "FETCH c.company_name, count(o)");
        assertTrue(sql.contains("LEFT OUTER JOIN"), sql);
        assertTrue(sql.contains("o.freight > 10"), sql);
        assertTrue(!sql.contains("WHERE"), "the filter belongs in ON, not WHERE:\n" + sql);
    }

    /** A predicate on the required side does not endanger the outer join, and stays in WHERE. */
    @Test
    void predicateOnTheRequiredSideStaysInWhere() {
        String sql = sql("FIND customers c, c+orders o FILTER c.city = 'Berlin' "
                + "FETCH c.company_name, count(o)");
        assertTrue(sql.contains("LEFT OUTER JOIN"), sql);
        assertTrue(sql.contains("WHERE"), sql);
    }

    /** The same cross-entity predicate is perfectly fine on a required join — WHERE and ON agree there. */
    @Test
    void crossEntityPredicateOnARequiredJoinIsAccepted() {
        String sql = sql("FIND customers c, c orders o FILTER c.city = o.ship_city "
                + "FETCH c.company_name");
        assertTrue(sql.contains("INNER JOIN"), sql);
        assertTrue(sql.contains("c.city = o.ship_city"), sql);
    }
}
