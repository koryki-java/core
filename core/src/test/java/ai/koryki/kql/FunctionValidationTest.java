package ai.koryki.kql;

import ai.koryki.catalog.schema.types.CoreTypeFamily;
import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dialect-aware function validation: arity against the dialect's overload sets, unsupported markers. */
public class FunctionValidationTest {

    private static LinkResolver resolver;

    @BeforeAll
    public static void readNorthwindDB() throws IOException {
        resolver = NorthwindService.resolver();
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
                v.get(0).getMessage());
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
        assertEquals("function 'random' is not supported by this dialect", v.get(0).getMessage());
    }

    @Test
    void ambiguousTypedOverloadCallWithNullArgIsReportedAsViolation() {
        KQLTranspiler t = KQLTranspiler.builder(
                "FIND customers c FETCH c.company_name, to_text(NULL)", resolver).functions(toTextIntOrDouble()).build();

        List<Violation> v = t.violations();
        assertEquals(1, v.size());
        assertTrue(v.get(0).getMessage().startsWith("ambiguous call to 'to_text': argument 1 is untyped (NULL)"),
                v.get(0).getMessage());
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
        assertTrue(t.violations().isEmpty());
    }

    @Test
    void outOfOrderDurationIsReportedAsViolation() {
        KQLTranspiler t = transpiler("FIND orders o FETCH o.order_date + 4h2d");
        List<Violation> v = t.violations();
        assertEquals(1, v.size());
        assertTrue(v.get(0).getMessage().contains("largest-unit-first") && v.get(0).getMessage().contains("'2d4h'"),
                v.get(0).getMessage());
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
        assertTrue(v.get(0).getMessage().contains("INTERVAL add DATE/TIMESTAMP is not valid"),
                v.get(0).getMessage());
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
        assertTrue(v.get(0).getMessage().contains("INTERVAL minus DATE/TIMESTAMP is not valid"),
                v.get(0).getMessage());
    }

    @Test
    void conditionalBranchesOfDifferentFamilyGroupsReportedAsViolation() {
        KQLTranspiler t = transpiler("FIND customers c FETCH coalesce(c.company_name, 1)");
        List<Violation> v = t.violations();
        assertEquals(1, v.size());
        assertTrue(v.get(0).getMessage().contains("different family-groups")
                        && v.get(0).getMessage().contains("TEXT") && v.get(0).getMessage().contains("INTEGER"),
                v.get(0).getMessage());
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

    private static KQLTranspiler transpiler(String kql) {
        FunctionRenderer functions = DuckdbBaseDialect.INSTANCE.getFunctionRenderer();
        return KQLTranspiler.builder(kql, resolver).functions(functions).build();
    }
}
