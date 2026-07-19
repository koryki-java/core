package ai.koryki.jdbc;

import ai.koryki.catalog.types.TypeDescriptor;

public interface ColumnInfo {

    default String toString(Object o) {
        return o != null ? o.toString() : "";
    }

    void setHeader(String header);

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
}
