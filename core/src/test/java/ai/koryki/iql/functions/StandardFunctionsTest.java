package ai.koryki.iql.functions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Aggregate classification is driven by FunctionDefinition metadata — the single source of truth. */
class StandardFunctionsTest {

    @Test
    void aggregatesAreClassifiedAsAggregate() {
        for (String agg : new String[]{"count", "sum", "avg", "min", "max", "string_agg"}) {
            assertTrue(StandardFunctions.isAggregate(agg), agg + " should be an aggregate");
        }
    }

    @Test
    void scalarsAndUnknownsAreNotAggregate() {
        assertFalse(StandardFunctions.isAggregate("substr"));
        assertFalse(StandardFunctions.isAggregate("length"));
        assertFalse(StandardFunctions.isAggregate("coalesce"));
        assertFalse(StandardFunctions.isAggregate("does_not_exist"));
    }
}
