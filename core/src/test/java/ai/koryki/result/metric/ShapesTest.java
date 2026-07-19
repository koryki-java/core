package ai.koryki.result.metric;

import ai.koryki.iql.functions.MathOp;
import ai.koryki.result.quantity.Quantity;
import ai.koryki.result.quantity.UnitRegistry;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ShapesTest {

    private static final Quantity UNIT_PRICE = UnitRegistry.quantity("USD", "unit-price");
    private static final Quantity COUNT = UnitRegistry.quantity("1", "count");
    private static final Quantity RATIO = UnitRegistry.quantity("%", "ratio");
    private static final Quantity MONEY = UnitRegistry.quantity("USD", "money");

    @Test
    public void flattensParserBinaryNesting() {
        // a*b*c arrives from the parser as (a*b)*c
        Shape a = Shapes.leaf("a", UNIT_PRICE);
        Shape b = Shapes.leaf("b", COUNT);
        Shape c = Shapes.leaf("c", RATIO);
        Shape nested = Shapes.op(MathOp.multiply,
                List.of(Shapes.op(MathOp.multiply, List.of(a, b), UNIT_PRICE.times(COUNT)), c),
                UNIT_PRICE.times(COUNT).times(RATIO));

        assertInstanceOf(Shape.Op.class, nested);
        assertEquals(3, ((Shape.Op) nested).operands().size());
    }

    @Test
    public void commutativeOperandsAreSorted() {
        Shape a = Shapes.leaf("a", UNIT_PRICE);
        Shape b = Shapes.leaf("b", COUNT);
        Shape ab = Shapes.op(MathOp.multiply, List.of(a, b), UNIT_PRICE.times(COUNT));
        Shape ba = Shapes.op(MathOp.multiply, List.of(b, a), UNIT_PRICE.times(COUNT));

        assertEquals(ab, ba);
        assertEquals(ab.canon(), ba.canon());
    }

    @Test
    public void minusAndDivideKeepOperandOrder() {
        // non-commutative ops must not be reordered: a-b and b-a stay distinct
        // shapes (canon may collide for same-kind leaves - equality decides)
        Shape a = Shapes.leaf("a", MONEY);
        Shape b = Shapes.leaf("b", MONEY);
        Shape ab = Shapes.op(MathOp.minus, List.of(a, b), MONEY.plus(MONEY));
        Shape ba = Shapes.op(MathOp.minus, List.of(b, a), MONEY.plus(MONEY));

        assertNotEquals(ab, ba);
        assertEquals(List.of(a, b), ((Shape.Op) ab).operands());
    }

    @Test
    public void oneMinusRatioRecognized() {
        Shape net = Shapes.op(MathOp.minus,
                List.of(Shapes.literal(1), Shapes.leaf("discount", RATIO)),
                Quantity.DIMENSIONLESS.plus(RATIO));
        assertTrue(Shapes.isOneMinusRatio(net));
    }

    @Test
    public void twoMinusRatioRejected() {
        Shape s = Shapes.op(MathOp.minus,
                List.of(Shapes.literal(2), Shapes.leaf("discount", RATIO)),
                Quantity.DIMENSIONLESS.plus(RATIO));
        assertFalse(Shapes.isOneMinusRatio(s));
    }

    @Test
    public void oneMinusMoneyRejected() {
        Shape s = Shapes.op(MathOp.minus,
                List.of(Shapes.literal(1), Shapes.leaf("freight", MONEY)),
                Quantity.UNKNOWN);
        assertFalse(Shapes.isOneMinusRatio(s));
    }

    @Test
    public void lagOfMatchesStructurally() {
        Shape rev = Shapes.call("sum", false, false, List.of(Shapes.leaf("freight", MONEY)), MONEY);
        Shape revAgain = Shapes.call("sum", false, false, List.of(Shapes.leaf("freight", MONEY)), MONEY);
        Shape lagged = Shapes.call("lag", true, false, List.of(revAgain), MONEY);

        assertTrue(Shapes.isLagOf(lagged, rev));
        assertFalse(Shapes.isLagOf(rev, rev));
    }

    @Test
    public void nullOperandsBecomeOpaque() {
        Shape s = Shapes.op(MathOp.multiply,
                Arrays.asList(Shapes.leaf("a", MONEY), null), Quantity.UNKNOWN);
        assertInstanceOf(Shape.Opaque.class, ((Shape.Op) s).operands().stream()
                .filter(o -> o instanceof Shape.Opaque).findFirst().orElse(null));
    }

    @Test
    public void unwrapAggUnwrapsOnlyListedFunctions() {
        Shape inner = Shapes.leaf("a", MONEY);
        Shape sum = Shapes.call("sum", false, false, List.of(inner), MONEY);
        Shape max = Shapes.call("max", false, false, List.of(inner), MONEY);

        assertEquals(inner, Shapes.unwrapAgg(sum, "sum"));
        assertEquals(max, Shapes.unwrapAgg(max, "sum"));
        assertEquals(inner, Shapes.unwrapAgg(inner, "sum"));
    }
}
