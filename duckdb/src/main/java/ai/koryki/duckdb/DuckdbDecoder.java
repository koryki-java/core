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
package ai.koryki.duckdb;

import ai.koryki.catalog.types.CoreTypeEncoding;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.IntervalStringEncoding;
import ai.koryki.catalog.types.IntervalTypeEncoding;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeEncoding;
import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.jdbc.CoreDecoder;
import ai.koryki.jdbc.Interval;

import java.time.ZoneId;

/**
 * DuckDB has a single INTERVAL type (months + days + microseconds). Its JDBC
 * driver has no interval value class, so any INTERVAL value — a native interval
 * column or a bare duration literal/expression rendered with {@code to_years(...)}
 * etc. — reads back as the verbose string DuckDB renders, e.g.
 * {@code "1 year 2 months 3 days 04:05:06.789"}, {@code "4 days 05:06:07"} or
 * {@code "00:01:30"}. Parse that to the canonical {@link Interval}.
 *
 * <p>The only INTERVAL-family values <em>not</em> read as a DuckDB verbose string are
 * the non-native encodings — a numeric count ({@code INTERVAL:<unit>}) or an ISO/clock
 * text ({@code INTERVAL_FROM_STRING}) — which {@link CoreDecoder} decodes instead.
 */
public class DuckdbDecoder extends CoreDecoder {

    public DuckdbDecoder(ZoneId zone) {
        super(zone);
    }

    @Override
    public Object decode(Object v, ColumnInfo info) {
        if (v instanceof String s && isNativeInterval(info)) {
            return parseVerbose(s);
        }
        return super.decode(v, info);
    }

    /** A DuckDB-native INTERVAL value: the INTERVAL family minus the encodings CoreDecoder owns. */
    private static boolean isNativeInterval(ColumnInfo info) {
        TypeDescriptor td = info != null ? info.getTypeDescriptor() : null;
        if (td == null || !CoreTypeFamily.INTERVAL.equals(td.getTypeFamily())) {
            return false;
        }
        TypeEncoding enc = td.getTypeEncoding();
        return !(enc instanceof IntervalTypeEncoding
              || enc instanceof IntervalStringEncoding
              || CoreTypeEncoding.INTERVAL_FROM_STRING.equals(enc)
              || CoreTypeEncoding.CALENDAR_DISTANCE.equals(enc));
    }

    /** {@code "[N year[s]] [N month[s]] [N day[s]] [HH:MM:SS[.ffffff]]"} -> {@link Interval}. */
    static Interval parseVerbose(String s) {
        s = s.trim();
        if (s.isEmpty()) {
            return Interval.ZERO;
        }
        int months = 0, days = 0;
        long nanos = 0L;
        String[] tok = s.split("\\s+");
        for (int i = 0; i < tok.length; i++) {
            String t = tok[i];
            if (t.indexOf(':') >= 0) {                       // HH:MM:SS[.f] time component
                nanos += parseClock(t);
                continue;
            }
            long n = Long.parseLong(t);                      // a count, unit word follows
            String unit = i + 1 < tok.length ? tok[++i].toLowerCase() : "";
            if      (unit.startsWith("year")) months += (int) (n * 12);
            else if (unit.startsWith("mon"))  months += (int) n;
            else if (unit.startsWith("week")) days   += (int) (n * 7);
            else if (unit.startsWith("day"))  days   += (int) n;
            else if (unit.startsWith("hour")) nanos  += n * 3_600_000_000_000L;
            else if (unit.startsWith("min"))  nanos  += n *     60_000_000_000L;
            else if (unit.startsWith("sec"))  nanos  += n *      1_000_000_000L;
        }
        return Interval.of(months, days, nanos);
    }

    /** {@code "[-]HH:MM:SS[.fffffffff]"} -> signed nanoseconds. */
    private static long parseClock(String t) {
        int sign = 1;
        if (t.startsWith("-")) { sign = -1; t = t.substring(1); }
        String[] p = t.split(":");
        long nanos = Long.parseLong(p[0]) * 3_600_000_000_000L;
        if (p.length > 1) nanos += Long.parseLong(p[1]) * 60_000_000_000L;
        if (p.length > 2) {
            String sec = p[2];
            int dot = sec.indexOf('.');
            if (dot < 0) {
                nanos += Long.parseLong(sec) * 1_000_000_000L;
            } else {
                nanos += Long.parseLong(sec.substring(0, dot)) * 1_000_000_000L;
                String frac = (sec.substring(dot + 1) + "000000000").substring(0, 9);
                nanos += Long.parseLong(frac);
            }
        }
        return sign * nanos;
    }
}
