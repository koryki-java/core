package ai.koryki.jdbc;

import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.presentation.Presentation;

/**
 * What the read layer knows about one output column: its name, its resolved type, and how its values
 * should read.
 *
 * <p>This is the reader family's own vocabulary — {@code ResultConsumer}, {@code ResultProcessor},
 * {@code ListResult}, {@code CSVFileResult} and {@code XMLFileResult} are all bounded by it — which
 * is why it belongs here. It once moved to {@code ai.koryki.result} on the theory that a column
 * description is a result description; the measurement disagreed. It depends on nothing in that
 * package, and of its three implementations only {@code Finding} lives there — the other two are
 * {@code kql.HeaderInfo} and the test harness's {@code StableFormatInfo}.
 */
public interface ColumnInfo {

    default String toString(Object o) {
        return o != null ? o.toString() : "";
    }

    void setHeader(String header);

    /** The name this column answers under; null when it has none. Counterpart to setHeader. */
    default String getHeader() {
        return null;
    }

    /**
     * Resolved logical type of this output column (the type of the FETCH
     * expression, including computed ones). Enables type-driven decode and
     * locale-aware presentation at the read boundary. Default no-ops keep
     * existing implementations valid; {@code null} = type unknown.
     */
    default void setTypeDescriptor(TypeDescriptor type) {
    }

    default TypeDescriptor getTypeDescriptor() {
        return null;
    }

    /**
     * How this column's values should read — decimal places, significant digits, and later
     * percentages or addresses. {@code null} = nothing concluded, render at full precision.
     *
     * <p>Separate from {@link #getTypeDescriptor()} because it is a different statement: the type
     * says what a value <em>is</em>, the presentation how it should be <em>shown</em>. Folding a
     * display scale into the type would make the type lie, and eight dialects' goldens read it.
     */
    default Presentation getPresentation() {
        return null;
    }
}
