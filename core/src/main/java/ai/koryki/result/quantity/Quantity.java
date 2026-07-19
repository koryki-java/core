package ai.koryki.result.quantity;

/**
 * The dimensional value of a result column: display unit, business kind and
 * dimension vector. Combinators mirror the quantity calculus and never throw -
 * anything underivable degrades to {@link #UNKNOWN}, because charting must
 * never break query execution.
 *
 * <p>{@code known} separates "no annotation anywhere" (UNKNOWN) from a derived
 * dimensionless result (ratio). Two UNKNOWN quantities are treated as
 * compatible with each other only.
 */
public record Quantity(Unit unit, QuantityKind kind, DimVector dim, boolean known) {

    /** Nothing known about the column - unannotated fields, failed derivations. */
    public static final Quantity UNKNOWN = new Quantity(null, null, DimVector.NONE, false);

    /** Bare literals and derived ratios: dimension is empty, but that is known. */
    public static final Quantity DIMENSIONLESS = new Quantity(null, null, DimVector.NONE, true);

    /** Result of count(...): dimension COUNT, unit "1". */
    public static final Quantity COUNT = UnitRegistry.quantity("1", "count");

    /** multiply: exponents add; a countish factor keeps the other side's display unit. */
    public Quantity times(Quantity other) {
        if (!known || !other.known) {
            return UNKNOWN;
        }
        DimVector d = dim.times(other.dim);
        Unit u = countish(other.dim) ? unit : countish(dim) ? other.unit : null;
        return derived(d, u);
    }

    /** divide: exponents subtract; same dimensions cancel to a ratio. */
    public Quantity dividedBy(Quantity other) {
        if (!known || !other.known) {
            return UNKNOWN;
        }
        DimVector d = dim.dividedBy(other.dim);
        Unit u = countish(other.dim) ? unit : null;
        return derived(d, u);
    }

    /** add/subtract: requires equal dimensions and preserves them (revenue - cost = profit). */
    public Quantity plus(Quantity other) {
        if (!known || !other.known || !dim.equals(other.dim)) {
            return UNKNOWN;
        }
        Unit u;
        if (unit == null) {
            u = other.unit;
        } else if (other.unit == null || unit.code().equals(other.unit.code())) {
            u = unit;
        } else {
            u = null; // EUR + USD: dimension holds, display unit does not
        }
        return derived(dim, u);
    }

    /** Same-dimension check for shared chart axes; UNKNOWN only matches UNKNOWN. */
    public boolean compatibleWith(Quantity other) {
        if (known != other.known) {
            return false;
        }
        return !known || dim.equals(other.dim);
    }

    /** Display symbol for axis titles, null when there is nothing to show. */
    public String symbol() {
        return unit != null && !unit.symbol().isEmpty() ? unit.symbol() : null;
    }

    private static Quantity derived(DimVector dim, Unit unit) {
        QuantityKind k = UnitRegistry.kindByDim(dim).orElse(null);
        return new Quantity(unit, k, dim, true);
    }

    private static boolean countish(DimVector d) {
        return d.isDimensionless() || d.equals(DimVector.of(BaseDim.COUNT));
    }
}
