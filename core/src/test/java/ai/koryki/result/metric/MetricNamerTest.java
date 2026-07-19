package ai.koryki.result.metric;

import ai.koryki.iql.functions.MathOp;
import ai.koryki.result.quantity.Quantity;
import ai.koryki.result.quantity.UnitRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * One test per rule M1-M10 plus the deliberate drop cases and the scientific
 * kind fallback. Shapes are built the way Investigator's walk would build them
 * (quantities derived through the real calculus).
 */
public class MetricNamerTest {

    private static final Quantity UNIT_PRICE = UnitRegistry.quantity("USD", "unit-price");
    private static final Quantity COUNT = UnitRegistry.quantity("1", "count");
    private static final Quantity RATIO = UnitRegistry.quantity("%", "ratio");
    private static final Quantity MONEY = UnitRegistry.quantity("USD", "money");
    private static final Quantity COST = UnitRegistry.quantity("USD", "cost");
    private static final Quantity LENGTH = UnitRegistry.quantity("km", "length");
    private static final Quantity DURATION = UnitRegistry.quantity("h", "duration");

    private final MetricNamer namer = new MetricNamer();

    private Optional<String> key(Shape s) {
        return namer.metricKey(s);
    }

    // ---- building blocks ----

    private static Shape revenueMultiply(boolean net) {
        Shape price = Shapes.leaf("Unit Price", UNIT_PRICE);
        Shape qty = Shapes.leaf("Quantity", COUNT);
        Quantity q = UNIT_PRICE.times(COUNT);
        List<Shape> factors;
        if (net) {
            Shape oneMinus = Shapes.op(MathOp.minus,
                    List.of(Shapes.literal(1), Shapes.leaf("Discount", RATIO)),
                    Quantity.DIMENSIONLESS.plus(RATIO));
            q = q.times(oneMinus.quantity());
            // parser nesting: (price*qty)*(1-discount)
            factors = List.of(Shapes.op(MathOp.multiply, List.of(price, qty), UNIT_PRICE.times(COUNT)), oneMinus);
        } else {
            factors = List.of(price, qty);
        }
        return Shapes.op(MathOp.multiply, factors, q);
    }

    private static Shape sum(Shape inner) {
        return Shapes.call("sum", false, false, List.of(inner), inner.quantity());
    }

    // ---- M1..M10 ----

    @Test
    public void m1Count() {
        Shape s = Shapes.call("count", false, false, List.of(), Quantity.COUNT);
        assertEquals(Optional.of("metric.count"), key(s));
    }

    @Test
    public void m2Growth() {
        Shape rev = sum(Shapes.leaf("Freight", MONEY));
        Shape lag = Shapes.call("lag", true, false, List.of(sum(Shapes.leaf("Freight", MONEY))), MONEY);
        Shape diff = Shapes.op(MathOp.minus, List.of(rev, lag), MONEY.plus(MONEY));
        Shape growth = Shapes.op(MathOp.divide, List.of(diff, lag), diff.quantity().dividedBy(MONEY));
        assertEquals(Optional.of("metric.growth"), key(growth));
    }

    @Test
    public void m3Change() {
        Shape rev = sum(Shapes.leaf("Freight", MONEY));
        Shape lag = Shapes.call("lag", true, false, List.of(sum(Shapes.leaf("Freight", MONEY))), MONEY);
        Shape change = Shapes.op(MathOp.minus, List.of(rev, lag), MONEY.plus(MONEY));
        assertEquals(Optional.of("metric.change"), key(change));
    }

    @Test
    public void m4ShareOfTotal() {
        Shape rev = sum(Shapes.leaf("Freight", MONEY));
        Shape total = Shapes.call("sum", true, false, List.of(sum(Shapes.leaf("Freight", MONEY))), MONEY);
        Shape share = Shapes.op(MathOp.divide, List.of(rev, total), MONEY.dividedBy(MONEY));
        assertEquals(Optional.of("metric.share"), key(share));
    }

    @Test
    public void m4ShareOverInnerArgument() {
        // rev / sum(freight) OVER () where the window total wraps the bare argument
        Shape rev = sum(Shapes.leaf("Freight", MONEY));
        Shape total = Shapes.call("sum", true, false, List.of(Shapes.leaf("Freight", MONEY)), MONEY);
        Shape share = Shapes.op(MathOp.divide, List.of(rev, total), MONEY.dividedBy(MONEY));
        assertEquals(Optional.of("metric.share"), key(share));
    }

    @Test
    public void m5NetRevenue() {
        assertEquals(Optional.of("metric.net-revenue"), key(sum(revenueMultiply(true))));
    }

    @Test
    public void m5NetRevenueBareMultiply() {
        assertEquals(Optional.of("metric.net-revenue"), key(revenueMultiply(true)));
    }

    @Test
    public void m6Revenue() {
        assertEquals(Optional.of("metric.revenue"), key(sum(revenueMultiply(false))));
    }

    @Test
    public void m7Profit() {
        Shape profit = Shapes.op(MathOp.minus,
                List.of(Shapes.leaf("Revenue", MONEY), Shapes.leaf("Cost", COST)),
                MONEY.plus(COST));
        assertEquals(Optional.of("metric.profit"), key(sum(profit)));
        assertEquals(Optional.of("metric.profit"), key(profit));
    }

    @Test
    public void m8Margin() {
        Shape profit = Shapes.op(MathOp.minus,
                List.of(Shapes.leaf("Revenue", MONEY), Shapes.leaf("Cost", COST)),
                MONEY.plus(COST));
        Shape margin = Shapes.op(MathOp.divide,
                List.of(profit, Shapes.leaf("Revenue", MONEY)),
                profit.quantity().dividedBy(MONEY));
        assertEquals(Optional.of("metric.margin"), key(margin));
    }

