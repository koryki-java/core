package ai.koryki.kql;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.IQLSerializer;
import ai.koryki.iql.IQLTranspiler;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.functions.FunctionRenderer;
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
 * A predicate after a comparison must not disappear.
 *
 * <p>The {@code unary_logical_expression} rule allowed the pair form — meant for
 * {@code BETWEEN x AND y} — after <em>every</em> operator. Because a bare expression may be a
 * predicate, {@code a > 1 AND b} was ambiguous; ANTLR took the pair alternative, {@code b} became
 * a third operand of {@code >}, and its template {@code "{0} > {1}"} never renders a third one.
 * The predicate was dropped — no violation, no warning, and right or wrong depending on the order
 * the operands happened to be written in.
 *
 * <p>That order is therefore the point of these tests: the same logical condition came out whole
 * one way round and mutilated the other. Testing one direction proves half of it.
 *
 * <p>PostgreSQL has the same ambiguity and also resolves it in the grammar, see
 * <a href="https://www.postgresql.org/docs/current/functions-comparison.html">functions-comparison</a>.
 */
class BarePredicateAfterOperatorTest {

    private static LinkResolver resolver;

    @BeforeAll
    static void readNorthwindDB() throws IOException {
        resolver = NorthwindService.resolver();
    }

    /** The reported case: the bare predicate stands after the comparison. */
    @Test
    void predicateAfterAComparisonSurvives() {
        String sql = sql("FIND products p FILTER p.unit_price > 10 AND p.discontinued");
        assertTrue(sql.contains("p.unit_price > 10"), sql);
        assertTrue(sql.contains("p.discontinued <> 0"), () -> "the second predicate is missing:\n" + sql);
    }

    /**
     * The other direction — it was always right. It is here because only comparing the two forms
     * shows that the same condition no longer depends on the order.
     */
    @Test
    void theSameConditionInTheOtherOrderIsIdentical() {
        String forward = where(sql("FIND products p FILTER p.unit_price > 10 AND p.discontinued"));
        String reversed = where(sql("FIND products p FILTER p.discontinued AND p.unit_price > 10"));

        assertTrue(forward.contains("p.unit_price > 10") && forward.contains("p.discontinued <> 0"), forward);
        assertTrue(reversed.contains("p.unit_price > 10") && reversed.contains("p.discontinued <> 0"), reversed);
    }

    /** The middle predicate of a three-link chain was dropped. */
    @Test
    void theMiddlePredicateOfAChainSurvives() {
        String sql = sql("FIND products p FILTER p.unit_price > 10 AND p.discontinued AND p.units_in_stock > 5");
        assertTrue(sql.contains("p.unit_price > 10"), sql);
        assertTrue(sql.contains("p.discontinued <> 0"), () -> "the middle predicate is missing:\n" + sql);
        assertTrue(sql.contains("p.units_in_stock > 5"), sql);
    }

    /**
     * Parentheses did not help: {@code (p.discontinued)} is itself an {@code expression} and so
     * fit the pair alternative just as well.
     */
    @Test
    void parenthesesAroundThePredicateAlsoSurvive() {
        String sql = sql("FIND products p FILTER p.unit_price > 10 AND (p.discontinued)");
        assertTrue(sql.contains("p.unit_price > 10"), sql);
        assertTrue(sql.contains("p.discontinued <> 0"), () -> "the parenthesized predicate is missing:\n" + sql);
    }

    /** BETWEEN keeps its pair form — that was the reason for the alternative and has to hold. */
    @Test
    void betweenKeepsItsPair() {
        assertTrue(where(sql("FIND products p FILTER p.unit_price BETWEEN 5 AND 10")).contains("BETWEEN 5 AND 10"));
    }

