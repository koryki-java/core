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
import ai.koryki.catalog.schema.types.EpochTypeEncoding;
import ai.koryki.catalog.schema.types.TypeDescriptor;
import ai.koryki.catalog.schema.types.TypeEncoding;
import ai.koryki.iql.query.Expression;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Comparison-operand reconciliation for an <em>integer-encoded</em> TIMESTAMP/DATE column compared
 * against a date/timestamp literal. An {@code EPOCH:<unit>} or {@code DATE_FROM_EPOCH_DAY} column is
 * physically an integer, so the literal is rendered as the matching integer count — keeping the column
 * bare and the filter index-friendly (the same approach as {@link IntervalEncodings#durationOperand}).
 *
 * <p>The literal is a model-zone wall-clock value, so an EPOCH count is taken by interpreting it in the
 * {@code modelZone} threaded from the renderer (docs/TEMPORAL.md: comparisons convert the literal, not the
 * column). DATE_FROM_EPOCH_DAY is a zone-free day count. A native / zone-aware (INSTANT) column is left to
 * the dialect's own literal coercion and is not reconciled here.
 */
public final class EpochEncodings {

    private EpochEncodings() {
    }

    public static Optional<String> literalOperand(TypeDescriptor columnType, Expression operand, ZoneId modelZone) {
        if (columnType == null) {
            return Optional.empty();
        }
        LocalDate date = operand.getLocalDate();
        LocalDateTime dateTime = operand.getLocalDateTime();
        if (date == null && dateTime == null) {
            return Optional.empty();   // not a date/timestamp literal — nothing to reconcile
        }
        TypeEncoding enc = columnType.getTypeEncoding();
        if (enc instanceof EpochTypeEncoding epoch) {
            Instant instant = (dateTime != null ? dateTime : date.atStartOfDay()).atZone(modelZone).toInstant();
            return Optional.of(Long.toString(epochCount(instant, epoch)));
        }
        if (CoreTypeEncoding.DATE_FROM_EPOCH_DAY.equals(enc)) {
            LocalDate day = date != null ? date : dateTime.toLocalDate();
            return Optional.of(Long.toString(day.toEpochDay()));
        }
        return Optional.empty();
    }

    private static long epochCount(Instant instant, EpochTypeEncoding epoch) {
        return switch (epoch.getUnit()) {
            case MILLIS -> instant.toEpochMilli();
            case MICROS -> instant.getEpochSecond() * 1_000_000L + instant.getNano() / 1_000L;
            case NANOS  -> instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
            default     -> instant.getEpochSecond();   // SECONDS
        };
    }
}
