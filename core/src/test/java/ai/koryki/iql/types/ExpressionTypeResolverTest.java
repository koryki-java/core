package ai.koryki.iql.types;

import ai.koryki.catalog.schema.types.CoreTypeFamily;
import ai.koryki.catalog.schema.types.TypeDescriptor;
import ai.koryki.iql.query.Expression;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Numeric-literal type inference:
 * <ul>
 *   <li>Phase 2 — a decimal literal carries its own precision/scale (12.34 -&gt; DECIMAL(4,2)).</li>
 *   <li>an integer literal resolves to the INTEGER family.</li>
 * </ul>
 * The number-literal branch of {@code resolve} uses none of the resolver's collaborators,
 * so they can be null here.
 */
class ExpressionTypeResolverTest {

    private static final ExpressionTypeResolver RESOLVER = new ExpressionTypeResolver(null, null, null);

    private static TypeDescriptor resolveNumber(Number n) {
        Expression e = new Expression();
        e.setNumber(n);
        return RESOLVER.resolve(e);
    }

    @Test
    void decimalLiteralCarriesPrecisionAndScale() {
        TypeDescriptor d = resolveNumber(new BigDecimal("12.34"));
        assertEquals(CoreTypeFamily.DECIMAL, d.getTypeFamily());
        assertEquals(4, d.getPrecision());
        assertEquals(2, d.getScale());
    }

    @Test
    void integerLiteralIsIntegerFamily() {
        TypeDescriptor d = resolveNumber(new BigInteger("42"));
        assertEquals(CoreTypeFamily.INTEGER, d.getTypeFamily());
    }

    @Test
    void nullLiteralResolvesToNullType() {
        Expression e = new Expression();
        e.setNull(true);
        assertEquals(TypeDescriptor.NULL, RESOLVER.resolve(e));
    }
}
