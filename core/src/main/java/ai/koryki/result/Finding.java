package ai.koryki.result;

import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.catalog.types.TypeFamily;
import ai.koryki.iql.query.Out;
import ai.koryki.jdbc.ColumnInfo;

public class Finding implements ColumnInfo {

    private Out out;
    private String header;

    private TypeFamily fallbackFamily;
    private String dialectType;
    private String unit;
    private double scale;
    private boolean aggregate;
    private ai.koryki.iql.functions.MathOp math;
    private String aggregateFunction;
    private TypeDescriptor typeDescriptor;
    private boolean key;
    private String orderDirection;
    private Integer orderPriority;
    private boolean grouped;
    private String grain;
    private boolean ordinalTemporal;
    private ai.koryki.result.quantity.Quantity quantity;
    private ai.koryki.result.metric.Shape shape;
    private String metric;

    public Finding() {
        this(null);
    }

    public Finding(Out out) {
        this.out = out;
    }

    public String getHeader() {
        return header;
    }

    @Override
    public void setHeader(String header) {
        this.header = header;
    }

    public Out getOut() {
        return out;
    }

    public void setOut(Out out) {
        this.out = out;
    }

    public TypeFamily getFallbackFamily() {
        return fallbackFamily;
    }

    public void setFallbackFamily(TypeFamily fallbackFamily) {
        this.fallbackFamily = fallbackFamily;
    }

    public String getDialectType() {
        return dialectType;
    }

    public void setDialectType(String dialectType) {
        this.dialectType = dialectType;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        this.scale = scale;
    }

    public boolean isAggregate() {
        return aggregate;
    }

    public void setAggregate(boolean aggregate) {
        this.aggregate = aggregate;
    }

    public ai.koryki.iql.functions.MathOp getMath() {
        return math;
    }

    public void setMath(ai.koryki.iql.functions.MathOp math) {
        this.math = math;
    }

    public String getAggregateFunction() {
        return aggregateFunction;
    }

    public void setAggregateFunction(String aggregateFunction) {
        this.aggregateFunction = aggregateFunction;
    }

    @Override
    public TypeDescriptor getTypeDescriptor() {
        return typeDescriptor;
    }

    @Override
    public void setTypeDescriptor(TypeDescriptor typeDescriptor) {
        this.typeDescriptor = typeDescriptor;
    }

    /** Whether the underlying column is part of a primary key (id columns are no chart measures). */
    public boolean isKey() {
        return key;
    }

    public void setKey(boolean key) {
        this.key = key;
    }

    /** "ASC"/"DESC" when the query explicitly orders by this column, else null. */
    public String getOrderDirection() {
        return orderDirection;
    }

    public void setOrderDirection(String orderDirection) {
        this.orderDirection = orderDirection;
    }

    /** 1-based position in the query's ORDER list, null when unordered. */
    public Integer getOrderPriority() {
        return orderPriority;
    }

    public void setOrderPriority(Integer orderPriority) {
        this.orderPriority = orderPriority;
    }

    /** Whether the column appears in the (inferred) GROUP BY - a dimension of an aggregated result. */
    public boolean isGrouped() {
        return grouped;
    }

    public void setGrouped(boolean grouped) {
        this.grouped = grouped;
    }

    /**
     * Time grain when the column is a calendar bucket or part:
     * "year"|"quarter"|"month"|"week"|"day"|"hour"|"minute"|"second", null otherwise.
     * Set for date_trunc/*_begin buckets and for part extractors like month(x) -
     * the latter return INTEGER, so temporal classification must use this field,
     * never the type family alone.
     */
    public String getGrain() {
        return grain;
    }

    public void setGrain(String grain) {
        this.grain = grain;
    }

    /** True for part extractors (month(x) -> 1..12, cyclic ordinal), false for real date buckets. */
    public boolean isOrdinalTemporal() {
        return ordinalTemporal;
    }

    public void setOrdinalTemporal(boolean ordinalTemporal) {
        this.ordinalTemporal = ordinalTemporal;
    }

    /** Dimensional value (unit, kind, dimension vector), null when nothing is known. */
    /** Canonical expression-shape signature, internal to metric derivation - never on the wire. */
    public ai.koryki.result.metric.Shape getShape() {
        return shape;
    }

    public void setShape(ai.koryki.result.metric.Shape shape) {
        this.shape = shape;
    }

    /**
     * Localized business/scientific metric name concluded from the shape
     * ("Net revenue"/"Nettoumsatz", "Speed"/"Geschwindigkeit"); null when
     * nothing can be concluded. Additional information - the header stays
     * the mechanical expression rendering.
     */
    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public ai.koryki.result.quantity.Quantity getQuantity() {
        return quantity;
    }

    public void setQuantity(ai.koryki.result.quantity.Quantity quantity) {
        this.quantity = quantity;
    }
}
