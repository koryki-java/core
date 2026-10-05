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

import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.jdbc.CoreDecoder;
import ai.koryki.jdbc.Interval;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.postgresql.util.PGInterval;
import org.postgresql.util.PGobject;

/**
 * Postgres returns an {@code interval} column as a {@link PGInterval} (months, days and a
 * fractional-second time component, each independent). Convert it to the canonical {@link
 * Interval}; everything else falls through to {@link CoreDecoder}.
 *
 * <p>A composite type, the structs of the complex types, arrives as a {@link PGobject} holding its
 * text, {@code (1.5,"Main St",{a,b})}. It is read into its fields by the types the catalog gives
 * them, and each field into the Java value of its type, so that what {@link CoreDecoder} receives
 * is the same as from a driver that hands over a {@code java.sql.Struct}. Arrays come as {@code
 * java.sql.Array} and need nothing here.
 */
public class PostgresDecoder extends CoreDecoder {

    public PostgresDecoder(ZoneId zone) {
        super(zone);
    }

    @Override
    public Object decode(Object v, ColumnInfo info) {
        if (v instanceof PGInterval pg) {
            long nanos =
                    (long) pg.getHours() * 3_600_000_000_000L
                            + (long) pg.getMinutes() * 60_000_000_000L
                            + Math.round(pg.getSeconds() * 1_000_000_000d);
            return Interval.of(pg.getYears() * 12 + pg.getMonths(), pg.getDays(), nanos);
        }
        TypeDescriptor td = info != null ? info.getTypeDescriptor() : null;
        if (v instanceof PGobject o
                && td != null
                && CoreTypeFamily.STRUCT.equals(td.getTypeFamily())
                && o.getValue() != null) {
            return super.decode(struct(o.getValue(), td), info);
        }
        return super.decode(v, info);
    }

    private static Object[] struct(String text, TypeDescriptor td) {
        List<String> fields = PostgresText.record(text);
        if (fields.size() != td.children().size()) {
            throw new IllegalArgumentException(
                    "a record of "
                            + fields.size()
                            + " fields where "
                            + td.portable()
                            + " has "
                            + td.children().size());
        }
        Object[] values = new Object[fields.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = typed(fields.get(i), td.children().get(i));
        }
        return values;
    }

    /** The Java value of a field or element, from its text and the type the catalog says. */
    private static Object typed(Object raw, TypeDescriptor td) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof List<?> inner) {
            List<Object> out = new ArrayList<>();
            for (Object o : inner) {
                out.add(typed(o, td.element()));
            }
            return out;
        }
        String text = raw.toString();
        if (td.getTypeFamily() == null) {
            return text;
        }
        if (CoreTypeFamily.STRUCT.equals(td.getTypeFamily())) {
            return struct(text, td);
        } else if (CoreTypeFamily.ARRAY.equals(td.getTypeFamily())) {
            List<Object> out = new ArrayList<>();
            for (Object o : PostgresText.array(text)) {
                out.add(typed(o, td.element()));
            }
            return out;
        } else if (CoreTypeFamily.INTEGER.equals(td.getTypeFamily())) {
            return Long.valueOf(text);
        } else if (CoreTypeFamily.DECIMAL.equals(td.getTypeFamily())) {
            return new BigDecimal(text);
        } else if (CoreTypeFamily.FLOAT.equals(td.getTypeFamily())) {
            return Double.valueOf(text);
        } else if (CoreTypeFamily.BOOLEAN.equals(td.getTypeFamily())) {
            return text.equals("t") || text.equalsIgnoreCase("true");
        } else if (CoreTypeFamily.DATE.equals(td.getTypeFamily())) {
            return LocalDate.parse(text);
        } else if (CoreTypeFamily.TIMESTAMP.equals(td.getTypeFamily())) {
            return LocalDateTime.parse(text.replace(' ', 'T'));
        } else if (CoreTypeFamily.TIME.equals(td.getTypeFamily())) {
            return LocalTime.parse(text);
        }
        return text;
    }
}
