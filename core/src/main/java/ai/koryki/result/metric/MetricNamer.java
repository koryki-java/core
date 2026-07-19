package ai.koryki.result.metric;

import ai.koryki.iql.functions.MathOp;
import ai.koryki.result.quantity.BaseDim;
import ai.koryki.result.quantity.DimVector;

import java.util.List;
import java.util.Optional;

/**
 * Concludes a metric key for a derived measure column from its {@link Shape} -
 * ordered decision table, first match wins.
 * Business rules (M1-M10) recognize expression shapes whose dimension alone is
 * ambiguous; anything unmatched falls back to the derived quantity's kind
 * ("kind.speed", "kind.pressure", ...), which is what makes scientific models
 * work without further rules. Bare column references never get a metric - a
 * raw annotated column keeps its header.
 *
 * <p>Rule order encodes hazards: growth before margin (both divides), change
 * before profit (both minuses), share before price-per-unit, window rules
 * before the revenue family (a running total of a revenue product is a
 * running total - windowed aggregates are not transparent totalizers).
 *
 * <p>Deliberately unrecognized (dropped rather than guessed): money-money
 * without a cost-kind annotation, non-windowed share denominators, bare
 * money/money as margin, (1+ratio) gross modifiers, min/max-wrapped totals.
 */
public final class MetricNamer {

    private static final DimVector MONEY = DimVector.of(BaseDim.MONEY);

    private final List<MetricRule> rules = List.of(
            MetricNamer::m1Count,
            MetricNamer::m2Growth,
            MetricNamer::m3Change,
            MetricNamer::m4ShareOfTotal,
            MetricNamer::m5RunningTotal,
            MetricNamer::m6MovingAverage,
            MetricNamer::m7Rank,
            MetricNamer::m8NetRevenue,
            MetricNamer::m9Revenue,
            MetricNamer::m11Profit,
            MetricNamer::m12Margin,
            // total is the weakest sum rule - any richer sum shape must win first
            MetricNamer::m10Total,
            MetricNamer::m13AverageValue,
            MetricNamer::m14PricePerUnit);

    /**
     * Shape rules -> kind fallback -> empty. Composite (derived) shapes only,
     * and the kind fallback applies to arithmetic derivations (Op) alone: a
     * plain max(freight) keeps its readable header instead of degrading to the
     * kind's generic display name.
     */
    public Optional<String> metricKey(Shape shape) {
        if (shape == null || !Shapes.composite(shape)) {
            return Optional.empty();
        }
        for (MetricRule rule : rules) {
            Optional<String> key = rule.apply(shape);
            if (key.isPresent()) {
                return key;
            }
        }
        if (shape instanceof Shape.Op && shape.quantity().kind() != null) {
            return Optional.of("kind." + shape.quantity().kind().code());
        }
        return Optional.empty();
    }

    private static Optional<String> m1Count(Shape s) {
        return s instanceof Shape.Call c && "count".equals(c.func())
                ? Optional.of("metric.count") : Optional.empty();
    }

    /** (x - lag(x)) / lag(x)  or  (x - lag(x)) / x */
    private static Optional<String> m2Growth(Shape s) {
        if (s instanceof Shape.Op div && div.op() == MathOp.divide && div.operands().size() == 2
                && div.operands().get(0) instanceof Shape.Op num && num.op() == MathOp.minus
                && num.operands().size() == 2) {
            Shape base = num.operands().get(0);
            Shape lagged = num.operands().get(1);
            Shape den = div.operands().get(1);
            if (Shapes.isLagOf(lagged, base) && (den.equals(lagged) || den.equals(base))) {
                return Optional.of("metric.growth");
            }
        }
        return Optional.empty();
    }

    /** x - lag(x) */
    private static Optional<String> m3Change(Shape s) {
        if (s instanceof Shape.Op op && op.op() == MathOp.minus && op.operands().size() == 2
                && Shapes.isLagOf(op.operands().get(1), op.operands().get(0))) {
            return Optional.of("metric.change");
        }
        return Optional.empty();
    }

    /** x / sum(x) OVER (...) - the denominator must be a windowed total of the same shape. */
    private static Optional<String> m4ShareOfTotal(Shape s) {
        if (s instanceof Shape.Op div && div.op() == MathOp.divide && div.operands().size() == 2
                && div.operands().get(1) instanceof Shape.Call total
                && "sum".equals(total.func()) && total.windowed() && total.args().size() == 1) {
            Shape x = div.operands().get(0);
            Shape totalArg = total.args().get(0);
            if (totalArg.equals(x) || totalArg.equals(Shapes.unwrapAgg(x, "sum"))) {
                return Optional.of("metric.share");
            }
        }
        return Optional.empty();
    }

