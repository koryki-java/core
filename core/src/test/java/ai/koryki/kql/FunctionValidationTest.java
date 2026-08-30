package ai.koryki.kql;

import ai.koryki.iql.IQLTranspiler;
import ai.koryki.antlr.KorykiaiException;
import ai.koryki.antlr.RangeException;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.SqlQueryRenderer;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.functions.FunctionDefinition;
import ai.koryki.iql.functions.FunctionRegistry;
import ai.koryki.iql.functions.FunctionRenderer;
import ai.koryki.iql.functions.ReturnTypes;
import ai.koryki.iql.functions.StandardFunctions;
import ai.koryki.iql.validate.Violation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static ai.koryki.iql.functions.FunctionArg.arg;
import ai.koryki.iql.validate.PlaceholderValidator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dialect-aware function validation: arity against the dialect's overload sets, unsupported markers. */
public class FunctionValidationTest {

    private static final FunctionRenderer FUNCTIONS = DuckdbBaseDialect.INSTANCE.getFunctionRenderer();

    private static LinkResolver resolver;

    @BeforeAll
    public static void readNorthwindDB() throws IOException {
        resolver = NorthwindService.resolver();
    }

    /**
     * An alias that cannot be resolved used to surface as a bare {@code NullPointerException} from
     * {@code KQLQueryMapper.iterateLinks}. The mapper cannot build a tree without it, so it still
     * fails fast — but with a positioned, readable message.
     */
    @Test
    void unknownAliasFailsWithAPositionedMessage() {
        RangeException e = assertThrows(RangeException.class,
                () -> transpiler("FIND orders o, zz order_details od FETCH o.order_id").violations());
        assertTrue(e.getMessage().contains("unknown alias 'zz'"), e.getMessage());
    }

    /**
     * Re-using an alias is the back-reference syntax (see the cycle_1 fixture), but only for the same
     * table. Rebinding it to a different one silently resolved to the first source; now it is an error.
     */
    @Test
    void aliasReboundToADifferentTableIsAmbiguous() {
        RangeException e = assertThrows(RangeException.class,
                () -> transpiler("FIND customers c, c orders o, o products c FETCH o.order_id").violations());
        assertTrue(e.getMessage().contains("ambiguous alias 'c'")
                && e.getMessage().contains("customers") && e.getMessage().contains("products"),
                e.getMessage());
    }

    /**
     * Both sides of an explicit join are checked, and both are reported.
     *
     * <p>The columns used to be resolved while rendering, where the first unknown one threw and the
     * run stopped — a query with two mistakes revealed them one per attempt. They are violations
     * now, of the same category an unknown column anywhere else produces, so the author sees the
     * whole list at once and in the same form.
     */
    @Test
    void bothSidesOfAnExplicitJoinAreReported() {
        List<Violation> v = transpiler(
                "FIND employee_territories a, [no_such_left=no_such_right] employee_territories b "
                        + "FETCH a.employee_id").violations();
        assertEquals(2, v.size(), v.toString());
        assertTrue(v.stream().allMatch(x -> Violation.UNKNOWN_COLUMN.equals(x.getCategory())), v.toString());
        assertTrue(v.stream().anyMatch(x -> x.getMessage().contains("'no_such_left'")), v.toString());
        assertTrue(v.stream().anyMatch(x -> x.getMessage().contains("'no_such_right'")), v.toString());
    }

    @Test
    void aliasBackReferenceToTheSameTableIsValid() {
        // the cycle_1 shape: c is re-used, but names the same table, so it is a back-reference
        assertTrue(transpiler("FIND customers c, c orders o, o customers c FETCH o.order_id")
                .violations().isEmpty());
    }