    /**
     * Where the two meet: BETWEEN's {@code AND} binds tighter than the logical one, so the
     * predicate that follows no longer belongs to the range. Exactly the reading PostgreSQL documents.
     */
    @Test
    void betweenFollowedByAPredicateKeepsBoth() {
        String sql = sql("FIND products p FILTER p.unit_price BETWEEN 5 AND 10 AND p.discontinued");
        assertTrue(sql.contains("BETWEEN 5 AND 10"), sql);
        assertTrue(sql.contains("p.discontinued <> 0"), () -> "the predicate after BETWEEN is missing:\n" + sql);
    }

    /**
     * A compound bound needs <em>no</em> parentheses. PostgreSQL advises them because there the
     * expression after BETWEEN is restricted to a narrower class ({@code b_expr}). KQL's
     * {@code expression} contains no logical AND, so the ambiguity never arises at all — the advice
     * does not apply here, and the documentation must not repeat it.
     */
    @Test
    void aCompoundBoundNeedsNoParentheses() {
        assertTrue(where(sql("FIND products p FILTER p.unit_price BETWEEN 1 + 2 AND 5"))
                .contains("BETWEEN 1 + 2 AND 5"),
                () -> where(sql("FIND products p FILTER p.unit_price BETWEEN 1 + 2 AND 5")));
    }

    /**
     * A non-boolean expression in this position vanished silently as well — and escaped the type
     * check too, because {@code checkBarePredicate} requires a missing operator. It now arrives as
     * a predicate of its own and is checked.
     */
    @Test
    void aNonBooleanPredicateIsNowReported() {
        List<Violation> violations = transpiler("FIND products p FILTER p.unit_price > 10 AND p.product_name").violations();

        assertFalse(violations.isEmpty(), "the non-boolean expression is not reported");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("yes/no test")),
                violations.toString());
    }

    /**
     * BETWEEN is no longer a member of the {@code operator} rule, so a single bound is a parse
     * error. Before, it ran all the way into the renderer and ended there with an internal message
     * about a template — at a place that tells the query's author nothing.
     */
    @Test
    void betweenWithASingleBoundIsARejectedAtParseTime() {
        RuntimeException e = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> sql("FIND products p FILTER p.unit_price BETWEEN 5"));
        assertFalse(e.getMessage() != null && e.getMessage().contains("template"),
                () -> "should fail at the parser, not in the renderer: " + e.getMessage());
    }

    /**
     * The round trip: the same change sits in both grammars, and the IQL form must produce
     * byte-identical SQL. Had only {@code KQLRules.g4} been touched, the IQL path would still read
     * it wrongly — and unnoticed, because it produces the same plausible SQL.
     */
    @Test
    void theSameQueryThroughIqlProducesIdenticalSql() {
        for (String kql : List.of(
                "FIND products p FILTER p.unit_price > 10 AND p.discontinued",
                "FIND products p FILTER p.unit_price BETWEEN 5 AND 10",
                "FIND products p FILTER p.unit_price BETWEEN 5 AND 10 AND p.discontinued")) {

            KQLTranspiler kqlTranspiler = transpiler(kql);
            String viaKql = kqlTranspiler.getSql(renderer());
            String iql = new IQLSerializer(kqlTranspiler.getQuery()).toString();
            String viaIql = IQLTranspiler.builder(iql, resolver).build().getSql(renderer());

            assertEquals(viaKql, viaIql, () -> "Roundtrip weicht ab fuer: " + kql + "\nIQL:\n" + iql);
        }
    }

    private static String sql(String kql) {
        return transpiler(kql).getSql(renderer());
    }

    private static SqlQueryRenderer renderer() {
        return new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, ZoneId.of("UTC"));
    }

    /** The WHERE part only — the assertions should not hang on FETCH columns. */
    private static String where(String sql) {
        int i = sql.indexOf("WHERE");
        return i < 0 ? "" : sql.substring(i);
    }

    private static KQLTranspiler transpiler(String kql) {
        FunctionRenderer functions = DuckdbBaseDialect.INSTANCE.getFunctionRenderer();
        return KQLTranspiler.builder(kql, resolver).functions(functions).build();
    }
}
