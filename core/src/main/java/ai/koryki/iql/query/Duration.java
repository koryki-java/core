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
package ai.koryki.iql.query;

import java.util.List;
import java.util.stream.Collectors;

public class Duration {

    public enum Unit {
        MILLISECOND("ms"),
        SECOND("s"),
        MINUTE("min"),
        HOUR("h"),
        DAY("d"),
        WEEK("w"),
        MONTH("mo"),
        QUARTAL("q"),
        YEAR("y");

        private final String u;

        Unit(String u) {
            this.u = u;
        }

        public String getUnit() {
            return u;
        }
    }

    public record Component(int value, Unit unit) {
        @Override
        public String toString() {
            return value + unit.getUnit();
        }
    }

    private final List<Component> components;

    /** Single-component duration — backward-compatible constructor. */
    public Duration(int value, Unit unit) {
        this.components = List.of(new Component(value, unit));
    }

    /** Compound duration with multiple components, e.g. 2d4h6min. */
    public Duration(List<Component> components) {
        if (components.isEmpty()) throw new IllegalArgumentException("Duration requires at least one component");
        this.components = List.copyOf(components);
    }

    public List<Component> getComponents() {
        return components;
    }

    /** Value of the first (or only) component — kept for backward compatibility. */
    public int getValue() {
        return components.getFirst().value();
    }

    /** Unit of the first (or only) component — kept for backward compatibility. */
    public Unit getUnit() {
        return components.getFirst().unit();
    }

    public boolean isCompound() {
        return components.size() > 1;
    }

    /**
     * The spelling KQL and IQL can read back.
     *
     * <p>The DURATION token has no sign: it is {@code (DIGIT+ unit)+}. A negative {@code -2d4h} is
     * therefore a minus <em>plus</em> a duration, and the mapper folds the sign into the
     * components. Printed component-wise that would give {@code -2d-4h} -- of which the lexer would
     * read only {@code -2d}, and the rest would be a syntax error. A wholly negative duration is
     * therefore written as one leading minus in front of the positive form, which reads back
     * byte-identically. Mixed signs cannot be expressed by a literal at all.
     */
    @Override
    public String toString() {
        boolean allNegative = components.stream().allMatch(c -> c.value() < 0);
        if (allNegative) {
            return "-" + components.stream()
                    .map(c -> new Component(-c.value(), c.unit()).toString())
                    .collect(Collectors.joining());
        }
        return components.stream().map(Component::toString).collect(Collectors.joining());
    }
}