    /**
     * A field naming a column the entity does not have used to pass validation and then blow up in
     * {@code ExpressionTypeResolver.resolveField} on a bare {@code Optional.get()}, as
     * {@code NoSuchElementException: No value present} — no position, no column name. It is the most
     * common failure the shared corpus produces, because a fixture written against one dialect's
     * schema meets it on every dialect whose schema lacks the column.
     */
    @Test
    void anUnknownColumnIsReportedWithItsNameAndPosition() {
        List<Violation> v = transpiler("FIND customers c FETCH c.does_not_exist").violations();
        assertEquals(1, v.size());
        assertEquals("unknown column 'does_not_exist' on customers", v.getFirst().getMessage());
        assertEquals(Violation.UNKNOWN_COLUMN, v.getFirst().getCategory());
    }

    /**
     * An alias bound in more than one place resolves per <em>scope</em>, so the column check applies
     * there like anywhere else. It could not while {@code SchemaValidator} kept one flat alias map
     * filled as sources were visited: the same alias in two scopes kept whichever came last, and
     * fields are visited before the sources written after them. The check had to skip such aliases
     * to avoid calling a valid column unknown; now it reports the typo instead of staying silent.
     */
    @Test
    void aColumnIsCheckedEvenBehindAnAliasBoundTwice() {
        assertTrue(transpiler("FIND customers c, c orders o, o customers c FETCH o.freight")
                .violations().isEmpty(), "the valid column must still pass");

        List<Violation> v = transpiler(
                "FIND customers c, c orders o, o customers c FETCH o.tippfehler").violations();
        assertEquals(1, v.size());
        assertEquals("unknown column 'tippfehler' on orders", v.getFirst().getMessage());
    }

    /** freight is a column of orders, not of customers — the alias decides which entity is asked. */
    @Test
    void theAliasDecidesWhichEntityTheColumnIsLookedUpOn() {
        List<Violation> v = transpiler("FIND customers c, c orders o FETCH c.freight").violations();
        assertEquals(1, v.size());
        assertEquals("unknown column 'freight' on customers", v.getFirst().getMessage());
    }

    @Test
    void validCallProducesNoViolations() {
        KQLTranspiler t = transpiler("FIND customers c FETCH c.company_name, position('x', c.company_name)");
        assertTrue(t.violations().isEmpty());
    }

    @Test
    void arityMismatchIsReportedAsViolation() {
        KQLTranspiler t = transpiler("FIND customers c FETCH position(c.company_name)");

        List<Violation> v = t.violations();
        assertEquals(1, v.size());
        assertEquals("no overload of 'position' matches 1 argument(s) — candidates: position(substr, str)",
                v.getFirst().getMessage());
    }

    /**
     * KQL keywords are upper case (the lexer defines them as literals, and {@code ID} is lower-case
     * only), so {@code distinct} does not reach the {@code DISTINCT} alternative of the
     * {@code operator} rule — it lands in the {@code custom=ID} escape hatch. It then rendered the
     * ANSI {@code IS NOT DISTINCT FROM} anyway, because {@code negatedOperatorTemplate} normalises
     * its argument while the MariaDB and Oracle overrides compare exactly; both engines reject that
     * form. A case-only difference from a real operator names the correct spelling instead.
     */
    @Test
    void anOperatorSpelledInTheWrongCaseIsAnError() {
        KQLTranspiler t = transpiler(
                "FIND customers c, c orders o FILTER NOT c.region distinct o.ship_region FETCH c.customer_id");

        List<Violation> v = t.violations();
        assertEquals(1, v.size());
        assertEquals("'distinct' is not a KQL operator — write it as 'DISTINCT'; KQL keywords are upper case",
                v.getFirst().getMessage());
        assertTrue(v.getFirst().isError(), "a mistyped operator must be fatal, not advisory");
    }

    /** The escape hatch itself is unchanged: a genuinely foreign operator still only warns. */
    @Test
    void anUnknownOperatorIsStillOnlyAWarning() {
        KQLTranspiler t = transpiler(
                "FIND customers c FILTER c.country wobbly ('UK') FETCH c.customer_id");

        List<Violation> v = t.violations();
        assertEquals(1, v.size());
        assertTrue(v.getFirst().getMessage().startsWith("'wobbly' is not a KQL operator; it is passed through"),
                v.getFirst().getMessage());
        assertFalse(v.getFirst().isError(), v.getFirst().getMessage());
    }

