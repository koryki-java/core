package ai.koryki.iql.functions;

import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.Families;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ordered comparisons take {@link Families#ORDERED}, not {@link Families#ANY}: BLOB and JSON
 * have no order that survives a change of dialect, so {@code a < b} on one is refused by the
 * validator instead of failing at the database. Equality is unaffected and stays polymorphic.
 *
 * <p>No model in the test corpus has a BLOB or JSON column, so this pins the catalog declarations
 * directly rather than through a KQL query — which is also the level the rule lives at.
 */
class OrderedOperandsTest {

    private static final String[] ORDERED_OPERATORS = {"<", "<=", ">", ">=", "BETWEEN"};
    private static final String[] EQUALITY_OPERATORS = {"=", "<>", "IN", "DISTINCT"};

    @Test
    void orderedFamilyIsAnyWithoutBlobAndJson() {
        for (CoreTypeFamily f : CoreTypeFamily.values()) {
            boolean orderable = f != CoreTypeFamily.BLOB && f != CoreTypeFamily.JSON;
            assertEquals(orderable, Families.ORDERED.accepts(f),
                    f + " should " + (orderable ? "" : "not ") + "be orderable");
            assertTrue(Families.ANY.accepts(f), f + " should still be comparable for equality");
        }
    }

    @Test
    void everyOperandOfAnOrderedOperatorIsDeclaredOrdered() {
        FunctionRegistry registry = StandardFunctions.registry();
        for (String op : ORDERED_OPERATORS) {
            FunctionSignature signature = signatureOf(registry, op);
            for (int i = 0; i < signature.args().size(); i++) {
                assertEquals(Families.ORDERED, signature.familyAt(i),
                        "operand " + (i + 1) + " of '" + op + "'");
            }
        }
    }

    /** Equality keeps the wildcard — a BLOB may be compared for equality, just not put in order. */
    @Test
    void equalityOperatorsStillAcceptAnyFamily() {
        FunctionRegistry registry = StandardFunctions.registry();
        for (String op : EQUALITY_OPERATORS) {
            FunctionSignature signature = signatureOf(registry, op);
            for (int i = 0; i < signature.args().size(); i++) {
                assertTrue(signature.familyAt(i).accepts(CoreTypeFamily.BLOB),
                        "operand " + (i + 1) + " of '" + op + "' should accept BLOB");
            }
        }
    }

    /** The rule is only worth having if the declared family is what rejects a BLOB operand. */
    @Test
    void aBlobIsRejectedByTheDeclaredFamilyOfEveryOrderedOperator() {
        FunctionRegistry registry = StandardFunctions.registry();
        for (String op : ORDERED_OPERATORS) {
            FunctionSignature signature = signatureOf(registry, op);
            assertFalse(signature.familyAt(0).accepts(CoreTypeFamily.BLOB), op + " with a BLOB left operand");
            assertFalse(signature.familyAt(0).accepts(CoreTypeFamily.JSON), op + " with a JSON left operand");
        }
    }

    private static FunctionSignature signatureOf(FunctionRegistry registry, String op) {
        var overloads = registry.overloads(op);
        assertFalse(overloads.isEmpty(), "no catalog entry for operator '" + op + "'");
        FunctionSignature signature = overloads.get(0).getSignature();
        assertNotNull(signature, "operator '" + op + "' has no signature");
        return signature;
    }
}
