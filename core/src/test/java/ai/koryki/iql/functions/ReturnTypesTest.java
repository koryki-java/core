package ai.koryki.iql.functions;

import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeNames;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.query.Function;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Phase 3 — DECIMAL multiplication precision propagation, with fallback to LEAST_RESTRICTIVE. */
class ReturnTypesTest {

    private static TypeDescriptor decimal(int p, int s) {
        return new TypeDescriptor(TypeNames.TYPE_DECIMAL, null, CoreTypeFamily.DECIMAL, p, s);
    }

    /** A FunctionBinding whose i-th operand resolves to operandTypes[i]. */
    private static FunctionBinding binding(TypeDescriptor... operandTypes) {
        Function f = new Function();
        f.setFunc("multiply");
        List<Expression> args = new ArrayList<>();
        Map<Expression, TypeDescriptor> types = new IdentityHashMap<>();
        for (TypeDescriptor t : operandTypes) {
            Expression e = new Expression();
            args.add(e);
            types.put(e, t);
        }
        f.setArguments(args);
        return new FunctionBinding(f, types::get);
    }

    @Test
    void multiplyOfTwoDecimals() {
        TypeDescriptor r = ReturnTypes.DECIMAL_MULTIPLY.infer(binding(decimal(18, 6), decimal(18, 6)));
        assertEquals(CoreTypeFamily.DECIMAL, r.getTypeFamily());
        assertEquals(37, r.getPrecision());   // p1 + p2 + 1
        assertEquals(12, r.getScale());        // s1 + s2
    }

    @Test
    void multiplyFoldsAcrossThreeDecimals() {
        TypeDescriptor r = ReturnTypes.DECIMAL_MULTIPLY.infer(binding(decimal(5, 2), decimal(5, 2), decimal(5, 2)));
        assertEquals(17, r.getPrecision());    // (5+5+1) + 5 + 1
        assertEquals(6, r.getScale());         // 2 + 2 + 2
    }

    @Test
    void fallsBackForIntegerOperands() {
        TypeDescriptor r = ReturnTypes.DECIMAL_MULTIPLY.infer(binding(TypeDescriptor.INTEGER, TypeDescriptor.INTEGER));
        assertEquals(CoreTypeFamily.INTEGER, r.getTypeFamily());
    }

    @Test
    void fallsBackWhenAnyDecimalLacksPrecision() {
        // TypeDescriptor.DECIMAL constant has precision/scale = -1 -> not eligible -> LEAST_RESTRICTIVE
        TypeDescriptor r = ReturnTypes.DECIMAL_MULTIPLY.infer(binding(decimal(18, 6), TypeDescriptor.DECIMAL));
        assertEquals(CoreTypeFamily.DECIMAL, r.getTypeFamily());
        assertEquals(18, r.getPrecision());    // operand(0), NOT the 37 the multiply rule would give
    }

    @Test
    void addOfTwoDecimals() {
        TypeDescriptor r = ReturnTypes.DECIMAL_ADD.infer(binding(decimal(18, 6), decimal(18, 6)));
        assertEquals(CoreTypeFamily.DECIMAL, r.getTypeFamily());
        assertEquals(19, r.getPrecision());    // max(12,12) + 6 + 1
        assertEquals(6, r.getScale());
    }

    @Test
    void divideOfTwoDecimals() {
        TypeDescriptor r = ReturnTypes.DECIMAL_DIVIDE.infer(binding(decimal(18, 6), decimal(18, 6)));
        assertEquals(CoreTypeFamily.DECIMAL, r.getTypeFamily());
        assertEquals(6, r.getScale());         // max(6,6,6)
        assertEquals(24, r.getPrecision());    // (18-6) + 6 + 6
    }

    @Test
    void toDecimalCastReadsPrecisionScaleArgs() {
        Function f = new Function();
        f.setFunc("to_decimal");
        Expression value = new Expression();
        Expression p = new Expression();
        p.setNumber(new BigInteger("18"));
        Expression s = new Expression();
        s.setNumber(new BigInteger("6"));
        f.setArguments(List.of(value, p, s));

        TypeDescriptor r = ReturnTypes.DECIMAL_CAST.infer(new FunctionBinding(f, e -> null));
        assertEquals(CoreTypeFamily.DECIMAL, r.getTypeFamily());
        assertEquals(18, r.getPrecision());
        assertEquals(6, r.getScale());
    }
}
