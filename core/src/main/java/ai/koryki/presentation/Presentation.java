package ai.koryki.presentation;

import java.util.Locale;

/**
 * How a column's values should read — the presentation hint {@code TypeDescriptor} deliberately does
 * not carry.
 *
 * <p>The SQL type cannot answer "how many decimals": measured across the corpus,
 * {@link ai.koryki.catalog.types.TypeDescriptor#getScale() TypeDescriptor.getScale()} is
 * {@code -1} on every column except an explicit
 * {@code to_decimal(x,16,8)}. What can answer it is the semantic layer — a money column shows the
 * currency's minor units, a count shows none — and that knowledge has to reach the read boundary
 * somehow. This is the carrier.
 *
 * <p><b>Why a strategy and not a record of numbers.</b> Decimal places are one member of a family
 * that also holds percentages, IP addresses and magnitude scaling ("57 M"). Two {@code Integer}
 * fields cannot express those. The shape mirrors
 * {@link ai.koryki.catalog.types.TypeEncoding TypeEncoding}: a name that serialises to a
 * single string, prefix-parameterised, resolvable through {@link PresentationRegistry} — so a
 * presentation can be written into {@code db.json} exactly the way {@code typeEncoding} already is.
 *
 * <p><b>One difference from that model, and it is the point.</b> A {@code TypeEncoding} only
 * describes; its consumers branch on it, which is right there because a storage encoding reaches
 * into SQL generation in many places. A presentation reaches exactly one place — the read boundary —
 * so it carries its own rendering. Adding {@code IP} or {@code PERCENT} later costs a class and a
 * registry line, and no change to any formatter.
 *
 * <p>Implementations must be immutable and value-comparable ({@code equals}/{@code hashCode} on the
 * name), because they are cached by name in the registry.
 */
public interface Presentation {

    /**
     * The serialised form, and the registry key: {@code "DECIMALS:2"}, {@code "SIGNIFICANT:4"}.
     * {@link PresentationRegistry#ofNullable(String)} must return an equal instance for it.
     */
    String name();

    /**
     * Renders the value, or {@code null} when this presentation has nothing to say about it — a
     * decimal rule meeting a text value, say. The caller then falls back to its own rendering, so
     * "not applicable" never turns into a wrong answer or an exception.
     *
     * @param locale presentation locale; {@code null} means canonical, locale-independent output,
     *               the deterministic mode golden tests rely on
     */
    String render(Object value, Locale locale);
}
