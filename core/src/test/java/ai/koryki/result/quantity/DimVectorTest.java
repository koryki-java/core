package ai.koryki.result.quantity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DimVectorTest {

    @Test
    public void volumeFromLengths() {
        DimVector length = DimVector.of(BaseDim.LENGTH);
        DimVector volume = length.times(length).times(length);
        assertEquals(DimVector.of(BaseDim.LENGTH, 3), volume);
        assertEquals("LENGTH^3", volume.toString());
    }

    @Test
    public void speedFromDistanceAndDuration() {
        DimVector speed = DimVector.of(BaseDim.LENGTH).dividedBy(DimVector.of(BaseDim.TIME));
        assertEquals(new DimVector(java.util.Map.of(BaseDim.LENGTH, 1, BaseDim.TIME, -1)), speed);
    }

    @Test
    public void sameDimensionsCancelToDimensionless() {
        DimVector money = DimVector.of(BaseDim.MONEY);
        assertTrue(money.dividedBy(money).isDimensionless());
        assertEquals(DimVector.NONE, money.dividedBy(money));
    }

    @Test
    public void unitPriceTimesCountIsMoney() {
        DimVector unitPrice = DimVector.of(BaseDim.MONEY).dividedBy(DimVector.of(BaseDim.COUNT));
        DimVector count = DimVector.of(BaseDim.COUNT);
        assertEquals(DimVector.of(BaseDim.MONEY), unitPrice.times(count));
    }

    @Test
    public void zeroExponentsAreNeverStored() {
        DimVector v = new DimVector(java.util.Map.of(BaseDim.MASS, 0));
        assertTrue(v.isDimensionless());
        assertEquals(DimVector.NONE, v);
    }
}
