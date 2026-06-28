package ai.koryki.jdbc;

public interface TypeDecoder {

     Object decode(Object v, ColumnInfo info);

}
