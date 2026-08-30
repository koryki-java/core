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
package ai.koryki.trino;

import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.jdbc.CoreDecoder;
import ai.koryki.jdbc.Interval;
import ai.koryki.jdbc.IntervalStrings;

import java.time.ZoneId;

/**
 * Turns Trino's textual intervals into a koryki {@link Interval}.
 *
 * <p>The Trino JDBC driver hands an {@code INTERVAL YEAR TO MONTH} back as {@code "3-6"} and an
 * {@code INTERVAL DAY TO SECOND} as {@code "0 01:02:03.000"} — the SQL-standard spellings, and the
 * same ones Oracle produces. Without a decoder those strings travelled all the way into the result,
 * so a query answering {@code 1h2min3s} on seven dialects answered {@code 0 01:02:03.000} here. The
 * value was never wrong, only its spelling, which is why the two fixtures carried an
 * {@code ignore=trino} marker claiming the results differ.
 *
 * <p>Everything else is the core decoder's business; this only converts what the driver leaves as
 * vendor text.
 */
public class TrinoDecoder extends CoreDecoder {

    public TrinoDecoder(ZoneId zone) {
        super(zone);
    }

    @Override
    public Object decode(Object v, ColumnInfo info) {
        TypeDescriptor td = info != null ? info.getTypeDescriptor() : null;
        // The driver hands back its own TrinoIntervalDayTime / TrinoIntervalYearMonth, not a
        // String -- but their toString() is exactly the standard spelling, so the text form is
        // the portable way in without compiling against driver classes. Anything CoreDecoder
        // already understands is left to it, and a shape it does not recognise falls through.
        if (td != null && CoreTypeFamily.INTERVAL.equals(td.getTypeFamily()) && v != null
                && !(v instanceof Interval) && !(v instanceof java.time.Period)
                && !(v instanceof java.time.Duration) && !(v instanceof Number)) {
            Interval parsed = IntervalStrings.parse(v.toString());
            if (parsed != null) {
                return parsed;
            }
        }
        return super.decode(v, info);
    }
}
