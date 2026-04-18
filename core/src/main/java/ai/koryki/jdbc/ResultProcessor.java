package ai.koryki.jdbc;

import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public interface ResultProcessor<C extends ColumnInfo> extends ResultConsumer<C> {

    default void metadata(ResultSetMetaData metaData) {

    }

    boolean append(List<Object> row);


    default <O> List<String> formatRow(
            List<O> row,
            List<C> infos) {

        if (infos != null && row.size() == infos.size()) {

            BiFunction<Object, C, String> consumer = (o, i) -> i.toString(o);
            List<String> result = new ArrayList<>();
            for (int i = 0; i < row.size(); i++) {
                result.add(consumer.apply(row.get(i), infos.get(i)));
            }
            return result;
        } else {

            return row.stream().map(c -> c != null ? c.toString() : "").collect(Collectors.toList());
        }
    }
}