    /** Windowed sum: OVER with ORDER accumulates (running total), without it spans the partition. */
    private static Optional<String> m5RunningTotal(Shape s) {
        if (s instanceof Shape.Call c && "sum".equals(c.func()) && c.windowed()) {
            return Optional.of(c.ordered() ? "metric.running-total" : "metric.group-total");
        }
        return Optional.empty();
    }

    /** Windowed avg: ordered frame slides (moving average), partition-wide compares. */
    private static Optional<String> m6MovingAverage(Shape s) {
        if (s instanceof Shape.Call c && "avg".equals(c.func()) && c.windowed()) {
            return Optional.of(c.ordered() ? "metric.moving-average" : "metric.group-average");
        }
        return Optional.empty();
    }

    /** rank()/dense_rank() OVER (...). */
    private static Optional<String> m7Rank(Shape s) {
        if (s instanceof Shape.Call c && ("rank".equals(c.func()) || "dense_rank".equals(c.func()))) {
            return Optional.of("metric.rank");
        }
        return Optional.empty();
    }

    /** sum(unit-price * count * (1 - ratio)) with MONEY result. */
    private static Optional<String> m8NetRevenue(Shape s) {
        return revenueProduct(s, true) ? Optional.of("metric.net-revenue") : Optional.empty();
    }

    /** sum(unit-price * count) with MONEY result. */
    private static Optional<String> m9Revenue(Shape s) {
        return revenueProduct(s, false) ? Optional.of("metric.revenue") : Optional.empty();
    }

    /** Plain sum over money that is no revenue product - sum(freight) is a total. */
    private static Optional<String> m10Total(Shape s) {
        if (s instanceof Shape.Call c && "sum".equals(c.func()) && !c.windowed()
                && c.args().size() == 1 && Shapes.hasDim(c.args().get(0), MONEY)) {
            return Optional.of("metric.total");
        }
        return Optional.empty();
    }

    private static boolean revenueProduct(Shape s, boolean net) {
        Shape inner = Shapes.unwrapAgg(s, "sum");
        if (!(inner instanceof Shape.Op mul) || mul.op() != MathOp.multiply || !Shapes.hasDim(mul, MONEY)) {
            return false;
        }
        boolean price = mul.operands().stream().anyMatch(o -> Shapes.hasKind(o, "unit-price"));
        boolean count = mul.operands().stream().anyMatch(Shapes::isCountish);
        boolean netFactor = mul.operands().stream().anyMatch(Shapes::isOneMinusRatio);
        return price && count && netFactor == net;
    }

    /** sum?(money - money) where the subtrahend is annotated as cost. */
    private static Optional<String> m11Profit(Shape s) {
        return profitShape(s) ? Optional.of("metric.profit") : Optional.empty();
    }

    private static boolean profitShape(Shape s) {
        Shape inner = Shapes.unwrapAgg(s, "sum");
        return inner instanceof Shape.Op op && op.op() == MathOp.minus && op.operands().size() == 2
                && Shapes.hasDim(op.operands().get(0), MONEY)
                && Shapes.hasDim(op.operands().get(1), MONEY)
                && Shapes.hasKind(op.operands().get(1), "cost");
    }

    /** profit-shape / money */
    private static Optional<String> m12Margin(Shape s) {
        if (s instanceof Shape.Op div && div.op() == MathOp.divide && div.operands().size() == 2
                && profitShape(div.operands().get(0))
                && Shapes.hasDim(div.operands().get(1), MONEY)) {
            return Optional.of("metric.margin");
        }
        return Optional.empty();
    }

    /** avg(money) */
    private static Optional<String> m13AverageValue(Shape s) {
        if (s instanceof Shape.Call c && "avg".equals(c.func()) && c.args().size() == 1
                && Shapes.hasDim(c.args().get(0), MONEY)) {
            return Optional.of("metric.average-value");
        }
        return Optional.empty();
    }

    /** money / count, either side optionally sum-wrapped: sum(money)/sum(count) = average price. */
    private static Optional<String> m14PricePerUnit(Shape s) {
        if (s instanceof Shape.Op div && div.op() == MathOp.divide && div.operands().size() == 2
                && Shapes.hasDim(Shapes.unwrapAgg(div.operands().get(0), "sum"), MONEY)
                && Shapes.isCountish(Shapes.unwrapAgg(div.operands().get(1), "sum"))) {
            return Optional.of("metric.price-per-unit");
        }
        return Optional.empty();
    }
}
