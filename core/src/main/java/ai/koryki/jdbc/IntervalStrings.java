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
package ai.koryki.jdbc;

/**
 * Parses the two SQL-standard interval spellings a driver may hand back as plain text:
 * {@code "[-]Y-M"} for YEAR TO MONTH and {@code "[-]D H:M:S[.fff]"} for DAY TO SECOND.
 *
 * <p>Shared because two dialects meet the same strings. Oracle has parsed them since its decoder
 * existed; Trino returns exactly the same shapes ({@code "3-6"}, {@code "0 01:02:03.000"}) and had
 * no decoder at all, so its intervals reached the result as raw driver text while every other
 * engine produced a koryki {@link Interval}. That was visible only as two fixtures whose
 * {@code ignore=trino} marker said "results differ" — they did not differ in value, only in
 * spelling.
 *
 * <p>Which of the two a string is can be read off its shape without knowing the column's type: a
 * YEAR TO MONTH value is digits, one dash, digits; a DAY TO SECOND value always carries a space and
 * colons. {@link #parse} uses that, so a caller with no sub-family information can still decode.
 */
public final class IntervalStrings {

    private IntervalStrings() {
    }

    /** Decodes either spelling, telling them apart by shape; null when the text is neither. */
    public static Interval parse(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.indexOf(':') >= 0 && t.indexOf(' ') >= 0) {
            return parseDaySecond(t);
        }
        String body = t.startsWith("-") || t.startsWith("+") ? t.substring(1) : t;
        int dash = body.indexOf('-');
        if (dash > 0 && body.indexOf(' ') < 0 && digitsOnly(body.substring(0, dash))
                && digitsOnly(body.substring(dash + 1))) {
            return parseYearMonth(t);
        }
        return null;
    }

    private static boolean digitsOnly(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** {@code "[-]Y-M"} (e.g. {@code "2-3"}, {@code "-1-6"}) -> {@link Interval}. */
    public static Interval parseYearMonth(String s) {
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
    public static Interval parseDaySecond(String s) {
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
