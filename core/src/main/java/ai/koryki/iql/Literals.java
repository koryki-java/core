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
package ai.koryki.iql;

import ai.koryki.antlr.KorykiaiException;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Literal formatting shared by the SQL renderers and the IQL serializer, so the two never drift. */
final class Literals {

    private Literals() {
    }

    /**
     * Canonical text form of a numeric literal: integers verbatim, decimals with trailing zeros
     * dropped (0.0 → 0) but full precision kept. Used by both {@code SqlSelectRenderer} (→ SQL)
     * and {@code IQLSerializer} (→ IQL); the surrounding quoting/typing differs, this core does not.
     */
    static String number(Number n) {
        if (n instanceof BigInteger bigInteger) {
            return bigInteger.toString();
        } else if (n instanceof BigDecimal bigDecimal) {
            return bigDecimal.stripTrailingZeros().toPlainString();
        } else {
            throw new KorykiaiException("unsupported number type: " + n.getClass());
        }
    }
}
