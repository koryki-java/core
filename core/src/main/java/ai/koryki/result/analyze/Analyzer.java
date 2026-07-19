package ai.koryki.result.analyze;

import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeFamily;
import ai.koryki.result.Finding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the result-level analytic model after execution: column roles from
 * the query-time Finding signals plus data statistics from the rows (role
 * rules that need cardinality cannot run at query time).
 *
 * <p>Role rules, ordered, first match wins:
 * <ol>
 *   <li>aggregate -> MEASURE</li>
 *   <li>key -> IDENTIFIER</li>
 *   <li>temporal type -> TIME</li>
 *   <li>time grain set (integer-typed month(x) etc.) -> TIME</li>
 *   <li>grouped -> DIMENSION</li>
 *   <li>boolean or text -> DIMENSION; text unique per row on larger results -> OTHER</li>
 *   <li>numeric: detail data (no aggregates anywhere) -> MEASURE, else DIMENSION</li>
 *   <li>anything else -> OTHER</li>
 * </ol>
 */
public class Analyzer {

    /** Stats scan cap - beyond this, counts are lower bounds and exact=false. */
    private static final int SCAN_LIMIT = 10_000;

    /** From this row count on, an all-unique text column reads as free text, not a dimension. */
    private static final int UNIQUE_TEXT_LIMIT = 30;

    public ResultAnalysis analyze(ai.koryki.jdbc.ListResult<Finding> result) {
        List<Finding> infos = result.getInfos() != null ? result.getInfos() : List.of();
        List<List<Object>> rows = result.getRows();
        boolean aggregated = infos.stream().anyMatch(Finding::isAggregate);

        List<AnalyzedColumn> columns = new ArrayList<>();
        for (int i = 0; i < infos.size(); i++) {
            Finding f = infos.get(i);
            ColumnStats stats = stats(rows, i);
            columns.add(new AnalyzedColumn(i, f, role(f, stats, aggregated, rows.size()), stats));
        }
        return new ResultAnalysis(List.copyOf(columns), rows.size(), aggregated);
    }

    private ColumnRole role(Finding f, ColumnStats stats, boolean aggregated, int rowCount) {
        if (f.isAggregate()) {
            return ColumnRole.MEASURE;
        }
        if (f.isKey()) {
            return ColumnRole.IDENTIFIER;
        }
        if (isTemporalType(f) || f.getGrain() != null) {
            return ColumnRole.TIME;
        }
        if (f.isGrouped()) {
            return ColumnRole.DIMENSION;
        }
        if (isBooleanType(f)) {
            return ColumnRole.DIMENSION;
        }
        if (isTextType(f)) {
            if (rowCount >= UNIQUE_TEXT_LIMIT && stats.distinctCount() == Math.min(rowCount, SCAN_LIMIT)) {
                return ColumnRole.OTHER;
            }
            return ColumnRole.DIMENSION;
        }
        if (isNumericType(f)) {
            // GroupRule grouped every non-aggregate output of an aggregated
            // result, so the residual numeric case is detail data - scatter
            // material - or a leftover of an aggregated shape.
            return aggregated ? ColumnRole.DIMENSION : ColumnRole.MEASURE;
        }
        return ColumnRole.OTHER;
    }

    private static ColumnStats stats(List<List<Object>> rows, int index) {
        Set<Object> distinct = new HashSet<>();
        long nulls = 0;
        Comparable<Object> min = null;
        Comparable<Object> max = null;
        int n = Math.min(rows.size(), SCAN_LIMIT);
        for (int r = 0; r < n; r++) {
            List<Object> row = rows.get(r);
            Object v = index < row.size() ? row.get(index) : null;
            if (v == null) {
                nulls++;
                continue;
            }
            distinct.add(v);
            if (v instanceof Comparable<?> c && (min == null || min.getClass() == v.getClass())) {
                try {
                    @SuppressWarnings("unchecked")
                    Comparable<Object> cv = (Comparable<Object>) c;
                    if (min == null || cv.compareTo(min) < 0) {
                        min = cv;
                    }
                    if (max == null || cv.compareTo(max) > 0) {
                        max = cv;
                    }
                } catch (RuntimeException e) {
                    // incomparable values - leave min/max as they are
                }
            }
        }
        return new ColumnStats(distinct.size(), nulls, min, max, rows.size() <= SCAN_LIMIT);
    }

    static TypeFamily family(Finding f) {
        return f.getTypeDescriptor() != null ? f.getTypeDescriptor().getTypeFamily() : null;
    }

    static boolean isTemporalType(Finding f) {
        TypeFamily t = family(f);
        if (t == CoreTypeFamily.DATE || t == CoreTypeFamily.TIME || t == CoreTypeFamily.TIMESTAMP) {
            return true;
        }
        TypeFamily g = f.getFallbackFamily();
        return g == CoreTypeFamily.DATE || g == CoreTypeFamily.TIMESTAMP || g == CoreTypeFamily.TIME;
    }

    static boolean isNumericType(Finding f) {
        TypeFamily t = family(f);
        if (t == CoreTypeFamily.INTEGER || t == CoreTypeFamily.DECIMAL || t == CoreTypeFamily.FLOAT) {
            return true;
        }
        if (t != null) {
            // the descriptor is authoritative: a math header may fall back to varchar
            return false;
        }
        TypeFamily g = f.getFallbackFamily();
        return g == CoreTypeFamily.INTEGER || g == CoreTypeFamily.FLOAT || g == CoreTypeFamily.DECIMAL;
    }

    private static boolean isBooleanType(Finding f) {
        return family(f) == CoreTypeFamily.BOOLEAN || f.getFallbackFamily() == CoreTypeFamily.BOOLEAN;
    }

    private static boolean isTextType(Finding f) {
        TypeFamily t = family(f);
        if (t == CoreTypeFamily.TEXT || t == CoreTypeFamily.UUID) {
            return true;
        }
        return t == null && f.getFallbackFamily() == CoreTypeFamily.TEXT;
    }
}