    @Test
    void withoutDialectCatalogArityIsNotChecked() {
        KQLTranspiler t = KQLTranspiler.builder("FIND customers c FETCH position(c.company_name)", resolver).build();
        assertTrue(t.violations().isEmpty());
    }

    @Test
    void unsupportedFunctionIsReportedAsViolation() {
        FunctionRegistry registry = StandardFunctions.registry();
        registry.unsupported("random");

        KQLTranspiler t = KQLTranspiler.builder("FIND customers c FETCH c.company_name, random()", resolver).functions(registry).build();

        List<Violation> v = t.violations();
        assertEquals(1, v.size());
        assertEquals("function 'random' is not supported by this dialect", v.getFirst().getMessage());
        assertTrue(v.getFirst().isUnsupported(),
                "the unsupported violation must be distinguishable by category, not by message text");
    }

    /**
     * The counterpart of {@link #unsupportedFunctionIsReportedAsViolation}. A dialect can declare
     * that a function has no OVER form — MySQL's GROUP_CONCAT, LISTAGG on Oracle and Snowflake, and
     * T-SQL's STRING_AGG — but only {@code FunctionDefinition.render} acted on that, throwing a bare
     * {@code UnsupportedOperationException} with no position. It never reached
     * {@code ValidateException.isOnlyUnsupported}, so a shared fixture could not be skipped
     * automatically and carried a hand-written {@code ignore=} marker instead — which then
     * suppressed the SQL check along with it.
     */
    @Test
    void windowUseOfAWindowUnsupportedFunctionIsReportedAsViolation() {
        FunctionRegistry registry = StandardFunctions.registry();
        registry.windowUnsupported("string_agg");

        KQLTranspiler t = KQLTranspiler.builder(
                "FIND orders o FETCH string_agg(o.ship_city, ',') OVER (PARTITION o.customer_id) cities",
                resolver).functions(registry).build();

        List<Violation> v = t.violations();
        assertEquals(1, v.size());
        assertEquals("function 'string_agg' does not support an OVER clause in this dialect",
                v.getFirst().getMessage());
        assertTrue(v.getFirst().isUnsupported(),
                "the harness skips a shared fixture on this category — a plain error would fail it");
    }

    /**
     * A dialect can lack one <em>arity</em> of a function: MariaDB has {@code trim(s)} but no
     * set-based {@code trim(s, chars)}. Saying so used to be impossible — the dialect left a
     * one-argument template in place and the two-argument call died in {@code SqlTemplate}'s
     * surplus guard, with no position and no UNSUPPORTED category. Note that the supported overload
     * registers first, so the check must look at the overload the <em>call</em> selects.
     */
    @Test
    void anUnsupportedArityIsReportedWhileTheOthersStillWork() {
        FunctionRegistry registry = StandardFunctions.registry();
        registry.override("trim", "TRIM({0})");
        registry.unsupported("trim", 2);

        assertTrue(KQLTranspiler.builder("FIND customers c FETCH trim(c.company_name) a", resolver)
                .functions(registry).build().violations().isEmpty(), "one argument still renders");

        List<Violation> v = KQLTranspiler.builder(
                "FIND customers c FETCH trim(c.company_name, 'ab') a", resolver)
                .functions(registry).build().violations();
        assertEquals(1, v.size());
        assertEquals("function 'trim' is not supported by this dialect with 2 argument(s)",
                v.getFirst().getMessage());
        assertTrue(v.getFirst().isUnsupported(), v.getFirst().getMessage());
    }