    @Test
    public void m9AverageValue() {
        Shape s = Shapes.call("avg", false, false, List.of(Shapes.leaf("Freight", MONEY)), MONEY);
        assertEquals(Optional.of("metric.average-value"), key(s));
    }

    @Test
    public void m10PricePerUnit() {
        Shape s = Shapes.op(MathOp.divide,
                List.of(sum(Shapes.leaf("Freight", MONEY)), sum(Shapes.leaf("Quantity", COUNT))),
                MONEY.dividedBy(COUNT));
        assertEquals(Optional.of("metric.price-per-unit"), key(s));
    }

    // ---- window family and totals (from the privatetest corpus shapes) ----

    @Test
    public void runningTotalFromOrderedWindow() {
        // sum(freight) OVER (PARTITION ... ORDER ... ROWS ...) - cumulative_freight
        Shape s = Shapes.call("sum", true, true, List.of(Shapes.leaf("Freight", MONEY)), MONEY);
        assertEquals(Optional.of("metric.running-total"), key(s));
    }

    @Test
    public void groupTotalFromPartitionOnlyWindow() {
        Shape s = Shapes.call("sum", true, false, List.of(Shapes.leaf("Freight", MONEY)), MONEY);
        assertEquals(Optional.of("metric.group-total"), key(s));
    }

    @Test
    public void movingAverageFromOrderedWindow() {
        Shape s = Shapes.call("avg", true, true, List.of(Shapes.leaf("Unit Price", UNIT_PRICE)), UNIT_PRICE);
        assertEquals(Optional.of("metric.moving-average"), key(s));
    }

    @Test
    public void groupAverageFromPartitionOnlyWindow() {
        Shape s = Shapes.call("avg", true, false, List.of(Shapes.leaf("Unit Price", UNIT_PRICE)), UNIT_PRICE);
        assertEquals(Optional.of("metric.group-average"), key(s));
    }

    @Test
    public void rankOverWindow() {
        Shape s = Shapes.call("rank", true, true, List.of(), Quantity.COUNT);
        assertEquals(Optional.of("metric.rank"), key(s));
    }

    @Test
    public void plainSumOfMoneyIsTotal() {
        assertEquals(Optional.of("metric.total"), key(sum(Shapes.leaf("Freight", MONEY))));
    }

    @Test
    public void windowedSumOfRevenueProductIsRunningTotalNotRevenue() {
        // order_total from the corpus: sum(price*qty) OVER (... ORDER ...) -
        // windowed aggregates are not transparent totalizers
        Shape s = Shapes.call("sum", true, true, List.of(revenueMultiply(false)), UNIT_PRICE.times(COUNT));
        assertEquals(Optional.of("metric.running-total"), key(s));
    }

    // ---- deliberate drops and fallbacks ----

    @Test
    public void maxOfMoneyGetsNoMetric() {
        // kind fallback is Op-only: max(freight) keeps its readable header
        Shape s = Shapes.call("max", false, false, List.of(Shapes.leaf("Freight", MONEY)), MONEY);
        assertEquals(Optional.empty(), key(s));
    }

    @Test
    public void moneyMinusMoneyWithoutCostFallsBackToKind() {
        Shape s = Shapes.op(MathOp.minus,
                List.of(Shapes.leaf("Freight", MONEY), Shapes.leaf("Other", MONEY)),
                MONEY.plus(MONEY));
        assertEquals(Optional.of("kind.money"), key(s));
    }

    @Test
    public void moneyOverMoneyIsRatioNotMargin() {
        Shape s = Shapes.op(MathOp.divide,
                List.of(Shapes.leaf("Freight", MONEY), Shapes.leaf("Other", MONEY)),
                MONEY.dividedBy(MONEY));
        assertEquals(Optional.of("kind.ratio"), key(s));
    }

    @Test
    public void nonWindowedTotalIsNoShare() {
        Shape rev = sum(Shapes.leaf("Freight", MONEY));
        Shape total = Shapes.call("sum", false, false, List.of(sum(Shapes.leaf("Freight", MONEY))), MONEY);
        Shape s = Shapes.op(MathOp.divide, List.of(rev, total), MONEY.dividedBy(MONEY));
        assertNotEquals(Optional.of("metric.share"), key(s));
    }

    @Test
    public void speedConcludedFromDimensionFallback() {
        Shape s = Shapes.op(MathOp.divide,
                List.of(Shapes.leaf("Distance", LENGTH), Shapes.leaf("Duration", DURATION)),
                LENGTH.dividedBy(DURATION));
        assertEquals(Optional.of("kind.speed"), key(s));
    }

    @Test
    public void bareLeafGetsNoMetric() {
        assertEquals(Optional.empty(), key(Shapes.leaf("Freight", MONEY)));
    }

    @Test
    public void unknownQuantityCompositeGetsNoMetric() {
        Shape s = Shapes.op(MathOp.multiply,
                List.of(Shapes.leaf("a", Quantity.UNKNOWN), Shapes.leaf("b", Quantity.UNKNOWN)),
                Quantity.UNKNOWN);
        assertEquals(Optional.empty(), key(s));
    }

    @Test
    public void newKindsRegistered() {
        assertTrue(UnitRegistry.kind("cost").isPresent());
        assertTrue(UnitRegistry.kind("pressure").isPresent());
        assertTrue(UnitRegistry.kind("rate").isPresent());
        assertTrue(UnitRegistry.kind("density").isPresent());
        // cost must not hijack the canonical money kind for derived dims
        assertEquals("money", UnitRegistry.kindByDim(MONEY.dim()).orElseThrow().code());
    }
}
