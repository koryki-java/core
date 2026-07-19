package ai.koryki.jdbc;

import ai.koryki.catalog.types.TypeDescriptor;

/**
 * Result-set-wide value&rarr;string strategy: a pure function of the value and
 * its resolved {@link TypeDescriptor}. One instance formats every column (the
 * locale/strategy is global to the result set), set once via
 * {@link ResultConsumer#setFormat}. The replacement for per-column formatting
 * baked into {@code ColumnInfo.toString(Object)}.
 */
public interface Format {

    String format(Object value, TypeDescriptor type);
}
