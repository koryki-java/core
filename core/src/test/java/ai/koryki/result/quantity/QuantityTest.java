package ai.koryki.result.quantity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class QuantityTest {

    private static final Quantity UNIT_PRICE = UnitRegistry.quantity("USD", "unit-price");
    private static final Quantity COUNT = UnitRegistry.quantity("1", "count");
    private static final Quantity MONEY_USD = UnitRegistry.quantity("USD", "money");
    private static final Quantity MONEY_EUR = UnitRegistry.quantity("EUR", "money");
    private static final Quantity RATIO = UnitRegistry.quantity("%", "ratio");
    private static final Quantity MASS = UnitRegistry.quantity("kg", "mass");

    @Test
    public void revenueFromPriceTimesQuantity() {
        // unit_price * quantity * (1 - discount) -> money, keeping the currency
        Quantity revenue = UNIT_PRICE.times(COUNT).times(Quantity.DIMENSIONLESS.plus(RATIO));
        assertTrue(revenue.known());
        assertEquals(DimVector.of(BaseDim.MONEY), revenue.dim());
        assertEquals("$", revenue.symbol());
        assertEquals("money", revenue.kind().code());
    }

    @Test
    public void profitFromRevenueMinusCost() {
        // revenue - cost = profit: money - money stays money
        Quantity profit = MONEY_USD.plus(MONEY_USD);
        assertEquals(DimVector.of(BaseDim.MONEY), profit.dim());
        assertEquals("$", profit.symbol());
    }

    @Test
    public void marginFromMoneyDividedByMoney() {
        Quantity margin = MONEY_USD.dividedBy(MONEY_USD);
        assertTrue(margin.known());
        assertTrue(margin.dim().isDimensionless());
        assertEquals("ratio", margin.kind().code());
        assertNull(margin.symbol());
    }

    @Test
    public void mixedDimensionSumDegradesToUnknown() {
        assertFalse(MONEY_USD.plus(MASS).known());
        assertSame(Quantity.UNKNOWN, MONEY_USD.plus(MASS));
    }

    @Test
    public void mixedCurrenciesKeepDimensionLoseUnit() {
        Quantity q = MONEY_USD.plus(MONEY_EUR);
        assertTrue(q.known());
        assertEquals(DimVector.of(BaseDim.MONEY), q.dim());
        assertNull(q.symbol());
    }

    @Test
    public void unknownIsViral() {
        assertFalse(MONEY_USD.times(Quantity.UNKNOWN).known());
        assertFalse(Quantity.UNKNOWN.dividedBy(COUNT).known());
    }

    @Test
    public void countIsAKnownDimension() {
        assertTrue(Quantity.COUNT.known());
        assertEquals(DimVector.of(BaseDim.COUNT), Quantity.COUNT.dim());
    }

    @Test
    public void compatibility() {
        assertTrue(MONEY_USD.compatibleWith(MONEY_EUR));   // same dimension, shared axis
        assertFalse(MONEY_USD.compatibleWith(MASS));
        assertFalse(MONEY_USD.compatibleWith(Quantity.UNKNOWN));
        assertTrue(Quantity.UNKNOWN.compatibleWith(Quantity.UNKNOWN));
    }

    @Test
    public void speedFromDistanceOverDuration() {
        Quantity distance = UnitRegistry.quantity("km", "length");
        Quantity duration = UnitRegistry.quantity("h", "duration");
        Quantity speed = distance.dividedBy(duration);
        assertTrue(speed.known());
        assertEquals("speed", speed.kind().code());
    }
}
