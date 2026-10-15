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
package ai.koryki.iql.query;

public class Selector {

    private Integer index;
    private Integer from;
    private Integer to;
    private String id;
    private String name;

    /**
     * The key of the entry in the schema this step goes to, where the name written is that of a
     * language of the model and not the key itself. Set by a rule, never by the mapping, and not a
     * bean property: the query keeps what was written, and everything that has to find the entry
     * asks {@link #key()}.
     */
    private String key;

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public Integer getFrom() {
        return from;
    }

    public void setFrom(Integer from) {
        this.from = from;
    }

    public Integer getTo() {
        return to;
    }

    public void setTo(Integer to) {
        this.to = to;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /** The name as it was written, an identifier or a quoted name; null for a position or range. */
    public String written() {
        return id != null ? id : name;
    }

    /** The key of the entry in the schema: the translated name, else the name as written. */
    public String key() {
        return key != null ? key : written();
    }

    /** Says which entry of the schema the written name stands for. */
    public void translateTo(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
