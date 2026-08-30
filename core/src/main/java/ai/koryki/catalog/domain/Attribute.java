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
package ai.koryki.catalog.domain;


public class Attribute {

    private String name;
    private String label;
    private String comment;
    private String description;
    private String column;

    public Attribute() {

    }

    public Attribute(String name) {
        this.name = name;
    }

    public Attribute(String name, String comment, String description) {
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

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * The database's own name for this attribute's column, when it differs from the model's.
     *
     * <p><b>The exact stored spelling.</b> Not a logical name and not a convenience spelling: this
     * value is what the renderer will put between quotes the moment the name cannot go bare, and a
     * quoted name is matched literally. Nothing here introspects a database, so the renderer has no
     * second source to check it against - if the catalog says {@code Betrag} and the attribute's column was
     * created unquoted on Oracle, it is stored {@code BETRAG} and the query finds nothing.
     *
     * <p>The distinction is invisible for the ordinary case and only bites at the edges. An
     * all-lowercase name goes unquoted, and every engine either folds an unquoted name or ignores
     * case, so it resolves whatever its stored spelling. A name carrying a space or a special
     * character could never have been created without quotes, so it is stored exactly as typed. It
     * is the merely mixed-case name that is ambiguous, and this contract is what resolves it.
     *
     * <p>Must be unqualified. A schema-qualified value such as {@code public.orders} is rendered as
     * one identifier containing a dot, which resolves to nothing; qualify through the connection
     * instead.
     *
     * @see ai.koryki.iql.SqlDialect#renderIdentifier
     */
    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }
}
