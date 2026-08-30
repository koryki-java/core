package ai.koryki.jdbc;

import ai.koryki.catalog.types.TypeDescriptor;

/**
 * Result-set-wide value&rarr;string strategy: a pure function of the value and
 * its resolved {@link TypeDescriptor}. One instance formats every column (the
 * locale/strategy is global to the result set), set once via
 * {@link ResultConsumer#setValueFormat}. The replacement for per-column formatting
 * baked into {@code ColumnInfo.toString(Object)}.
 */
public interface ValueFormat {

    String format(Object value, TypeDescriptor type);

    /**
     * The column-aware form, which is what {@link ResultProcessor#formatRow} calls: the column info
     * carries more than its type — a {@code Presentation} says how the values should read, and the
     * SQL type cannot.
     *
     * <p>A default rather than a second abstract method on purpose. Every existing implementation
     * overrides only {@code format(value, type)}, this delegates there virtually, so their behaviour
     * is unchanged — including {@code StableFormat}, whose normalisation the CSV goldens of eight
     * dialects depend on. A presentation-aware formatter overrides this one instead.
     *
     * <p>A separate name rather than an overload: {@code format(value, null)} would be ambiguous
     * between {@code TypeDescriptor} and {@code ColumnInfo}, which are unrelated reference types.
     * Two existing call sites hit that the moment the overload existed.
     */
    default String formatColumn(Object value, ColumnInfo info) {
        return format(value, info == null ? null : info.getTypeDescriptor());
    }
}
