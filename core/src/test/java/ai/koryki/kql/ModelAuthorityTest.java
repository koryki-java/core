package ai.koryki.kql;

import ai.koryki.databases.northwind.duckdb.NorthwindService;
import ai.koryki.iql.DuckdbBaseDialect;
import ai.koryki.iql.LinkResolver;
import ai.koryki.iql.functions.FunctionRenderer;
import ai.koryki.iql.validate.Violation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * model.json is the language, and a rejected name says what to write instead.
 *
 * <p>KQL used to accept any physical column of the underlying table as well as the model's
 * attributes, which made db.json a second, undocumented vocabulary: a query could name a foreign
 * key the model deliberately hides behind a link and be told nothing, right up until it ran on a
 * dialect whose schema spelled it differently.
 */
public class ModelAuthorityTest {

    private static final FunctionRenderer FUNCTIONS = DuckdbBaseDialect.INSTANCE.getFunctionRenderer();

    private static LinkResolver en;
    private static LinkResolver de;

    @BeforeAll
    public static void readNorthwindDB() throws IOException {
        en = NorthwindService.resolver(Locale.ENGLISH);
        de = NorthwindService.resolver(Locale.GERMAN);
    }

    /**
     * {@code employee_id} is a real column of the orders table and deliberately not an attribute of
     * the orders entity — it is reached through the same_employee link. Before, this validated.
     */
    @Test
    void aPhysicalColumnTheModelDoesNotExposeIsAnError() {
        List<Violation> v = errors("FIND orders o FETCH o.employee_id", en);
        assertEquals(1, v.size(), () -> "expected exactly one violation: " + v);
        assertEquals(Violation.UNKNOWN_COLUMN, v.getFirst().getCategory());
        assertTrue(v.getFirst().getMessage().startsWith("unknown column 'employee_id' on orders"),
                () -> v.getFirst().getMessage());
    }

    /** The attributes the model does expose keep working, suggestions and all. */
    @Test
    void anAttributeTheModelExposesStillValidates() {
        assertEquals(List.of(), errors("FIND orders o FETCH o.order_id, o.freight, o.customer_id", en));
    }

    /** A misspelt column names the entity's own attributes, and nothing wider. */
    @Test
    void aMisspeltColumnIsToldWhichAttributeWasMeant() {
        List<Violation> v = errors("FIND customers c FETCH c.company_nmae", en);
        assertEquals(1, v.size(), v::toString);
        assertEquals(List.of("company_name"), v.getFirst().getDidYouMean());
        assertEquals("unknown column 'company_nmae' on customers — did you mean 'company_name'?",
                v.getFirst().getMessage());
    }

    /**
     * Writing a name in another convention is still an error — it just says which spelling wins.
     *
     * <p>Only the separator case can be tested through the language: a KQL identifier is
     * lowercase-only, so {@code orderId} and {@code ORDER_ID} never reach validation at all — the
     * lexer rejects them first. Normalization still folds case, which costs nothing and covers the
     * names that arrive quoted or through another front end.
     */
    @Test
    void aDifferentSpellingConventionIsAnErrorWithTheModelSpelling() {
        List<Violation> v = errors("FIND orders o FETCH o.orderid", en);
        assertEquals(1, v.size(), v::toString);
        assertEquals(List.of("order_id"), v.getFirst().getDidYouMean());
        assertEquals("unknown column 'orderid' on orders — did you mean 'order_id'?",
                v.getFirst().getMessage());
    }

    /**
     * The other door into db.json's vocabulary: an attribute's physical {@code column} override.
     * Every attribute of the German model declares one, so this is where it would show.
     */
    @Test
    void theGermanModelRejectsTheEnglishColumnSpelling() {
        List<Violation> v = errors("FIND bestellungen o FETCH o.order_id", de);
        assertEquals(1, v.size(), v::toString);
        assertEquals(Violation.UNKNOWN_COLUMN, v.getFirst().getCategory());
        assertEquals(List.of("bestell_id"), v.getFirst().getDidYouMean());

        assertEquals(List.of(), errors("FIND bestellungen o FETCH o.bestell_id", de));
    }

    /** An unknown entity is offered the entities, and the source is named — it used not to be. */
    @Test
    void anUnknownSourceNamesItselfAndSuggests() {
        List<Violation> v = errors("FIND custmers c FETCH c.company_name", en);
        Violation source = v.stream().filter(x -> x.getMessage().startsWith("invalid source"))
                .findFirst().orElseThrow(() -> new AssertionError(v.toString()));
        assertEquals("invalid source 'custmers' — did you mean 'customers'?", source.getMessage());
    }

    /** An explicit join column list is held to the same rule as any other field. */
    @Test
    void anExplicitJoinColumnMustAlsoBeAModelAttribute() {
        List<Violation> v = errors(
                "FIND orders o, o [employee_id = employee_id] employees e FETCH o.order_id", en);
        assertTrue(v.stream().anyMatch(x -> Violation.UNKNOWN_COLUMN.equals(x.getCategory())
                        && x.getMessage().contains("employee_id")),
                () -> "the hidden foreign key must be rejected here too: " + v);
    }

    /** A misspelt function is offered the calls the dialect knows — and only the calls. */
    @Test
    void aMisspeltFunctionIsToldWhichCallWasMeant() {
        List<Violation> v = warnings("FIND orders o FETCH upperr(o.ship_city)", en);
        assertEquals(1, v.size(), v::toString);
        assertEquals(List.of("upper"), v.getFirst().getDidYouMean());
        assertTrue(v.getFirst().getMessage().startsWith("'upperr' is not a KQL function"),
                () -> v.getFirst().getMessage());
        assertTrue(v.getFirst().getMessage().endsWith("— did you mean 'upper'?"),
                () -> v.getFirst().getMessage());
    }

    private static List<Violation> warnings(String kql, LinkResolver resolver) {
        return KQLTranspiler.builder(kql, resolver).functions(FUNCTIONS).build().warnings();
    }

    private static List<Violation> errors(String kql, LinkResolver resolver) {
        return KQLTranspiler.builder(kql, resolver).functions(FUNCTIONS).build().errors();
    }
}
