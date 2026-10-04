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

/**
 * One step of a postfix selection on a nested value, one member per way to write it. Exactly one
 * way is set:
 *
 * <ul>
 *   <li>{@link #getIndex()}: {@code @1}, {@code @-1}; a position, 1-based, negative from the end;
 *   <li>{@link #getFrom()} and/or {@link #getTo()}: {@code @1:3}, {@code @2:}, {@code @:3}; a
 *       range, 1-based and inclusive, an open end is null;
 *   <li>{@link #getId()}: {@code @name}; a field or key written as an identifier;
 *   <li>{@link #getName()}: {@code @'a key'}; a field or key written as a quoted name, which allows
 *       any characters.
 * </ul>
 *
 * What a step means, a list position, a struct field or a map key, depends on the type of the value
 * it is applied to, so the bean only says what was written.
 */
public class Selector {

    private Integer index;
    private Integer from;
    private Integer to;
    private String id;
    private String name;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
