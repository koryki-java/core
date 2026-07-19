package ai.koryki.result.analyze;

import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.result.Finding;
import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.result.ListResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AnalyzerTest {

    @Test
    public void aggregateIsMeasure() {
        Finding f = finding("Sum(x)", TypeDescriptor.DOUBLE);
        f.setAggregate(true);
        ResultAnalysis a = analyze(List.of(f), rows(List.of(1.0), List.of(2.0)));

        assertEquals(ColumnRole.MEASURE, a.columns().get(0).role());
        assertTrue(a.aggregated());
    }

    @Test
    public void keyIsIdentifierNeverMeasure() {
        Finding f = finding("Id", TypeDescriptor.INTEGER);
        f.setKey(true);
        ResultAnalysis a = analyze(List.of(f), rows(List.of(1L), List.of(2L)));

        assertEquals(ColumnRole.IDENTIFIER, a.columns().get(0).role());
    }

    @Test
    public void temporalTypeIsTime() {
        Finding f = finding("Order Date", TypeDescriptor.DATE);
        ResultAnalysis a = analyze(List.of(f), rows(List.of(java.time.LocalDate.of(2023, 1, 1))));

        AnalyzedColumn c = a.columns().get(0);
        assertEquals(ColumnRole.TIME, c.role());
        assertTrue(c.temporalValues());
        assertEquals("date", c.parseHint());
    }

    @Test
    public void integerTypedMonthExtractorIsOrdinalTime() {
        Finding f = finding("month(Order Date)", TypeDescriptor.INTEGER);
        f.setGrain("month");
        f.setOrdinalTemporal(true);
        ResultAnalysis a = analyze(List.of(f), rows(List.of(1L), List.of(2L)));

        AnalyzedColumn c = a.columns().get(0);
        assertEquals(ColumnRole.TIME, c.role());
        assertFalse(c.temporalValues());
        assertTrue(c.ordinalTemporal());
        assertEquals("number", c.parseHint());
    }

    @Test
    public void groupedColumnIsDimension() {
        Finding f = finding("Category", TypeDescriptor.TEXT);
        f.setGrouped(true);
        ResultAnalysis a = analyze(List.of(f), rows(List.of("a"), List.of("b")));

        assertEquals(ColumnRole.DIMENSION, a.columns().get(0).role());
    }

    @Test
    public void textIsDimensionUntilUniquePerRow() {
        Finding f = finding("Name", TypeDescriptor.TEXT);
        List<List<Object>> few = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            few.add(List.of("n" + i));
        }
        assertEquals(ColumnRole.DIMENSION, analyze(List.of(f), few).columns().get(0).role());

        List<List<Object>> many = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            many.add(List.of("n" + i));
        }
        assertEquals(ColumnRole.OTHER, analyze(List.of(f), many).columns().get(0).role());
    }

    @Test
    public void numericDetailDataIsMeasureButDimensionWhenAggregated() {
        Finding price = finding("Price", TypeDescriptor.DOUBLE);
        assertEquals(ColumnRole.MEASURE,
                analyze(List.of(price), rows(List.of(1.5), List.of(2.5))).columns().get(0).role());

        Finding count = finding("Count(x)", TypeDescriptor.INTEGER);
        count.setAggregate(true);
        Finding stray = finding("Code", TypeDescriptor.INTEGER);
        ResultAnalysis a = analyze(List.of(count, stray), rows(List.of(1L, 7L)));
        assertEquals(ColumnRole.DIMENSION, a.columns().get(1).role());
    }

    @Test
    public void statsCountDistinctNullsAndRange() {
        Finding f = finding("v", TypeDescriptor.INTEGER);
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of(3L));
        rows.add(List.of(1L));
        rows.add(java.util.Collections.singletonList(null));
        rows.add(List.of(3L));
        ColumnStats s = analyze(List.of(f), rows).columns().get(0).stats();

        assertEquals(2, s.distinctCount());
        assertEquals(1, s.nullCount());
        assertEquals(1L, s.min());
        assertEquals(3L, s.max());
        assertTrue(s.exact());
    }

    private static ResultAnalysis analyze(List<Finding> infos, List<List<Object>> rows) {
        ListResult r = new ListResult();
        r.setInfos(infos);
        rows.forEach(r::append);
        return new Analyzer().analyze(r);
    }

    private static Finding finding(String header, TypeDescriptor type) {
        Finding f = new Finding();
        f.setHeader(header);
        f.setTypeDescriptor(type);
        return f;
    }

    @SafeVarargs
    private static List<List<Object>> rows(List<Object>... rows) {
        return new ArrayList<>(List.of(rows));
    }

    @Test
    public void parseHintForBooleanAndText() {
        Finding b = finding("Flag", TypeDescriptor.BOOLEAN);
        Finding t = finding("Name", TypeDescriptor.TEXT);
        ResultAnalysis a = analyze(List.of(b, t), rows(List.of(true, "x")));

        assertEquals("boolean", a.columns().get(0).parseHint());
        assertEquals("string", a.columns().get(1).parseHint());
    }

    @Test
    public void genericTypeFallbackWhenDescriptorMissing() {
        Finding f = new Finding();
        f.setHeader("v");
        f.setFallbackFamily(CoreTypeFamily.FLOAT);
        ResultAnalysis a = analyze(List.of(f), rows(List.of(1.0)));

        assertEquals(ColumnRole.MEASURE, a.columns().get(0).role());
        assertEquals("number", a.columns().get(0).parseHint());
    }
}
