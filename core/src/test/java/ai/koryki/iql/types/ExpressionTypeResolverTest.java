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
package ai.koryki.iql.types;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.iql.query.Expression;
import ai.koryki.iql.typing.ExpressionTypeResolver;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/**
 * Numeric-literal type inference:
 *
 * <ul>
 *   <li>Phase 2 — a decimal literal carries its own precision/scale (12.34 -&gt; DECIMAL(4,2)).
 *   <li>an integer literal resolves to the INTEGER family.
 * </ul>
 *
 * The number-literal branch of {@code resolve} uses none of the resolver's collaborators, so they
 * can be null here.
 */
class ExpressionTypeResolverTest {

    private static final ExpressionTypeResolver RESOLVER =
            new ExpressionTypeResolver(null, null, null);

    private static TypeDescriptor resolveNumber(Number n) {
        Expression e = new Expression();
        e.setNumber(n);
        return RESOLVER.resolve(e);
    }

    @Test
    void decimalLiteralCarriesPrecisionAndScale() {
        TypeDescriptor d = resolveNumber(new BigDecimal("12.34"));
        assertEquals(CoreTypeFamily.DECIMAL, d.getTypeFamily());
        assertEquals(4, d.getPrecision());
        assertEquals(2, d.getScale());
    }

    @Test
    void integerLiteralIsIntegerFamily() {
        TypeDescriptor d = resolveNumber(new BigInteger("42"));
        assertEquals(CoreTypeFamily.INTEGER, d.getTypeFamily());
    }

    @Test
    void nullLiteralResolvesToNullType() {
        Expression e = new Expression();
        e.setNull(true);
        assertEquals(TypeDescriptor.NULL, RESOLVER.resolve(e));
    }
}
