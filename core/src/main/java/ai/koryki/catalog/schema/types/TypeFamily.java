package ai.koryki.catalog.schema.types;

public interface TypeFamily {

    String name();

    /**
     * Whether this <em>declared</em> family accepts a <em>candidate</em> (actual)
     * family. A leaf family accepts only itself; a {@link FamilyGroup} accepts its
     * members. This is the basis of operand-type checking and overload selection —
     * use it instead of {@link Object#equals} so an argument declared as a group
     * (e.g. {@code NUMERIC}) matches any member (INTEGER, DECIMAL, FLOAT).
     */
    default boolean accepts(TypeFamily candidate) {
        return equals(candidate);
    }
}
