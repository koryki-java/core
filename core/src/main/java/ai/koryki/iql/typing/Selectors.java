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
package ai.koryki.iql.typing;

import ai.koryki.catalog.types.CoreTypeFamily;
import ai.koryki.catalog.types.TypeDescriptor;
import ai.koryki.iql.IQLSerializer;
import ai.koryki.iql.query.Selector;
import java.util.List;

/**
 * What a selector chain does to a type. The grammar cannot tell a list position from a map key or a
 * struct field, because {@code @1} and {@code @name} look the same on every value; the type of the
 * value decides:
 *
 * <table>
 *   <caption>The meaning of a step by the type it is applied to</caption>
 *   <tr><th>step</th><th>ARRAY</th><th>STRUCT</th><th>MAP</th><th>JSON</th></tr>
 *   <tr><td>{@code @1}, {@code @-1}</td><td>element</td><td>field by position</td>
 *       <td>value of an integer key</td><td>JSON</td></tr>
 *   <tr><td>{@code @1:3}</td><td>list of the elements</td><td>-</td><td>-</td><td>-</td></tr>
 *   <tr><td>{@code @name}, {@code @'a key'}</td><td>-</td><td>field</td>
 *       <td>value of a text key</td><td>JSON</td></tr>
 * </table>
 *
 * Positions count from 1, and from the end when negative; there is no position 0. A step into a
 * scalar, TEXT included, is refused: portable across the dialects only for the containers.
 */
public final class Selectors {

    private Selectors() {}

    /**
     * The type after every step, or a {@link SelectorException} naming the first one that fails.
     */
    public static TypeDescriptor apply(TypeDescriptor base, List<Selector> steps) {
        TypeDescriptor type = base;
        for (Selector step : steps) {
            type = step(type, step);
        }
        return type;
    }

    /** The type after one step; a renderer follows the chain with it. */
    public static TypeDescriptor step(TypeDescriptor type, Selector step) {
        String text = IQLSerializer.selector(step);
        if (type == null || type.getTypeFamily() == null) {
            throw new SelectorException("cannot apply " + text + " to a value without a type");
        }
        if (step.getId() != null || step.getName() != null) {
            return byName(type, step.getId() != null ? step.getId() : step.getName(), text);
        } else if (step.getIndex() != null) {
            return byPosition(type, step.getIndex(), text);
        }
        return byRange(type, step, text);
    }

    private static TypeDescriptor byName(TypeDescriptor type, String name, String text) {
        if (CoreTypeFamily.STRUCT.equals(type.getTypeFamily())) {
            return type.field(name)
                    .orElseThrow(
                            () ->
                                    new SelectorException(
                                            "no field '"
                                                    + name
                                                    + "' in "
                                                    + type.portable()
                                                    + "; its fields are "
                                                    + String.join(", ", type.fieldNames())));
        } else if (CoreTypeFamily.MAP.equals(type.getTypeFamily())) {
            if (!CoreTypeFamily.TEXT.equals(type.key().getTypeFamily())) {
                throw new SelectorException(
                        text
                                + " selects a text key, but the keys of "
                                + type.portable()
                                + " are "
                                + type.key().portable());
            }
            return type.value();
        } else if (CoreTypeFamily.JSON.equals(type.getTypeFamily())) {
            return type;
        }
        throw refused(type, text, "by name; that needs a struct, a map or JSON");
    }

    private static TypeDescriptor byPosition(TypeDescriptor type, int position, String text) {
        if (position == 0) {
            throw new SelectorException(text + ": positions count from 1, there is no position 0");
        }
        if (CoreTypeFamily.ARRAY.equals(type.getTypeFamily())) {
            return type.element();
        } else if (CoreTypeFamily.STRUCT.equals(type.getTypeFamily())) {
            int n = type.children().size();
            int index = position > 0 ? position : n + position + 1;
            if (index < 1 || index > n) {
                throw new SelectorException(
                        text + " is outside " + type.portable() + ", which has " + n + " fields");
            }
            return type.children().get(index - 1);
        } else if (CoreTypeFamily.MAP.equals(type.getTypeFamily())) {
            if (!CoreTypeFamily.INTEGER.equals(type.key().getTypeFamily())) {
                throw new SelectorException(
                        text
                                + " selects an integer key, but the keys of "
                                + type.portable()
                                + " are "
                                + type.key().portable());
            }
            return type.value();
        } else if (CoreTypeFamily.JSON.equals(type.getTypeFamily())) {
            return type;
        }
        throw refused(type, text, "by position; that needs a list, a struct, a map or JSON");
    }

    private static TypeDescriptor byRange(TypeDescriptor type, Selector step, String text) {
        if (Integer.valueOf(0).equals(step.getFrom()) || Integer.valueOf(0).equals(step.getTo())) {
            throw new SelectorException(text + ": positions count from 1, there is no position 0");
        }
        if (CoreTypeFamily.ARRAY.equals(type.getTypeFamily())) {
            // a slice of a fixed-size array is a list: its length depends on the bounds
            return TypeDescriptor.array(type.element());
        }
        throw refused(type, text, "as a range; that needs a list");
    }

    private static SelectorException refused(TypeDescriptor type, String text, String why) {
        return new SelectorException(
                "cannot select " + text + " from " + type.portable() + ": " + why);
    }
}