    /** An arity nobody declares is still an arity error, not an unsupported one. */
    @Test
    void anArityNoOverloadCoversIsStillAnArityViolation() {
        FunctionRegistry registry = StandardFunctions.registry();
        registry.unsupported("trim", 2);

        List<Violation> v = KQLTranspiler.builder(
                "FIND customers c FETCH trim(c.company_name, 'ab', 'x') a", resolver)
                .functions(registry).build().violations();
        assertEquals(1, v.size());
        assertTrue(v.getFirst().getMessage().startsWith("no overload of 'trim' matches 3 argument(s)"),
                v.getFirst().getMessage());
        assertFalse(v.getFirst().isUnsupported(), v.getFirst().getMessage());
    }

    /** Without the OVER clause the same declaration must not fire: only the window form is barred. */
    @Test
    void plainUseOfAWindowUnsupportedFunctionIsFine() {
        FunctionRegistry registry = StandardFunctions.registry();
        registry.windowUnsupported("string_agg");

        KQLTranspiler t = KQLTranspiler.builder(
                "FIND orders o FETCH o.customer_id, string_agg(o.ship_city, ',') cities",
                resolver).functions(registry).build();

        assertTrue(t.violations().isEmpty(), String.valueOf(t.violations()));
    }

    @Test
    void ambiguousTypedOverloadCallWithNullArgIsReportedAsViolation() {
        KQLTranspiler t = KQLTranspiler.builder(
                "FIND customers c FETCH c.company_name, to_text(NULL)", resolver).functions(toTextIntOrDouble()).build();

        List<Violation> v = t.violations();
        assertEquals(1, v.size());
        assertTrue(v.getFirst().getMessage().startsWith("ambiguous call to 'to_text': argument 1 is untyped (NULL)"),
                v.getFirst().getMessage());
    }

    @Test
    void typedArgumentSelectsAnOverloadWithoutAmbiguityViolation() {
        // a non-NULL argument resolves to a concrete family, so the call is unambiguous
        KQLTranspiler t = KQLTranspiler.builder("FIND customers c FETCH to_text(c.company_name)", resolver).functions(toTextIntOrDouble()).build();
        assertTrue(t.violations().isEmpty());
    }

    /** Catalog with two same-arity, distinct-family overloads of {@code to_text} (int vs double). */
    private static FunctionRegistry toTextIntOrDouble() {
        FunctionRegistry registry = StandardFunctions.registry();
        registry.register(new FunctionDefinition("to_text", ReturnTypes.TEXT).args(arg("value", CoreTypeFamily.INTEGER)));
        registry.register(new FunctionDefinition("to_text", ReturnTypes.TEXT).args(arg("value", CoreTypeFamily.FLOAT)));
        return registry;
    }

    @Test
    void unknownFunctionNamesPassThrough() {
        KQLTranspiler t = transpiler("FIND customers c FETCH c.company_name, my_native_fn(c.company_name)");
        // still valid — the escape hatch survives; the name reaches SQL verbatim
        assertTrue(t.errors().isEmpty());
        assertTrue(t.getSql(new SqlQueryRenderer(DuckdbBaseDialect.INSTANCE, java.time.ZoneId.of("UTC")))
                .contains("my_native_fn("));
        // but it is no longer silent
        assertEquals(1, t.warnings().size());
        assertTrue(t.warnings().getFirst().getMessage().contains("my_native_fn")
                        && t.warnings().getFirst().getMessage().contains("passed through"),
                t.warnings().toString());
    }

    /**
     * Operators live in the same catalog tables as functions, and must not be offered: they cannot
     * be written as a call at all. {@code in}/{@code is null}/{@code and} are one edit from several
     * real names, so this would fire if the filter were missing.
     */
    @Test
    void anOperatorIsNeverOfferedAsAFunction() {
        List<String> names = FUNCTIONS.names();
        assertFalse(names.isEmpty(), "the catalog must know its own calls");
        for (String operator : List.of("=", "<", "AND", "OR", "NOT", "IN", "BETWEEN", "IS NULL")) {
            assertFalse(names.contains(operator), () -> operator + " is an operator, not a call");
        }
        assertTrue(names.contains("upper"), () -> "a plain call must be there: " + names);
        assertEquals(names.stream().distinct().sorted().toList(), names,
                "sorted and without repeats, so a message cannot flicker");
    }

