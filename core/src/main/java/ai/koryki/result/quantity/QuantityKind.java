package ai.koryki.result.quantity;

/**
 * Business meaning of a quantity where the unit alone cannot distinguish it:
 * cost, selling price and revenue are all "EUR" - their kind codes differ.
 * The kind's dimension wins over the unit's in the algebra: "unit-price" is
 * MONEY*COUNT^-1 even though its display unit is a plain currency, which is
 * what makes unit_price * quantity come out as MONEY (revenue).
 */
public record QuantityKind(String code, DimVector dim, String defaultUnit) {
}
