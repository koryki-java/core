package ai.koryki.result.metric;

import ai.koryki.iql.functions.MathOp;
import ai.koryki.result.quantity.BaseDim;
import ai.koryki.result.quantity.DimVector;
import ai.koryki.result.quantity.Quantity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Factories and rule predicates for {@link Shape} trees. {@code op()} does the
 * normalization that makes signatures canonical: the parser builds math ops as
 * nested binaries ({@code a*b*c} arrives as {@code (a*b)*c}), so same-op
 * multiply/add chains are flattened to n-ary nodes and their operands sorted -
 * {@code qty*price} and {@code price*qty} yield the same shape.
 */
public final class Shapes {

    private static final DimVector COUNT_DIM = DimVector.of(BaseDim.COUNT);

    private Shapes() {
    }

    public static Shape leaf(String ref, Quantity q) {
        return new Shape.Leaf(ref, q != null ? q : Quantity.UNKNOWN);
    }

    public static Shape literal(Number value) {
        return new Shape.Literal(new BigDecimal(value.toString()));
    }

    public static Shape call(String func, boolean windowed, boolean ordered, List<Shape> args, Quantity q) {
        return new Shape.Call(func, windowed, windowed && ordered,
                args.stream().map(Shapes::orOpaque).toList(),
                q != null ? q : Quantity.UNKNOWN);
    }

    public static Shape op(MathOp op, List<Shape> operands, Quantity q) {
        List<Shape> flat = new ArrayList<>();
        for (Shape s : operands) {
            Shape safe = orOpaque(s);
            if ((op == MathOp.multiply || op == MathOp.add)
                    && safe instanceof Shape.Op inner && inner.op() == op) {
                flat.addAll(inner.operands());
            } else {
                flat.add(safe);
            }
        }
        if (op == MathOp.multiply || op == MathOp.add) {
            flat.sort(Comparator.comparing(Shape::canon));
        }
        return new Shape.Op(op, List.copyOf(flat), q != null ? q : Quantity.UNKNOWN);
    }

    public static Shape opaque(Quantity q) {
        return new Shape.Opaque(q != null ? q : Quantity.UNKNOWN);
    }

    private static Shape orOpaque(Shape s) {
        return s != null ? s : new Shape.Opaque(Quantity.UNKNOWN);
    }

    // ---- predicates for metric rules ----

    /** Derived measures only: arithmetic or function results, never bare columns. */
    public static boolean composite(Shape s) {
        return s instanceof Shape.Op || s instanceof Shape.Call;
    }

    public static boolean hasDim(Shape s, DimVector dim) {
        return s.quantity().known() && s.quantity().dim().equals(dim);
    }

    public static boolean hasKind(Shape s, String code) {
        return s.quantity().kind() != null && code.equals(s.quantity().kind().code());
    }

    /** Count-like factor: count kind or COUNT dimension. */
    public static boolean isCountish(Shape s) {
        return hasKind(s, "count") || hasDim(s, COUNT_DIM);
    }

    /** The net modifier {@code (1 - ratio)}: minus of the literal one and a known dimensionless. */
    public static boolean isOneMinusRatio(Shape s) {
        return s instanceof Shape.Op op
                && op.op() == MathOp.minus
                && op.operands().size() == 2
                && op.operands().get(0) instanceof Shape.Literal one
                && one.value().compareTo(BigDecimal.ONE) == 0
                && op.operands().get(1).quantity().known()
                && op.operands().get(1).quantity().dim().isDimensionless();
    }

    /**
     * Unwraps a single-argument aggregate ({@code sum(x) -> x}); other shapes
     * pass through. Windowed aggregates are NOT transparent - a running total
     * of a revenue product is a running total, not revenue.
     */
    public static Shape unwrapAgg(Shape s, String... funcs) {
        if (s instanceof Shape.Call c && !c.windowed() && c.args().size() == 1) {
            for (String f : funcs) {
                if (f.equals(c.func())) {
                    return c.args().get(0);
                }
            }
        }
        return s;
    }

    /** True when candidate is {@code lag(base)} / {@code lead(base)} (extra offset args allowed). */
    public static boolean isLagOf(Shape candidate, Shape base) {
        return candidate instanceof Shape.Call c
                && ("lag".equals(c.func()) || "lead".equals(c.func()))
                && !c.args().isEmpty()
                && c.args().get(0).equals(base);
    }
}
