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

    /**
     * A chain through JSON that ends at a list of scalars the catalog declares is that list: its
     * functions and lambdas apply, and the renderer converts the JSON array into the dialect's
     * list. A list of objects, or of lists, stays JSON.
     */
    public static TypeDescriptor listOf(TypeDescriptor base, TypeDescriptor type) {
        if (base != null
                && CoreTypeFamily.JSON.equals(base.getTypeFamily())
                && type != null
                && CoreTypeFamily.JSON.equals(type.getTypeFamily())
                && type.shape() != null
                && CoreTypeFamily.ARRAY.equals(type.shape().getTypeFamily())
                && !type.shape().element().isComplex()) {
            return TypeDescriptor.array(type.shape().element());
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
            return inJson(type, shape -> member(shape, name, text));
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
            return inJson(type, shape -> positional(shape, text));
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

    /**
     * A step into JSON. Unshaped JSON stays JSON. With a declared shape the step follows it: a
     * member that is declared gives its type, a scalar as itself (the renderer reads the scalar out
     * of the JSON), a container as JSON with the shape of that member; a member that is not
     * declared is JSON of unknown content.
     */
    private static TypeDescriptor inJson(
            TypeDescriptor json, java.util.function.Function<TypeDescriptor, TypeDescriptor> step) {
        TypeDescriptor shape = json.shape();
        if (shape == null) {
            return json;
        }
        TypeDescriptor next = step.apply(shape);
        if (next == null) {
            return json.withShape(null);
        }
        return next.isComplex() ? json.withShape(next) : next;
    }

    private static TypeDescriptor member(TypeDescriptor shape, String name, String text) {
        if (CoreTypeFamily.STRUCT.equals(shape.getTypeFamily())) {
            return shape.field(name).orElse(null);
        } else if (CoreTypeFamily.MAP.equals(shape.getTypeFamily())) {
            if (!CoreTypeFamily.TEXT.equals(shape.key().getTypeFamily())) {
                throw new SelectorException(
                        text
                                + " selects a text key, but the keys of "
                                + shape.portable()
                                + " are "
                                + shape.key().portable());
            }
            return shape.value();
        }
        return null;
    }

    /** A position in a declared shape: the element of a list, or the value of an integer key. */
    private static TypeDescriptor positional(TypeDescriptor shape, String text) {
        if (CoreTypeFamily.ARRAY.equals(shape.getTypeFamily())) {
            return shape.element();
        } else if (CoreTypeFamily.MAP.equals(shape.getTypeFamily())) {
            if (!CoreTypeFamily.INTEGER.equals(shape.key().getTypeFamily())) {
                throw new SelectorException(
                        text
                                + " selects an integer key, but the keys of "
                                + shape.portable()
                                + " are "
                                + shape.key().portable());
            }
            return shape.value();
        }
        return null;
    }

    /**
     * The steps of a chain from JSON as the path will name them: where the shape says an object has
     * integer keys (JSON keys are text, so {@code 1} is the member {@code "1"}), a position is the
     * key it names, written as a name. Every other step is as it was.
     */
    public static List<Selector> jsonKeys(TypeDescriptor base, List<Selector> steps) {
        if (base == null
                || !CoreTypeFamily.JSON.equals(base.getTypeFamily())
                || base.shape() == null) {
            return steps;
        }
        List<Selector> out = new java.util.ArrayList<>();
        TypeDescriptor type = base;
        for (Selector step : steps) {
            TypeDescriptor shape = type.shape();
            if (shape != null
                    && step.getIndex() != null
                    && CoreTypeFamily.MAP.equals(shape.getTypeFamily())
                    && CoreTypeFamily.INTEGER.equals(shape.key().getTypeFamily())) {
                Selector key = new Selector();
                key.setName(String.valueOf(step.getIndex()));
                out.add(key);
            } else {
                out.add(step);
            }
            try {
                type = step(type, step);
            } catch (SelectorException ended) {
                out.addAll(steps.subList(out.size(), steps.size()));
                return out;
            }
            if (!CoreTypeFamily.JSON.equals(type.getTypeFamily())) {
                out.addAll(steps.subList(out.size(), steps.size()));
                return out;
            }
        }
        return out;
    }

    private static SelectorException refused(TypeDescriptor type, String text, String why) {
        return new SelectorException(
                "cannot select " + text + " from " + type.portable() + ": " + why);
    }
}
