package ai.koryki.result.analyze;

/**
 * Data statistics of one result column, computed by scanning the rows.
 * {@code exact} is false when the scan was capped and the counts are lower
 * bounds only.
 */
public record ColumnStats(long distinctCount, long nullCount, Object min, Object max, boolean exact) {
}
