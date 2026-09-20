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
package ai.koryki.presentation;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * A number written out as itself: no grouping, no rounding. Spelled {@code PLAIN}.
 *
 * <p>For numbers that are <em>names</em> rather than quantities. Order 10248 is not "10,248", and
 * the year 2026 is not "2,026" — a thousands separator says "this is a magnitude", and for an
 * identifier or a calendar part that is simply false. Measured on the demo UI: switching cells onto
 * the formatter turned every order id into a grouped number, which reads as a quantity a hundred
 * times smaller than it is.
 *
 * <p>The rest of the presentation family answers "how precise"; this one answers "not a quantity at
 * all", which is why it takes no parameter.
 */
public final class PlainPresentation implements Presentation {

    public static final String NAME = "PLAIN";

    public static final PlainPresentation INSTANCE = new PlainPresentation();

    private PlainPresentation() {}

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String render(Object value, Locale locale) {
        if (!(value instanceof Number n)) {
            return null;
        }
        // Locale-independent by intent, not by omission: a name reads the same everywhere.
        return n instanceof BigDecimal b ? b.stripTrailingZeros().toPlainString() : n.toString();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PlainPresentation;
    }

    @Override
    public int hashCode() {
        return NAME.hashCode();
    }

    @Override
    public String toString() {
        return NAME;
    }
}
