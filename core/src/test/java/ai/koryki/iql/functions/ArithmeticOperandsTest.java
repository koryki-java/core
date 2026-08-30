package ai.koryki.iql.functions;

import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.Families;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arithmetic operators take {@link Families#ARITHMETIC}, not {@link Families#ANY}: multiplying
 * two BLOBs or negating a UUID is not something SQL agrees on, so the catalog stops claiming it can.
 *
 * <p>The companion of {@code OrderedOperandsTest}, and pinned at the same level — the catalog — since
 * no model in the test corpus has a BLOB or JSON column to write a query against.
 */
class ArithmeticOperandsTest {

    private static final String[] OPERATORS = {"negate", "add", "minus", "multiply", "divide"};

    /** The families arithmetic is actually defined for, per docs/TEMPORAL.md. */
    private static final CoreTypeFamily[] ARITHMETIC = {
            CoreTypeFamily.INTEGER, CoreTypeFamily.DECIMAL, CoreTypeFamily.FLOAT,
            CoreTypeFamily.DATE, CoreTypeFamily.TIME, CoreTypeFamily.TIMESTAMP,
            CoreTypeFamily.INTERVAL};

    /** Excluded on purpose — TEXT included, because {@code +} is not concatenation in KQL. */
    private static final CoreTypeFamily[] NOT_ARITHMETIC = {
            CoreTypeFamily.TEXT, CoreTypeFamily.BOOLEAN, CoreTypeFamily.BLOB,
            CoreTypeFamily.JSON, CoreTypeFamily.UUID};

    @Test
    void arithmeticFamilyIsNumericTemporalAndInterval() {
        for (CoreTypeFamily f : ARITHMETIC) {
            assertTrue(Families.ARITHMETIC.accepts(f), f + " should be arithmetic");
        }
        for (CoreTypeFamily f : NOT_ARITHMETIC) {
            assertFalse(Families.ARITHMETIC.accepts(f), f + " should not be arithmetic");
            assertTrue(Families.ANY.accepts(f), f + " should still be comparable for equality");
        }
        assertEquals(ARITHMETIC.length + NOT_ARITHMETIC.length, CoreTypeFamily.values().length,
                "every family should be classified by this test");
    }

    @Test
    void everyOperandOfEveryOperatorIsDeclaredArithmetic() {
        FunctionRegistry registry = StandardFunctions.registry();
        for (String op : OPERATORS) {
            FunctionSignature signature = signatureOf(registry, op);
            for (int i = 0; i < signature.args().size(); i++) {
                assertEquals(Families.ARITHMETIC, signature.familyAt(i),
                        "operand " + (i + 1) + " of '" + op + "'");
            }
        }
    }

    @Test
    void textAndBlobOperandsAreRejectedByEveryOperator() {
        FunctionRegistry registry = StandardFunctions.registry();
        for (String op : OPERATORS) {
            FunctionSignature signature = signatureOf(registry, op);
            for (CoreTypeFamily f : NOT_ARITHMETIC) {
                assertFalse(signature.familyAt(0).accepts(f), op + " should reject a " + f + " operand");
            }
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