    @Test
    void outOfOrderDurationIsReportedAsViolation() {
        KQLTranspiler t = transpiler("FIND orders o FETCH o.order_date + 4h2d");
        List<Violation> v = t.violations();
        assertEquals(1, v.size());
        assertTrue(v.getFirst().getMessage().contains("largest-unit-first") && v.getFirst().getMessage().contains("'2d4h'"),
                v.getFirst().getMessage());
    }

    @Test
    void largestFirstDurationProducesNoViolation() {
        assertTrue(transpiler("FIND orders o FETCH o.order_date + 2d4h").violations().isEmpty());
    }

    @Test
    void intervalPlusDateIsReportedAsViolation() {
        KQLTranspiler t = transpiler("FIND orders o FETCH 2d + o.order_date");
        List<Violation> v = t.violations();
        assertEquals(1, v.size());
        assertTrue(v.getFirst().getMessage().contains("INTERVAL add DATE/TIMESTAMP is not valid"),
                v.getFirst().getMessage());
    }

    @Test
    void datePlusIntervalProducesNoViolation() {
        assertTrue(transpiler("FIND orders o FETCH o.order_date + 2d").violations().isEmpty());
    }

    @Test
    void intervalMinusDateIsReportedAsViolation() {
        KQLTranspiler t = transpiler("FIND orders o FETCH 2d - o.order_date");
        List<Violation> v = t.violations();
        assertEquals(1, v.size());
        assertTrue(v.getFirst().getMessage().contains("INTERVAL minus DATE/TIMESTAMP is not valid"),
                v.getFirst().getMessage());
    }

    @Test
    void conditionalBranchesOfDifferentFamilyGroupsReportedAsViolation() {
        KQLTranspiler t = transpiler("FIND customers c FETCH coalesce(c.company_name, 1)");
        List<Violation> v = t.violations();
        assertEquals(1, v.size());
        assertTrue(v.getFirst().getMessage().contains("different family-groups")
                        && v.getFirst().getMessage().contains("TEXT") && v.getFirst().getMessage().contains("INTEGER"),
                v.getFirst().getMessage());
    }

    @Test
    void conditionalBranchesOfSameFamilyProduceNoViolation() {
        assertTrue(transpiler("FIND customers c FETCH coalesce(c.company_name, c.contact_name)").violations().isEmpty());
    }

    @Test
    void logicalArgumentInNonConditionPositionIsReportedAsViolation() {
        KQLTranspiler t = transpiler("FIND orders o FETCH coalesce(o.freight > 0, o.freight)");
        assertTrue(t.violations().stream().anyMatch(v -> v.getMessage().contains("only valid as a condition")),
                t.violations().toString());
    }

    @Test
    void nestedFunctionInsideConditionIsValidated() {
        // proves AST traversal descends into a logical-expression argument (the case condition):
        // the arity error on the nested position() call is only reachable if the walker enters the condition
        KQLTranspiler t = transpiler("FIND orders o FETCH case(position(o.ship_city) > 0, o.ship_city, o.ship_country)");
        assertTrue(t.violations().stream().anyMatch(v -> v.getMessage().contains("position")), t.violations().toString());
    }

    @Test
    void aggregateInsideConditionIsDetectedByScalarAggregateHeuristic() {
        // the aggregate (sum) lives in the condition; mixed with scalar branch columns -> invalid
        // aggregation. Only detected if the heuristic descends into the logical condition.
        KQLTranspiler t = transpiler("FIND orders o FETCH case(sum(o.freight) > 0, o.ship_city, o.ship_country)");
        assertTrue(t.violations().stream().anyMatch(v -> v.getMessage().contains("invalid aggregation")),
                t.violations().toString());
    }

