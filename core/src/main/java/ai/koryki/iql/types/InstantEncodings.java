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

import ai.koryki.catalog.schema.types.CoreTypeEncoding;
import ai.koryki.catalog.schema.types.TypeDescriptor;
import ai.koryki.iql.query.Expression;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Comparison-operand reconciliation for an {@code INSTANT} ({@code timestamptz}) column compared against a
 * date/timestamp literal. The literal is a model-zone wall-clock value, so its absolute instant is taken by
 * interpreting it in the model zone (docs/TEMPORAL.md: comparisons convert the literal, not the column). The
 * dialect then renders that instant as its own instant literal ({@link ai.koryki.iql.SqlDialect#instantLiteral}),
 * so the comparison does not rely on the engine implicitly coercing a bare naive string — which fails on a
 * {@code timestamptz}/{@code DATETIMEOFFSET} column (SQL Server) or where the literal syntax differs (Trino).
 */
public final class InstantEncodings {

    private InstantEncodings() {
    }

    public static Optional<Instant> literalInstant(TypeDescriptor columnType, Expression operand, ZoneId modelZone) {
        if (columnType == null || !CoreTypeEncoding.INSTANT.equals(columnType.getTypeEncoding())) {
            return Optional.empty();
        }
        LocalDate date = operand.getLocalDate();
        LocalDateTime dateTime = operand.getLocalDateTime();
        if (date == null && dateTime == null) {
            return Optional.empty();   // not a date/timestamp literal — nothing to reconcile
        }
        LocalDateTime wallClock = dateTime != null ? dateTime : date.atStartOfDay();
        return Optional.of(wallClock.atZone(modelZone).toInstant());
    }
}
