package ai.koryki.result.quantity;

/**
 * A measurement unit: machine-readable code ("EUR", "kg", "1", "%"), display
 * symbol for axis titles, and the dimension it measures. The count unit "1"
 * has an empty symbol - count axes carry no unit suffix.
 */
public record Unit(String code, String symbol, DimVector dim) {
}
