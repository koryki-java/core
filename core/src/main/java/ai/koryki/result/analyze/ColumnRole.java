package ai.koryki.result.analyze;

/** BI role of a result column, decided by {@link Analyzer}. */
public enum ColumnRole {

    /** Calendar axis: temporal type or a time-grain bucket/part. */
    TIME,
    /** Categorical axis: grouped columns, booleans, names. */
    DIMENSION,
    /** Numeric value: aggregates, and numeric columns of detail data. */
    MEASURE,
    /** Primary-key membership: dimension-like, but never a chart measure or axis. */
    IDENTIFIER,
    /** Unchartable: blobs, free text, columns with a unique value per row. */
    OTHER
}