    @Test
    void logicalConditionsInCaseAreValid() {
        assertTrue(transpiler("FIND orders o FETCH case(o.freight > 0, o.ship_city, o.ship_country)").violations().isEmpty());
    }

    @Test
    void logicalAtCaseResultPositionIsReportedAsViolation() {
        KQLTranspiler t = transpiler("FIND orders o FETCH case(o.freight > 0, o.ship_city > 'x', o.ship_country)");
        assertTrue(t.violations().stream().anyMatch(v -> v.getMessage().contains("only valid as a condition")),
                t.violations().toString());
    }

    @Test
    void calendarDistanceAsProjectionIsValid() {
        KQLTranspiler t = transpiler("FIND orders o FETCH calendar_distance(o.order_date, o.shipped_date)");
        assertTrue(noProjectionOnlyViolation(t), () -> "unexpected projection-only violation: " + t.violations());
    }

    @Test
    void calendarDistanceInFilterIsRejected() {
        KQLTranspiler t = transpiler(
                "FIND orders o FILTER calendar_distance(o.order_date, o.shipped_date) > 1y FETCH o.order_id");
        assertTrue(hasProjectionOnlyViolation(t), () -> "expected projection-only violation, got: " + t.violations());
    }

    @Test
    void calendarDistanceAsFunctionArgumentIsRejected() {
        KQLTranspiler t = transpiler(
                "FIND orders o FETCH calendar_distance(o.order_date, o.shipped_date) + 1d");
        assertTrue(hasProjectionOnlyViolation(t), () -> "expected projection-only violation, got: " + t.violations());
    }

    private static boolean hasProjectionOnlyViolation(KQLTranspiler t) {
        return t.violations().stream().anyMatch(v -> v.getMessage().contains("projection-only"));
    }

    private static boolean noProjectionOnlyViolation(KQLTranspiler t) {
        return t.violations().stream().noneMatch(v -> v.getMessage().contains("projection-only"));
    }

