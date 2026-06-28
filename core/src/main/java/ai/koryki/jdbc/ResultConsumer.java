package ai.koryki.jdbc;

import java.util.List;

public interface ResultConsumer<C extends ColumnInfo> extends AutoCloseable {

    default void setInfos(List<C> infos) {

    }

    default List<C>  getInfos() {
        return List.of();
    }

    /**
     * One {@link Format} for the whole result set (replaces per-column
     * {@code ColumnInfo.toString}). Concrete processors store it; the default
     * is a no-op, so a processor without one keeps the legacy
     * {@code ColumnInfo.toString(value)} path in {@code formatRow}.
     */
    default void setFormat(Format format) {

    }

    default Format getFormat() {
        return null;
    }

    default void setSql(String sql) {
        // empty
    }


    @Override
    void close() throws RuntimeException;

}
