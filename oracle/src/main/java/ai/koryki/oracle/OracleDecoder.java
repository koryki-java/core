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
package ai.koryki.oracle;

import ai.koryki.catalog.schema.types.CoreTypeEncoding;
import ai.koryki.catalog.schema.types.TypeDescriptor;
import ai.koryki.catalog.schema.types.TypeEncoding;
import ai.koryki.jdbc.ColumnInfo;
import ai.koryki.jdbc.CoreDecoder;
import ai.koryki.jdbc.Interval;

import oracle.sql.INTERVALDS;
import oracle.sql.INTERVALYM;

import java.time.ZoneId;

/**
 * Oracle has two native interval types: INTERVAL YEAR TO MONTH (read as
 * {@link INTERVALYM}) and INTERVAL DAY TO SECOND (read as {@link INTERVALDS}).
 * Both expose only {@code stringValue()}, so parse that:
 * <ul>
 *   <li>YEAR TO MONTH: {@code "[-]Y-M"} (e.g. {@code "2-3"}, {@code "-1-6"})</li>
 *   <li>DAY TO SECOND: {@code "[-]D H:M:S[.fffffffff]"} (e.g. {@code "4 5:6:7.0"})</li>
 * </ul>
 * Depending on the type map a column may instead arrive as a {@code String} in
 * the same format; the column encoding then selects the parser. Everything else
 * falls through to {@link CoreDecoder}.
 */
public class OracleDecoder extends CoreDecoder {

    public OracleDecoder(ZoneId zone) {
        super(zone);
    }

    @Override
    public Object decode(Object v, ColumnInfo info) {
        if (v instanceof INTERVALYM ym) return parseYearMonth(ym.stringValue());
        if (v instanceof INTERVALDS ds) return parseDaySecond(ds.stringValue());
        if (v instanceof String s) {
            TypeEncoding enc = encoding(info);
            if (CoreTypeEncoding.INTERVAL_YEAR_MONTH.equals(enc)) return parseYearMonth(s);
            if (CoreTypeEncoding.INTERVAL_DAY_SECOND.equals(enc)) return parseDaySecond(s);
        }
        return super.decode(v, info);
    }

    private static TypeEncoding encoding(ColumnInfo info) {
        TypeDescriptor td = info != null ? info.getTypeDescriptor() : null;
        return td != null ? td.getTypeEncoding() : null;
    }

    /** {@code "[-]Y-M"} (e.g. {@code "2-3"}, {@code "-1-6"}) -> {@link Interval}. */
    static Interval parseYearMonth(String s) {
        s = s.trim();
        int sign = 1;
        if (s.startsWith("-"))      { sign = -1; s = s.substring(1); }
        else if (s.startsWith("+")) {            s = s.substring(1); }
        int dash = s.indexOf('-');
        int years  = Integer.parseInt(s.substring(0, dash).trim());
        int months = Integer.parseInt(s.substring(dash + 1).trim());
        return Interval.ofMonths(sign * (years * 12 + months));
    }

    /** {@code "[-]D H:M:S[.fffffffff]"} (e.g. {@code "4 5:6:7.0"}) -> {@link Interval}. */
    static Interval parseDaySecond(String s) {
        s = s.trim();
        int sign = 1;
        if (s.startsWith("-"))      { sign = -1; s = s.substring(1); }
        else if (s.startsWith("+")) {            s = s.substring(1); }
        int sp = s.indexOf(' ');
        int days = Integer.parseInt(s.substring(0, sp).trim());
        String[] hms = s.substring(sp + 1).trim().split(":");
        long nanos = Long.parseLong(hms[0].trim()) * 3_600_000_000_000L
                   + Long.parseLong(hms[1].trim()) *     60_000_000_000L;
        String sec = hms[2].trim();
        int dot = sec.indexOf('.');
        if (dot < 0) {
            nanos += Long.parseLong(sec) * 1_000_000_000L;
        } else {
            nanos += Long.parseLong(sec.substring(0, dot)) * 1_000_000_000L;
            String frac = (sec.substring(dot + 1) + "000000000").substring(0, 9);
            nanos += Long.parseLong(frac);
        }
        return Interval.of(0, sign * days, sign * nanos);
    }
}
