package ai.koryki.result.analyze;

import java.util.List;

/**
 * Result-level analytic model: all columns with roles and stats, plus the
 * shape facts the chart chooser keys on.
 */
public record ResultAnalysis(List<AnalyzedColumn> columns, int rowCount, boolean aggregated) {

    public List<AnalyzedColumn> measures() {
        return byRole(ColumnRole.MEASURE);
    }

    public List<AnalyzedColumn> timeColumns() {
        return byRole(ColumnRole.TIME);
    }

    public List<AnalyzedColumn> nominalDimensions() {
        return byRole(ColumnRole.DIMENSION);
    }

    public List<AnalyzedColumn> identifiers() {
        return byRole(ColumnRole.IDENTIFIER);
    }

    private List<AnalyzedColumn> byRole(ColumnRole role) {
        return columns.stream().filter(c -> c.role() == role).toList();
    }
}
