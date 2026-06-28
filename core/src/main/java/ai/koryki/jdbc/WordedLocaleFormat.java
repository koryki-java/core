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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Business-facing presentation: a {@link LocaleFormat} that renders an INTERVAL in human-readable
 * form for users without SQL/IT background (docs/TEMPORAL.md). Numbers/dates/times use the inherited
 * locale-aware rendering; a duration becomes its variable calendar fields (year/month/day) as words,
 * then the fixed clock remainder (hours/minutes/seconds) as {@code HH:MM:SS}:
 *
 * <pre>
 *   1h2min3s             → 01:02:03
 *   1y2mo                → 1 year 2 months          (WIDE)  / 1 yr 2 mos   (SHORT) / 1y 2mo (NARROW)
 *   1y2mo1d + 1h2min3s   → 1 year 2 months 1 day 01:02:03
 * </pre>
 *
 * <p>Two orthogonal axes (mirroring ICU's MeasureFormat): the {@link Locale} selects the unit words
 * (from a {@code duration[_lang].properties} {@link ResourceBundle}; English is the bundled default),
 * and {@link Width} selects how those words are abbreviated. {@code maxUnits > 0} caps the output to
 * that many most-significant parts (the clock counts as one part), truncating the rest.
 *
 * <p>A day stays a calendar day (no 24h rollup), the clock shows whole seconds (sub-second rounded),
 * and negatives get a leading {@code -}. Base {@link LocaleFormat} keeps the canonical {@code toKql()}.
 */
public class WordedLocaleFormat extends LocaleFormat {

    /** How much to abbreviate the calendar unit words (the clock part is always {@code HH:MM:SS}). */
    public enum Width { WIDE, SHORT, NARROW }

    private static final String BUNDLE = "ai.koryki.jdbc.duration";

    private final Width width;
    private final int maxUnits;
    private final transient ResourceBundle words;

    public WordedLocaleFormat(Locale locale, Width width) {
        this(locale, width, 0);
    }

    /**
     * @param width    calendar-word abbreviation level
     * @param maxUnits cap to this many most-significant parts (e.g. 2 → "1 year 2 months"); {@code <= 0} = no cap
     */
    public WordedLocaleFormat(Locale locale, Width width, int maxUnits) {
        super(locale);
        this.width = width;
        this.maxUnits = maxUnits;
        this.words = ResourceBundle.getBundle(BUNDLE, locale != null ? locale : Locale.ROOT);
    }

    /** WIDE, no precision cap. */
    public static WordedLocaleFormat wide(Locale locale) {
        return new WordedLocaleFormat(locale, Width.WIDE);
    }

    /** SHORT, no precision cap. (Named {@code compact} because {@code short} is a Java keyword.) */
    public static WordedLocaleFormat compact(Locale locale) {
        return new WordedLocaleFormat(locale, Width.SHORT);
    }

    @Override
    protected String interval(Interval iv) {
        if (iv.isZero()) {
            return "00:00:00";
        }
        boolean negative = iv.getMonths() < 0 || iv.getDays() < 0 || iv.getNanos() < 0;
        Interval a = negative ? iv.negated() : iv;
        int years  = a.getMonths() / 12;
        int months = a.getMonths() % 12;
        int days   = a.getDays();

        String sep = width == Width.NARROW ? "" : " ";   // narrow joins count+word (1y), wide/short space them
        List<String> parts = new ArrayList<>();
        if (years  != 0) parts.add(years  + sep + word("year",  years));
        if (months != 0) parts.add(months + sep + word("month", months));
        if (days   != 0) parts.add(days   + sep + word("day",   days));

        long totalSeconds = Math.round(a.getNanos() / 1_000_000_000.0);
        if (totalSeconds != 0) {
            parts.add(String.format("%02d:%02d:%02d",
                    totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60));
        }
        if (parts.isEmpty()) {   // calendar empty and the clock rounded to zero (sub-second only)
            return (negative ? "-" : "") + "00:00:00";
        }
        if (maxUnits > 0 && parts.size() > maxUnits) {
            parts = parts.subList(0, maxUnits);   // keep the most-significant parts; truncate the rest
        }
        return (negative ? "-" : "") + String.join(" ", parts);
    }

    /** Localized unit word for {@code count} of {@code unit}, keyed {@code <width>.<unit>.<one|other>}. */
    private String word(String unit, int count) {
        return words.getString(width.name().toLowerCase(Locale.ROOT) + "." + unit + "." + (count == 1 ? "one" : "other"));
    }
}