    /**
     * A literal zero divisor is decidable at write time and can never be intended, so it is an error
     * rather than the NULL a runtime zero produces. A column divisor is left alone — whether it holds
     * a zero is not knowable here, and the renderer guards it with NULLIF.
     */
    @Test
    void literalZeroDivisorIsRejected() {
        List<Violation> violations = transpiler("FIND orders o FETCH o.freight / 0 x").violations();
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("division by zero")),
                violations.toString());
    }

    @Test
    void nonZeroLiteralAndColumnDivisorsAreAccepted() {
        for (String kql : List.of("FIND orders o FETCH o.freight / 2 x",
                "FIND orders o FETCH o.freight / o.order_id x",
                "FIND orders o FETCH o.freight / (o.order_id - o.order_id) x")) {
            assertTrue(transpiler(kql).violations().stream()
                            .noneMatch(v -> v.getMessage().contains("division by zero")),
                    kql);
        }
    }

    /**
     * Every grammatical position a placeholder may take, and what each produced before
     * {@code PlaceholderValidator} existed. The fixtures under {@code validation/} assert only that
     * the query is rejected; these assert <em>which</em> violation rejects it, because two of the
     * four were already rejected — one by an internal message about a SQL template, one by an NPE —
     * and a fixture cannot tell those apart from the rule meant to catch them.
     */
    @Test
    void everyPlaceholderFormIsReportedAsAnUnboundPlaceholder() {
        for (String kql : List.of(
                "FIND orders o FILTER o.freight > #x FETCH o.order_id",
                "FIND orders o FILTER o.freight #x FETCH o.order_id",
                "FIND customers c FILTER c.country IN #laender FETCH c.company_name",
                "WITH ord #p FIND ord o FETCH o.order_id")) {
            List<Violation> v = transpiler(kql).violations();
            assertEquals(1, v.size(), kql + " -> " + v);
            assertEquals(PlaceholderValidator.PLACEHOLDER, v.getFirst().getCategory(), kql);
            assertTrue(v.getFirst().getMessage().contains("before the query is rendered"), v.getFirst().getMessage());
            assertNotNull(v.getFirst().getRange(), "the violation must be positioned: " + kql);
        }
    }

    /**
     * The placeholder check runs before the scope check, and this is what that buys: a block bound
     * to a placeholder leaves its id unresolvable, so the scope collector would report
     * "unknown block 'ord'" — the symptom, at the wrong place in the query.
     */
    @Test
    void aPlaceholderBlockIsReportedAtTheBlockNotAtItsUse() {
        List<Violation> v = transpiler("WITH ord #p FIND ord o FETCH o.order_id").violations();
        assertEquals(1, v.size(), v.toString());
        assertTrue(v.getFirst().getMessage().contains("block 'ord'"), v.getFirst().getMessage());
        assertTrue(v.getFirst().getMessage().contains("'#p'"), v.getFirst().getMessage());
    }

    /**
     * What a placeholder is made of, pinned on both front ends at once.
     *
     * <p>{@code PLACEHOLDER: HASHMARK (LOWER | DIGIT)+} — at least one character after the hash. The
     * {@code +} was a {@code *} until 2026-08-07, and the two spellings behave identically today:
     * {@code HASHMARK} is declared before {@code PLACEHOLDER} in both grammars, so a lone {@code #}
     * matched both at length 1 and ANTLR gave it to the earlier rule. Measured: reverting both
     * grammars to {@code *} fails no test.
     *
     * <p>So this does not pin the {@code +} — nothing can, while the two agree. It pins the language
     * surface the {@code +} makes independent of declaration order: reordering the token rules under
     * a {@code *} would silently turn {@code #} into a placeholder, and that lands here.
     *
     * <p>Both front ends, because the grammars are separate files and only a test keeps them saying
     * the same thing. IQL is where a serialized query re-enters the pipeline.
     */
    @Test
    void aPlaceholderNeedsAtLeastOneCharacterAfterTheHash() {
        for (String name : List.of("#x", "#1", "#a1")) {
            List<Violation> v = transpiler(
                    "FIND orders o FILTER o.freight > " + name + " FETCH o.order_id").violations();
            assertEquals(1, v.size(), name + " -> " + v);
            assertEquals(PlaceholderValidator.PLACEHOLDER, v.getFirst().getCategory(), name);

            List<Violation> iql = IQLTranspiler.builder(
                    "SELECT\n orders o\n  OUT o.order_id 1\n  FILTER\n    o.freight > " + name + "\n",
                    resolver).build().violations();
            assertEquals(1, iql.size(), "IQL " + name + " -> " + iql);
            assertEquals(PlaceholderValidator.PLACEHOLDER, iql.getFirst().getCategory(), "IQL " + name);
        }

        // A bare '#' is not a placeholder: it lexes as HASHMARK, which no parser rule accepts.
        assertThrows(KorykiaiException.class,
                () -> transpiler("FIND orders o FILTER o.freight > # FETCH o.order_id").violations(),
                "a bare # must not be a placeholder");
        assertThrows(KorykiaiException.class,
                () -> IQLTranspiler.builder(
                        "SELECT\n orders o\n  OUT o.order_id 1\n  FILTER\n    o.freight > #\n",
                        resolver).build().violations(),
                "IQL: a bare # must not be a placeholder");
    }

    /** A query without placeholders must not acquire one — the rule only fires on a real hole. */
    @Test
    void aQueryWithoutPlaceholdersIsUnaffected() {
        assertTrue(transpiler("FIND orders o FILTER o.freight > 100 FETCH o.order_id")
                .violations().stream()
                .noneMatch(v -> PlaceholderValidator.PLACEHOLDER.equals(v.getCategory())));
    }

    private static KQLTranspiler transpiler(String kql) {
        return KQLTranspiler.builder(kql, resolver).functions(FUNCTIONS).build();
    }
}
