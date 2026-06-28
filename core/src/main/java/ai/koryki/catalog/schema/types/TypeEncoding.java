package ai.koryki.catalog.schema.types;

public interface TypeEncoding {

    String name();

    /**
     * The single logical {@link TypeFamily} whose physical storage this encoding
     * describes — e.g. {@code TIME_FROM_STRING} is a {@code TIME}, {@code SCALED}
     * a {@code DECIMAL}. Every encoding binds to exactly one family (one-to-many:
     * a family has many encodings, each encoding one family), which lets the
     * {@code (family, encoding)} pair be validated or the family be derived.
     */
    TypeFamily family();
}
