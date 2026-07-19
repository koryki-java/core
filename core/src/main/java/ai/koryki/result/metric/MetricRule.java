package ai.koryki.result.metric;

import java.util.Optional;

/** One recognition rule: shape in, resource-bundle key ("metric.revenue") out. */
@FunctionalInterface
public interface MetricRule {

    Optional<String> apply(Shape shape);
}
