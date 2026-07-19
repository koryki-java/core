package ai.koryki.result.quantity;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Built-in unit and quantity-kind tables plus the side-car column annotations.
 * Column annotations normally come from the semantic layer (Column.unit /
 * Column.quantity in db.json); the side-car resource
 * {@code /ai/koryki/result/units/<schema>.json} is the fallback for schemas
 * whose db.json cannot be extended. Keys are {@code "table.column"}.
 */
public final class UnitRegistry {

    private static final Map<String, Unit> UNITS = new LinkedHashMap<>();
    private static final Map<String, QuantityKind> KINDS = new LinkedHashMap<>();
    private static final Map<DimVector, QuantityKind> KIND_BY_DIM = new LinkedHashMap<>();

    private static final Map<String, Map<String, ColumnUnit>> SIDECARS = new ConcurrentHashMap<>();

    /** Side-car annotation of one column: unit code and/or quantity-kind code. */
    public record ColumnUnit(String unit, String quantity) {
    }

    static {
        DimVector money = DimVector.of(BaseDim.MONEY);
        DimVector length = DimVector.of(BaseDim.LENGTH);
        DimVector mass = DimVector.of(BaseDim.MASS);
        DimVector time = DimVector.of(BaseDim.TIME);
        DimVector count = DimVector.of(BaseDim.COUNT);
        DimVector none = DimVector.NONE;

        unit("EUR", "€", money);
        unit("USD", "$", money);
        unit("GBP", "£", money);
        unit("m", "m", length);
        unit("km", "km", length);
        unit("cm", "cm", length);
        unit("mm", "mm", length);
        unit("kg", "kg", mass);
        unit("g", "g", mass);
        unit("t", "t", mass);
        unit("s", "s", time);
        unit("min", "min", time);
        unit("h", "h", time);
        unit("d", "d", time);
        unit("1", "", count);
        unit("%", "%", none);
        unit("°C", "°C", DimVector.of(BaseDim.TEMPERATURE));

        kind("money", money, null);
        kind("unit-price", money.dividedBy(count), null);
        kind("count", count, "1");
        kind("ratio", none, null);
        kind("length", length, "m");
        kind("area", DimVector.of(BaseDim.LENGTH, 2), null);
        kind("volume", DimVector.of(BaseDim.LENGTH, 3), null);
        kind("mass", mass, "kg");
        kind("duration", time, "s");
        kind("speed", length.dividedBy(time), null);
        kind("temperature", DimVector.of(BaseDim.TEMPERATURE), "°C");
        // registered after the canonical kinds: putIfAbsent keeps e.g. money the
        // kindByDim(MONEY) answer - cost only surfaces when annotated on a column
        kind("cost", money, null);
        kind("rate", count.dividedBy(time), null);
        kind("density", mass.dividedBy(DimVector.of(BaseDim.LENGTH, 3)), null);
        kind("pressure", mass.dividedBy(length).dividedBy(time).dividedBy(time), null);
    }

    private static void unit(String code, String symbol, DimVector dim) {
        UNITS.put(code, new Unit(code, symbol, dim));
    }

    private static void kind(String code, DimVector dim, String defaultUnit) {
        QuantityKind k = new QuantityKind(code, dim, defaultUnit);
        KINDS.put(code, k);
        KIND_BY_DIM.putIfAbsent(dim, k);
    }

    private UnitRegistry() {
    }

    public static Optional<Unit> unit(String code) {
        return Optional.ofNullable(code == null ? null : UNITS.get(code));
    }

    public static Optional<QuantityKind> kind(String code) {
        return Optional.ofNullable(code == null ? null : KINDS.get(code));
    }

    /** Canonical kind for a derived dimension: MONEY -> money, empty -> ratio, ... */
    public static Optional<QuantityKind> kindByDim(DimVector dim) {
        return Optional.ofNullable(KIND_BY_DIM.get(dim));
    }

    /**
     * Resolves unit and kind codes to a {@link Quantity}. The kind's dimension
     * wins over the unit's; an unregistered or missing pair yields UNKNOWN.
     */
    public static Quantity quantity(String unitCode, String kindCode) {
        Unit u = unit(unitCode).orElse(null);
        QuantityKind k = kind(kindCode).orElse(null);
        if (k == null && u == null) {
            return Quantity.UNKNOWN;
        }
        DimVector dim = k != null ? k.dim() : u.dim();
        if (u == null && k.defaultUnit() != null) {
            u = unit(k.defaultUnit()).orElse(null);
        }
        return new Quantity(u, k, dim, true);
    }

    /** Side-car annotations for a schema, empty when no resource exists. */
    public static Map<String, ColumnUnit> sidecar(String schemaName) {
        if (schemaName == null) {
            return Map.of();
        }
        return SIDECARS.computeIfAbsent(schemaName, UnitRegistry::loadSidecar);
    }

    private static Map<String, ColumnUnit> loadSidecar(String schemaName) {
        try (InputStream in = UnitRegistry.class.getResourceAsStream("/ai/koryki/result/units/" + schemaName + ".json")) {
            if (in == null) {
                return Map.of();
            }
            ObjectMapper mapper = new ObjectMapper();
            Map<String, ColumnUnit> m = new LinkedHashMap<>();
            mapper.readTree(in).properties().forEach(e ->
                    m.put(e.getKey(), new ColumnUnit(
                            e.getValue().path("unit").asText(null),
                            e.getValue().path("quantity").asText(null))));
            return Map.copyOf(m);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
