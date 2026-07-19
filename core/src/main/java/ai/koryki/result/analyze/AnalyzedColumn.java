package ai.koryki.result.analyze;

import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.result.Finding;
import ai.koryki.result.quantity.Quantity;

/** One result column with its BI role and data statistics. */
public record AnalyzedColumn(int index, Finding finding, ColumnRole role, ColumnStats stats) {

    public String header() {
        return finding.getHeader();
    }

    /** Never null - an unannotated column reads as UNKNOWN. */
    public Quantity quantity() {
        return finding.getQuantity() != null ? finding.getQuantity() : Quantity.UNKNOWN;
    }

    /** Localized metric name concluded from the expression shape, null when none. */
    public String metric() {
        return finding.getMetric();
    }

    public String grain() {
        return finding.getGrain();
    }

    public boolean ordinalTemporal() {
        return finding.isOrdinalTemporal();
    }

    /**
     * Column holds real temporal values (dates render on a temporal scale).
     * False for part extractors like month(x) - they are TIME by role but
     * INTEGER by value.
     */
    public boolean temporalValues() {
        return Analyzer.isTemporalType(finding);
    }

    public boolean numeric() {
        return Analyzer.isNumericType(finding);
    }

    /** Vega-friendly value class for parse hints: number, date, boolean or string. */
    public String parseHint() {
        if (numeric()) {
            return "number";
        }
        if (temporalValues()) {
            return "date";
        }
        if (Analyzer.family(finding) == CoreTypeFamily.BOOLEAN || finding.getFallbackFamily() == CoreTypeFamily.BOOLEAN) {
            return "boolean";
        }
        return "string";
    }
}
