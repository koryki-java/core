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
package ai.koryki.scaffold.domain;


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

    public String getColumn() {
        return column;
    }

//    public void setColumn(String column) {
//        this.column = column;
//    }

    public String getDialectColumn() {
        return column != null ? column : name;
    }

}
