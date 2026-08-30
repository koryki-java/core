/*
 * Copyright 2025-2026 Johannes Zemlin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package ai.koryki.catalog.schema;


public class Column {

    private String name;
    private String label;
    private String comment;
    private String description;

    private String typeFamily;
    private String typeEncoding;
    private String dialectType;
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

    // optional physical-quantity semantics carried from db.json (used by the
    // result-analysis layer); null unless the schema declares them.
    private String unit;
    private String quantity;
    private String presentation;

    public Column() {

    }

    public Column(String name) {
        this.name = name;
    }

    public Column(String name, String comment, String description) {
        this.name = name;
        this.comment = comment;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getQuantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getNullable() {
        return nullable;
    }

    public void setNullable(Boolean nullable) {
        this.nullable = nullable;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
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

    public String getDialectType() {
       return dialectType;
    }

    public void setDialectType(String dialectType) {
        this.dialectType = dialectType;
    }

    public String getTypeFamily() {
        return typeFamily;
    }

    public void setTypeFamily(String typeFamily) {
        this.typeFamily = typeFamily;
    }

    public String getTypeEncoding() {
        return typeEncoding;
    }

    public void setTypeEncoding(String typeEncoding) {
        this.typeEncoding = typeEncoding;
    }

    /**
     * How this column's values should read, as a
     * {@link ai.koryki.presentation.Presentation Presentation} name — {@code "DECIMALS:2"}, later
     * {@code "IP"}. Travels as one string exactly like {@link #getTypeEncoding()}, and is resolved
     * through {@link ai.koryki.presentation.PresentationRegistry PresentationRegistry}.
     *
     * <p>A name, not the type: the catalog describes data, and how a value is <em>shown</em> is not
     * a description of it. That is why the family lives in {@code ai.koryki.presentation} and only
     * its serialised form reaches this far.
     *
     * <p>The door for what no quantity calculus can conclude: that a number is an address, or that
     * this one column wants different precision from its kind. A declaration here beats the derived
     * default.
     */
    public String getPresentation() {
        return presentation;
    }

    public void setPresentation(String presentation) {
        this.presentation = presentation;
    }
}
