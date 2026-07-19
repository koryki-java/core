package ai.koryki.result.metric;

import ai.koryki.iql.functions.MathOp;
import ai.koryki.result.quantity.Quantity;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Canonical signature of a result column's expression, built bottom-up in the
 * same walk that derives {@link Quantity} - each node keeps its quantity so
 * metric rules can test dimensions and kinds of subtrees without re-deriving.
 * Records give structural equality, which is the join key for rules comparing
 * subtrees (lag deltas, shares).
 */
public sealed interface Shape {

    Quantity quantity();

    /** Canonical string: sort key for commutative operands, test and debug aid. */
    String canon();

    /** Column reference; ref is the derived header (identity hint across subtrees). */
    record Leaf(String ref, Quantity quantity) implements Shape {
        @Override
        public String canon() {
            String kind = quantity != null && quantity.kind() != null ? quantity.kind().code() : null;
            return "leaf[" + (kind != null ? kind : ref) + "]";
        }
    }

    /** Number literal with its value preserved - recognizes shapes like {@code 1 - ratio}. */
    record Literal(BigDecimal value) implements Shape {
        @Override
        public Quantity quantity() {
            return Quantity.DIMENSIONLESS;
        }

        @Override
        public String canon() {
            return "lit[" + value.stripTrailingZeros().toPlainString() + "]";
        }
    }

    /**
     * Named function call incl. aggregates and window functions (sum, avg,
     * count, lag, ...). {@code ordered} distinguishes running windows
     * (OVER ... ORDER -> running total, moving average) from partition-wide
     * ones (group total/average); false when not windowed.
     */
    record Call(String func, boolean windowed, boolean ordered, List<Shape> args, Quantity quantity) implements Shape {
        @Override
        public String canon() {
            String w = windowed ? (ordered ? "~ow" : "~w") : "";
            return func + w + "(" + args.stream().map(Shape::canon).collect(Collectors.joining(" ")) + ")";
        }
    }

    /** Arithmetic node; multiply/add are n-ary (flattened and canonically sorted by Shapes.op). */
    record Op(MathOp op, List<Shape> operands, Quantity quantity) implements Shape {
        @Override
        public String canon() {
            return "(" + op.name() + " " + operands.stream().map(Shape::canon).collect(Collectors.joining(" ")) + ")";
        }
    }

    /** Text/date literals and unrecognized constructs - matches nothing, poisons nothing. */
    record Opaque(Quantity quantity) implements Shape {
        @Override
        public String canon() {
            return "opaque";
        }
    }
}
