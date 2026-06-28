package ai.koryki.iql.functions;

import ai.koryki.antlr.KorykiaiException;
import ai.koryki.catalog.schema.types.CoreTypeFamily;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ai.koryki.iql.functions.FunctionArg.arg;
import static ai.koryki.iql.functions.FunctionArg.optionalArg;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionRegistryTest {

    @Test
    void signatureLessRegistrationReplacesByName() {
        FunctionRegistry r = new FunctionRegistry();
        FunctionDefinition first = new FunctionDefinition("position", ReturnTypes.INTEGER);
        FunctionDefinition second = new FunctionDefinition("position", ReturnTypes.INTEGER);
        r.register(first).register(second);

        assertEquals(1, r.overloads("position").size());
        assertSame(second, r.lookup("position"));
    }

    @Test
    void overloadsWithDisjointArityCoexistAndResolveByArgCount() {
        FunctionRegistry r = new FunctionRegistry();
        FunctionDefinition epoch = new FunctionDefinition("to_timestamp", ReturnTypes.TIMESTAMP)
                .args(arg("epoch"));
        FunctionDefinition text = new FunctionDefinition("to_timestamp", ReturnTypes.TIMESTAMP)
                .args(arg("text"), arg("format"));
        r.register(epoch).register(text);

        assertEquals(2, r.overloads("to_timestamp").size());
        assertSame(epoch, r.lookup("to_timestamp", 1));
        assertSame(text, r.lookup("to_timestamp", 2));
    }

    @Test
    void overlappingAritySupersedesExistingOverload() {
        FunctionRegistry r = new FunctionRegistry();
        FunctionDefinition twoArg = new FunctionDefinition("substr", ReturnTypes.TEXT)
                .args(arg("string"), arg("start"));
        FunctionDefinition twoOrThree = new FunctionDefinition("substr", ReturnTypes.TEXT)
                .args(arg("string"), arg("start"), optionalArg("length"));
        r.register(twoArg).register(twoOrThree);

        assertEquals(1, r.overloads("substr").size());
        assertSame(twoOrThree, r.lookup("substr", 2));
    }

    @Test
    void sameArityOverloadsWithDistinctFamiliesCoexistAndResolveByFamily() {
        FunctionRegistry r = new FunctionRegistry();
        FunctionDefinition fromInt = new FunctionDefinition("to_text", ReturnTypes.TEXT)
                .args(arg("value", CoreTypeFamily.INTEGER));
        FunctionDefinition fromDouble = new FunctionDefinition("to_text", ReturnTypes.TEXT)
                .args(arg("value", CoreTypeFamily.FLOAT));
        r.register(fromInt).register(fromDouble);

        // type-aware collides: same arity, disjoint families -> both survive
        assertEquals(2, r.overloads("to_text").size());

        // resolution picks the overload whose declared family matches the call's argument family
        assertSame(fromInt,    r.lookup("to_text", 1, () -> List.of(CoreTypeFamily.INTEGER)));
        assertSame(fromDouble, r.lookup("to_text", 1, () -> List.of(CoreTypeFamily.FLOAT)));
    }

    @Test
    void mixedKindOverloadsAreRejected() {
        FunctionRegistry r = new FunctionRegistry();
        r.register(new FunctionDefinition("f", ReturnTypes.TEXT, FunctionKind.AGGREGATE).args(arg("a")));

        FunctionDefinition scalar = new FunctionDefinition("f", ReturnTypes.TEXT, FunctionKind.SCALAR)
                .args(arg("a"), arg("b"));
        assertThrows(KorykiaiException.class, () -> r.register(scalar));
    }

    @Test
    void fullReplacementMayChangeKind() {
        FunctionRegistry r = new FunctionRegistry();
        r.register(new FunctionDefinition("g", ReturnTypes.TEXT, FunctionKind.SCALAR));
        r.register(new FunctionDefinition("g", ReturnTypes.TEXT, FunctionKind.AGGREGATE));

        assertEquals(FunctionKind.AGGREGATE, r.lookup("g").getKind());
    }

    @Test
    void unknownNameResolvesToNull() {
        FunctionRegistry r = new FunctionRegistry();
        assertNull(r.lookup("nope"));
        assertNull(r.lookup("nope", 2));
        assertTrue(r.overloads("nope").isEmpty());
    }

    @Test
    void signatureArity() {
        FunctionSignature s = FunctionSignature.of(arg("string"), arg("start"), optionalArg("length"));
        assertEquals(2, s.minArgs());
        assertEquals(3, s.maxArgs());
        assertTrue(s.matchesArity(2));
        assertTrue(s.matchesArity(3));
        assertFalse(s.matchesArity(1));
        assertFalse(s.matchesArity(4));

        FunctionSignature v = FunctionSignature.ofVariadic(arg("value"));
        assertEquals(1, v.minArgs());
        assertTrue(v.matchesArity(7));
        assertFalse(v.matchesArity(0));
    }

    @Test
    void signatureValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> FunctionSignature.of(optionalArg("a"), arg("b")));
        assertThrows(IllegalArgumentException.class,
                () -> FunctionSignature.ofVariadic(arg("a"), optionalArg("b")));
        assertThrows(IllegalArgumentException.class,
                () -> FunctionSignature.ofVariadic());
    }

    @Test
    void signatureToString() {
        assertEquals("(string, start [, length])",
                FunctionSignature.of(arg("string"), arg("start"), optionalArg("length")).toString());
        assertEquals("(value, ...)",
                FunctionSignature.ofVariadic(arg("value")).toString());
    }

    @Test
    void overrideInheritsKindAndSignatureFromBase() {
        FunctionRegistry r = new FunctionRegistry();
        r.register(new FunctionDefinition("string_agg", ReturnTypes.TEXT, FunctionKind.AGGREGATE)
                .args(arg("value"), arg("separator")));

        r.override("string_agg", "LISTAGG({0}, {1})");

        FunctionDefinition overlay = r.lookup("string_agg");
        assertEquals(FunctionKind.AGGREGATE, overlay.getKind());
        assertEquals("(value, separator)", overlay.getSignature().toString());
        assertEquals("LISTAGG({0}, {1})", overlay.getTemplate().toString());
        assertEquals(1, r.overloads("string_agg").size());
    }

    @Test
    void overrideOfUnknownFunctionIsRejected() {
        FunctionRegistry r = new FunctionRegistry();
        assertThrows(KorykiaiException.class, () -> r.override("nope", "NOPE({0})"));
    }

    @Test
    void overrideOfOverloadedNameRequiresArity() {
        FunctionRegistry r = new FunctionRegistry();
        r.register(new FunctionDefinition("to_timestamp", ReturnTypes.TIMESTAMP).args(arg("value")));
        r.register(new FunctionDefinition("to_timestamp", ReturnTypes.TIMESTAMP).args(arg("value"), arg("format")));

        assertThrows(KorykiaiException.class, () -> r.override("to_timestamp", "TS({*})"));

        r.override("to_timestamp", 2, "TO_TIMESTAMP_TZ({0}, {1})");
        assertEquals("TO_TIMESTAMP_TZ({0}, {1})", r.lookup("to_timestamp", 2).getTemplate().toString());
        assertNull(r.lookup("to_timestamp", 1).getTemplate());
    }

    @Test
    void unsupportedFunctionFailsAtRenderTime() {
        FunctionRegistry r = new FunctionRegistry();
        r.register(new FunctionDefinition("median", ReturnTypes.FLOAT, FunctionKind.AGGREGATE));
        r.unsupported("median");

        FunctionDefinition overlay = r.lookup("median");
        assertTrue(overlay.isUnsupported());
        assertEquals(FunctionKind.AGGREGATE, overlay.getKind());
        assertThrows(UnsupportedOperationException.class, () -> overlay.render(null, null, 0));
    }

    @Test
    void arityOverlapDetection() {
        FunctionSignature oneOrTwo = FunctionSignature.of(arg("a"), optionalArg("b"));
        FunctionSignature two = FunctionSignature.of(arg("a"), arg("b"));
        FunctionSignature three = FunctionSignature.of(arg("a"), arg("b"), arg("c"));

        assertTrue(oneOrTwo.overlapsArity(two));
        assertFalse(oneOrTwo.overlapsArity(three));
        assertFalse(two.overlapsArity(three));
    }
}
