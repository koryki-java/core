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
package ai.koryki.iql.typing;

import ai.koryki.catalog.types.CoreTypeEncoding;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeEncoding;
import ai.koryki.iql.query.Expression;
import java.util.Optional;

/**
 * Rendering support for column-level BOOLEAN encodings, the comparison dual of {@code
 * booleanPredicate}. A boolean column may physically hold 0/1 ({@code BOOLEAN_FROM_INTEGER}) or
 * 'Y'/'N' ({@code BOOLEAN_FROM_TEXT}); a {@code TRUE} or {@code FALSE} literal compared with it
 * must be written the way the column stores it, so both operands share one physical representation:
 * {@code c.flag = TRUE} is {@code c.flag = 1} on the first and {@code c.flag = 'Y'} on the second.
 *
 * <p>A native boolean column gives empty: the dialect writes the literal itself.
 */
public final class BooleanEncodings {

    private BooleanEncodings() {}

    /**
     * {@code operand} (a boolean literal) in {@code columnType}'s storage encoding, or empty when
     * it does not apply: not a boolean literal, not a BOOLEAN column, or a native one.
     */
    public static Optional<String> literalOperand(TypeDescriptor columnType, Expression operand) {
        if (columnType == null
                || operand.getBool() == null
                || !CoreTypeFamily.BOOLEAN.equals(columnType.getTypeFamily())) {
            return Optional.empty();
        }
        TypeEncoding enc = columnType.getTypeEncoding();
        boolean value = operand.getBool();
        if (CoreTypeEncoding.BOOLEAN_FROM_INTEGER.equals(enc)) {
            return Optional.of(value ? "1" : "0");
        }
        if (CoreTypeEncoding.BOOLEAN_FROM_TEXT.equals(enc)) {
            return Optional.of(value ? "'Y'" : "'N'");
        }
        return Optional.empty();
    }
}
