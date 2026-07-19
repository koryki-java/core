package ai.koryki.result.quantity;

import java.util.EnumMap;
import java.util.Map;

/**
 * Exponent vector over {@link BaseDim} - the core of the quantity calculus.
 * Multiplication adds exponents, division subtracts them:
 * length*width*height = LENGTH^3 (volume), distance/duration = LENGTH*TIME^-1 (speed),
 * unit-price*count = MONEY (revenue), money/money = dimensionless (ratio).
 * Canonical form never stores zero exponents, so {@link #equals} is dimension equality.
 */
public record DimVector(Map<BaseDim, Integer> exponents) {

    /** The empty vector: dimensionless (ratios, bare literals). */
    public static final DimVector NONE = new DimVector(Map.of());

    public DimVector {
        Map<BaseDim, Integer> m = new EnumMap<>(BaseDim.class);
        exponents.forEach((d, e) -> {
            if (e != null && e != 0) {
                m.put(d, e);
            }
        });
        exponents = Map.copyOf(m);
    }

    public static DimVector of(BaseDim dim) {
        return of(dim, 1);
    }

    public static DimVector of(BaseDim dim, int exponent) {
        return new DimVector(Map.of(dim, exponent));
    }

    public DimVector times(DimVector other) {
        return combine(other, 1);
    }

    public DimVector dividedBy(DimVector other) {
        return combine(other, -1);
    }

    private DimVector combine(DimVector other, int sign) {
        Map<BaseDim, Integer> m = new EnumMap<>(BaseDim.class);
        m.putAll(exponents);
        other.exponents.forEach((d, e) -> m.merge(d, sign * e, Integer::sum));
        return new DimVector(m);
    }

    public boolean isDimensionless() {
        return exponents.isEmpty();
    }

    @Override
    public String toString() {
        if (exponents.isEmpty()) {
            return "1";
        }
        StringBuilder b = new StringBuilder();
        for (BaseDim d : BaseDim.values()) {
            Integer e = exponents.get(d);
            if (e == null) {
                continue;
            }
            if (b.length() > 0) {
                b.append("*");
            }
            b.append(d.name());
            if (e != 1) {
                b.append("^").append(e);
            }
        }
        return b.toString();
    }
}
