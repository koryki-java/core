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
package ai.koryki.postgresql;

import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.jdbc.CoreDecoder;
import ai.koryki.jdbc.Interval;

import org.postgresql.util.PGInterval;

import java.time.ZoneId;

/**
 * Postgres returns an {@code interval} column as a {@link PGInterval} (months,
 * days and a fractional-second time component, each independent). Convert it to
 * the canonical {@link Interval}; everything else falls through to {@link CoreDecoder}.
 */
public class PostgresDecoder extends CoreDecoder {

    public PostgresDecoder(ZoneId zone) {
        super(zone);
    }

    @Override
    public Object decode(Object v, ColumnInfo info) {
        if (v instanceof PGInterval pg) {
            long nanos = (long) pg.getHours()   * 3_600_000_000_000L
                       + (long) pg.getMinutes() *     60_000_000_000L
                       + Math.round(pg.getSeconds() * 1_000_000_000d);
            return Interval.of(pg.getYears() * 12 + pg.getMonths(), pg.getDays(), nanos);
        }
        return super.decode(v, info);
    }
}
