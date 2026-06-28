package ai.koryki.catalog.schema.types;

import ai.koryki.catalog.schema.Column;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locks in the precision/scale contract of {@link TypeDescriptorParser}:
 * <ul>
 *   <li>Phase 0 — {@code DECIMAL(p,s)} stores precision=p, scale=s (not swapped).</li>
 *   <li>Phase 1 — char length and temporal fractional precision are captured into precision.</li>
 * </ul>
 */
class TypeDescriptorParserTest {

    private static final TypeDescriptorParser PARSER = new TypeDescriptorParser() {};

    private static TypeDescriptor parse(String family, String dialectType) {
        Column column = new Column();
        column.setTypeFamily(family);
        column.setDialectType(dialectType);
        return PARSER.parse(column);
    }

    @Test
    void decimalKeepsPrecisionThenScale() {
        TypeDescriptor d = parse("DECIMAL", "decimal(18,6)");
        assertEquals(CoreTypeFamily.DECIMAL, d.getTypeFamily());
        assertEquals(18, d.getPrecision(), "precision must be the first DECIMAL param (not swapped)");
        assertEquals(6, d.getScale(), "scale must be the second DECIMAL param");
    }

    @Test
    void decimalWithSinglePrecisionHasNoScale() {
        TypeDescriptor d = parse("DECIMAL", "decimal(10)");
        assertEquals(10, d.getPrecision());
        assertEquals(-1, d.getScale());
    }

    @Test
    void varcharCapturesLengthAsPrecision() {
        TypeDescriptor d = parse("TEXT", "varchar(40)");
        assertEquals(CoreTypeFamily.TEXT, d.getTypeFamily());
        assertEquals(40, d.getPrecision());
        assertEquals(-1, d.getScale());
    }

    @Test
    void unsizedTextHasNoPrecision() {
        TypeDescriptor d = parse("TEXT", "TEXT");
        assertEquals(-1, d.getPrecision());
        assertEquals(-1, d.getScale());
    }

    @Test
    void timeCapturesFractionalPrecision() {
        TypeDescriptor d = parse("TIME", "time(3)");
        assertEquals(CoreTypeFamily.TIME, d.getTypeFamily());
        assertEquals(3, d.getPrecision());
    }

    @Test
    void timestampCapturesFractionalPrecision() {
        TypeDescriptor d = parse("TIMESTAMP", "timestamp(6)");
        assertEquals(CoreTypeFamily.TIMESTAMP, d.getTypeFamily());
        assertEquals(6, d.getPrecision());
    }

    @Test
    void smallintHasNoPrecision() {
        TypeDescriptor d = parse("INTEGER", "smallint");
        assertEquals(CoreTypeFamily.INTEGER, d.getTypeFamily());
        assertEquals(-1, d.getPrecision());
    }
}
