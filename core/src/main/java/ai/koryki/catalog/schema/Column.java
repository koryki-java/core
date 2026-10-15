package ai.koryki.catalog.schema;

/**
 * A column of a table: its name, what belongs to it as part of the table (whether it may be empty,
 * its place in the key and in the name of the row), and its concept, which says everything else:
 * the type, the label, the unit. The concept is the column's own, or the one it refers to by name.
 */
public class Column {

    private String name;

    /**
     * The name of a concept in {@code Schema.getConcepts()}: what this column is, where the concept
     * says it (structure, unit, presentation, label). A column has this or a {@link #concept}, not
     * both.
     */
    private String conceptRef;

    /**
     * The concept of this column alone, not shared with any other: what the column is, said where
     * the concept says it. A column has this or a {@link #conceptRef}, not both.
     */
    private Concept concept;

    private Boolean nullable;
    private int pkPos;

    /**
     * Position in the entity's business name, or 0 when the column is not part of it.
     *
     * <p>Numbered like {@link #pkPos}, and for the same reason: a name can be made of several
     * columns, as an employee is a last name and a first name, and the order they are read in is
     * part of it.
     *
     * <p>What it is for: a primary key identifies a row and says nothing to a reader. A follow-up
     * question of the form "revenue per customer" has to group by something a person recognises,
     * and no other field here can say which column that is — a name and a phone number are both
     * text, neither is a key, and both hold one distinct value per row.
     *
     * <p>Optional throughout. Where a table declares none, a consumer falls back to its own rule;
     * where it declares some, those are the whole of the name. Deliberately here and not in the
     * per-locale model: which column names a thing is the same in every language, and the same
     * information kept once per locale has already been observed to drift apart.
     */
    private int namePos;

    public Column() {}

    public Column(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getNullable() {
        return nullable;
    }

    public void setNullable(Boolean nullable) {
        this.nullable = nullable;
    }

    public int getPkPos() {
        return pkPos;
    }

    public void setPkPos(int pkPos) {
        this.pkPos = pkPos;
    }

    public int getNamePos() {
        return namePos;
    }

    public void setNamePos(int namePos) {
        this.namePos = namePos;
    }

    public String getConceptRef() {
        return conceptRef;
    }

    public void setConceptRef(String conceptRef) {
        this.conceptRef = conceptRef;
    }

    public Concept getConcept() {
        return concept;
    }

    public void setConcept(Concept concept) {
        this.concept = concept;
    }
}
