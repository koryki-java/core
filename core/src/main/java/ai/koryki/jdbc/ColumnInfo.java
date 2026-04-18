package ai.koryki.jdbc;

public interface ColumnInfo {

    default String toString(Object o) {
        return o != null ? o.toString() : "";
    }

    void setHeader(String header);
}
