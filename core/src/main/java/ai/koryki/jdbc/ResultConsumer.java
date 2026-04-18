package ai.koryki.jdbc;

import java.util.List;

public interface ResultConsumer<C extends ColumnInfo> extends AutoCloseable {

    default void setInfos(List<C> infos) {

    }

    @Override
    void close() throws RuntimeException;

}
