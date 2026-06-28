/*
 * Copyright 2025-2026 Johannes Zemlin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package ai.koryki.iql.validate;

import ai.koryki.antlr.Range;
import ai.koryki.iql.Collector;
import ai.koryki.iql.Visitor;
import ai.koryki.iql.query.Duration;
import ai.koryki.iql.query.Expression;
import org.antlr.v4.runtime.RuleContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A DURATION literal must list its components largest-unit-first
 * ({@code 1y2mo15d4h}, not {@code 4h15d2mo1y}). The grammar accepts any order;
 * this turns an out-of-order literal into a positioned validation error instead
 * of silently reordering it at application time, so the written order matches
 * the order in which the components are applied.
 */
public class DurationValidator implements Visitor, Collector<List<Violation>> {

    private final List<Violation> violations = new ArrayList<>();
    private final Map<Object, RuleContext> iqlToContext;

    public DurationValidator(Map<Object, RuleContext> iqlToContext) {
        this.iqlToContext = iqlToContext;
    }

    @Override
    public boolean visit(Deque<Object> deque, Expression expression) {
        Duration duration = expression.getDuration();
        if (duration != null && !largestFirst(duration.getComponents())) {
            String canonical = duration.getComponents().stream()
                    .sorted(Comparator.comparingInt((Duration.Component c) -> c.unit().ordinal()).reversed())
                    .map(Duration.Component::toString)
                    .collect(Collectors.joining());
            violations.add(new Violation("duration", expression, Range.range(iqlToContext.get(expression)),
                    "duration '" + duration + "' must list components largest-unit-first — write '" + canonical + "'"));
        }
        return true;
    }

    /** Largest-first means no component names a larger unit than the one before it (Unit ordinals ascend by size). */
    private static boolean largestFirst(List<Duration.Component> components) {
        for (int i = 1; i < components.size(); i++) {
            if (components.get(i).unit().ordinal() > components.get(i - 1).unit().ordinal()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<Violation> collect() {
        return violations;
    }
}
