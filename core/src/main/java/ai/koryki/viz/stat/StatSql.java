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
package ai.koryki.viz.stat;

import java.math.BigDecimal;

/** Small SQL-literal helpers shared by the statistical transforms. */
final class StatSql {

    private StatSql() {
    }

    static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    /**
     * A SQL DOUBLE literal. The {@code E0} suffix forces floating-point parsing:
     * without it engines read {@code 91.60…} as a high-scale DECIMAL and the
     * downstream arithmetic overflows (e.g. DuckDB {@code DECIMAL(18)}).
     */
    static String lit(double d) {
        return BigDecimal.valueOf(d).toPlainString() + "E0";
    }
}
