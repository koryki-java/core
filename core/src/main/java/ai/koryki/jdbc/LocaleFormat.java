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

import ai.koryki.catalog.types.CoreTypeEncoding;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeFamily;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Read-layer presentation: turns a JDBC result value into a display string,
 * driven by the column's resolved {@link TypeDescriptor} (from
 * {@code ColumnInfo.getTypeDescriptor()}) and a {@link Locale}.
 *
 * <p>This is the presentation layer of docs/TEMPORAL.md — it <em>never changes
 * a value</em> (no zone conversion, no rounding beyond display scale); it only
 * chooses how to render the canonical value. SQL generation is unaffected.
 *
 * <p>A {@code null} locale selects canonical, locale-independent output (ISO
 * dates/times, plain decimals) — the deterministic mode for golden tests.
 *
 * <p>Note: currency and percentage are presentation <em>semantics</em>, not
 * base types — they need a presentation hint the {@link TypeDescriptor} does
 * not yet carry, and are intentionally out of scope here.
 */
public class LocaleFormat implements Format {



//    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
//    private static final DateTimeFormatter ISO_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
//    private static final DateTimeFormatter ISO_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter ISO_TIME = DateTimeFormatter.ISO_LOCAL_TIME;
    private static final DateTimeFormatter ISO_TIMESTAMP = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Locale locale;

    /** @param locale presentation locale, or {@code null} for canonical (ISO) output */
    public LocaleFormat(Locale locale) {
        this.locale = locale;
    }

    public String format(Object value, TypeDescriptor type) {
        if (value == null) {
            return "";
        }
        // to_text(boolean) yields TEXT, but the dialect renders it as "0"/"1" (MariaDB)
        // or "true"/"false" (DuckDB); normalize to canonical true/false. Checked before the
        // family branches because the family is TEXT.
        if (type != null && CoreTypeEncoding.TEXT_FROM_BOOLEAN.equals(type.getTypeEncoding())) {
            return bool(value);
        }

        // Storage encodings (epoch integers, seconds-of-day, a date carrying a time, ...) are
        // already decoded to canonical java.time values at the read boundary (JdbcDatabase#read);
        // here we only present the typed value, dispatched by its logical family.
        TypeFamily fam = type != null ? type.getTypeFamily() : null;

        if (CoreTypeFamily.TIME.equals(fam)) {
            LocalTime t = asTime(value);
            if (t != null) return time(t);
        } else if (CoreTypeFamily.DATE.equals(fam)) {
            LocalDate d = asDate(value);
            if (d != null) return date(d);
        } else if (CoreTypeFamily.TIMESTAMP.equals(fam)) {
            LocalDateTime dt = asDateTime(value);
            if (dt != null) return dateTime(dt);
        } else if (CoreTypeFamily.INTERVAL.equals(fam) && value instanceof Interval iv) {
            return interval(iv);
        } else if (isNumeric(fam) && value instanceof Number n) {
            return number(n, type);
        }
        return value.toString();
    }

    /** Render a boolean-encoded text ("0"/"1" or "true"/"false") as canonical true/false. */
    private static String bool(Object value) {
        String s = value.toString().trim();
        boolean b = s.equals("1") || s.equalsIgnoreCase("true") || s.equalsIgnoreCase("t");
        return Boolean.toString(b);
    }

    private String time(LocalTime t) {

//        return locale == null ? t.format(ISO_TIME)
//                : t.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale));
        return locale == null ? t.format(ISO_TIME)
                : t.format(ISO_TIME.withLocale(locale));
    }

    private String date(LocalDate d) {
//        return locale == null ? d.format(ISO_DATE)
//                : d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale));
        return locale == null ? d.format(ISO_DATE)
                : d.format(ISO_DATE.withLocale(locale));
    }

    private String dateTime(LocalDateTime dt) {
//        return locale == null ? dt.format(ISO_TIMESTAMP)
//                : dt.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale));
        return locale == null ? dt.format(ISO_TIMESTAMP)
                :dt.format(ISO_TIMESTAMP.withLocale(locale));
    }

    private String number(Number n, TypeDescriptor type) {
        BigDecimal bd = n instanceof BigDecimal b ? b : new BigDecimal(n.toString());
        int scale = type != null ? type.getScale() : -1;
        if (locale == null) {
            return scale >= 0 ? bd.setScale(scale, java.math.RoundingMode.HALF_UP).toPlainString() : bd.toPlainString();
        }
        NumberFormat nf = NumberFormat.getNumberInstance(locale);
        if (scale >= 0) {
            nf.setMinimumFractionDigits(scale);
            nf.setMaximumFractionDigits(scale);
        }
        return nf.format(bd);
    }

    /**
     * Renders an INTERVAL. The default is the canonical koryki notation ({@code 1y2mo3d4h}) — compact,
     * locale-independent, and the deterministic form golden tests rely on (inherited by StableFormat).
     * {@link WordedLocaleFormat} overrides this with a business-readable worded/{@code HH:MM:SS} form.
     */
    protected String interval(Interval iv) {
        return iv.toKql();
    }

    private static boolean isNumeric(TypeFamily fam) {
        return CoreTypeFamily.INTEGER.equals(fam)
                || CoreTypeFamily.DECIMAL.equals(fam)
                || CoreTypeFamily.FLOAT.equals(fam);
    }

    private static LocalTime asTime(Object v) {
        if (v instanceof LocalTime t) return t;
        if (v instanceof Time t) return t.toLocalTime();
        return null;
    }

    private static LocalDate asDate(Object v) {
        if (v instanceof LocalDate d) return d;
        if (v instanceof Date d) return d.toLocalDate();
        return null;
    }

    private static LocalDateTime asDateTime(Object v) {
        // Instants/epoch values are already converted to a model-zone wall-clock LocalDateTime at the
        // read boundary (CoreDecoder, driven by JdbcDatabase's model zone) — the single place that
        // applies the zone. Here we only present the (zone-neutral) wall-clock.
        if (v instanceof LocalDateTime d) return d;
        if (v instanceof Timestamp t) return t.toLocalDateTime();
        return null;
    }
}
