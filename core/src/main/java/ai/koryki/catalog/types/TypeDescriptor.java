package ai.koryki.catalog.types;

import java.util.Objects;

public class TypeDescriptor {

    public static final TypeDescriptor DATE      = new TypeDescriptor(TypeNames.TYPE_DATE,      null, CoreTypeFamily.DATE);
    public static final TypeDescriptor TIME      = new TypeDescriptor(TypeNames.TYPE_TIME,      null, CoreTypeFamily.TIME);
    public static final TypeDescriptor TIMESTAMP = new TypeDescriptor(TypeNames.TYPE_TIMESTAMP, null, CoreTypeFamily.TIMESTAMP);
    public static final TypeDescriptor TEXT      = new TypeDescriptor(TypeNames.TYPE_TEXT,      null, CoreTypeFamily.TEXT);
    public static final TypeDescriptor BOOLEAN   = new TypeDescriptor(TypeNames.TYPE_BOOLEAN,   null, CoreTypeFamily.BOOLEAN);
    public static final TypeDescriptor SMALLINT  = new TypeDescriptor(TypeNames.TYPE_SMALLINT,  null, CoreTypeFamily.INTEGER);
    public static final TypeDescriptor INTEGER   = new TypeDescriptor(TypeNames.TYPE_INTEGER,   null, CoreTypeFamily.INTEGER);
    public static final TypeDescriptor BIGINT    = new TypeDescriptor(TypeNames.TYPE_BIGINT,    null, CoreTypeFamily.INTEGER);
    public static final TypeDescriptor FLOAT     = new TypeDescriptor(TypeNames.TYPE_FLOAT,     null, CoreTypeFamily.FLOAT);
    public static final TypeDescriptor DOUBLE    = new TypeDescriptor(TypeNames.TYPE_DOUBLE,    null, CoreTypeFamily.FLOAT);
    public static final TypeDescriptor DECIMAL   = new TypeDescriptor(TypeNames.TYPE_DECIMAL,   null, CoreTypeFamily.DECIMAL);
    public static final TypeDescriptor INTERVAL  = new TypeDescriptor(TypeNames.TYPE_INTERVAL,  null, CoreTypeFamily.INTERVAL);
    /** Dialect-neutral NULL literal type: no family, so it never dominates in type widening (numericRank 0). */
    public static final TypeDescriptor NULL      = new TypeDescriptor(TypeNames.TYPE_NULL,      null, null);

    private final int  precision;
    private final int scale;
    private final TypeFamily typeFamily;
    private final String physicalTypeName;
    private final TypeEncoding typeEncoding;

    public TypeDescriptor(String physicalTypeName, TypeEncoding typeEncoding, TypeFamily typeFamily) {
        this(physicalTypeName, typeEncoding, typeFamily, -1, -1);
    }

    public TypeDescriptor(String physicalTypeName, TypeEncoding typeEncoding, TypeFamily typeFamily, int precision, int scale) {
        this.physicalTypeName = physicalTypeName;
        this.typeFamily = typeFamily;
        this.precision = precision;
        this.scale = scale;
        // An absent encoding means "stored in the family's natural physical type": resolve it to the
        // per-family NATIVE so the (family, encoding) pair is always meaningful — never null — at output
        // time (see NativeEncoding). The typeless NULL literal (no family) keeps a null encoding.
        this.typeEncoding = (typeEncoding == null && typeFamily != null)
                ? NativeEncoding.of(typeFamily)
                : typeEncoding;
    }

    public TypeFamily getTypeFamily() {
        return typeFamily;
    }

    public int getPrecision() {
        return precision;
    }

    public int getScale() {
        return scale;
    }

    public String getPhysicalTypeName() {
        return physicalTypeName;
    }


    public TypeEncoding getTypeEncoding() {
        return typeEncoding;
    }

    @Override
    public String toString() {
        return "TypeDescriptor{" +
                "typeFamily=" + typeFamily +
                ", physicalTypeName='" + physicalTypeName + '\'' +
                ", typeEncoding=" + typeEncoding +
                ", precision=" + precision +
                ", scale=" + scale +
                '}';
    }
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        TypeDescriptor that = (TypeDescriptor) o;
        return precision == that.precision && scale == that.scale && Objects.equals(typeFamily, that.typeFamily) && Objects.equals(physicalTypeName, that.physicalTypeName) && Objects.equals(typeEncoding, that.typeEncoding);
    }

    @Override
    public int hashCode() {
        return Objects.hash(precision, scale, typeFamily, physicalTypeName, typeEncoding);
    }


}
