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
package ai.koryki.catalog.types;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * A way down into a nested type: {@code []} the element of an array, {@code {}} the value of a map,
 * {@code <>} its key, {@code .name} (or {@code ."odd name"}) a struct field. {@code [].ts} is the
 * {@code ts} field of the elements of a list of structs. The catalog uses it to say something about
 * a leaf the type spelling cannot carry, an encoding.
 */
public final class TypePath {

    private enum Kind {
        ELEMENT,
        KEY,
        VALUE,
        FIELD
    }

    private record Step(Kind kind, String name) {}

    private final String text;
    private final List<Step> steps;

    private TypePath(String text, List<Step> steps) {
        this.text = text;
        this.steps = steps;
    }

    public static TypePath parse(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("empty type path");
        }
        List<Step> steps = new ArrayList<>();
        int i = 0;
        while (i < path.length()) {
            if (path.startsWith("[]", i)) {
                steps.add(new Step(Kind.ELEMENT, null));
                i += 2;
            } else if (path.startsWith("{}", i)) {
                steps.add(new Step(Kind.VALUE, null));
                i += 2;
            } else if (path.startsWith("<>", i)) {
                steps.add(new Step(Kind.KEY, null));
                i += 2;
            } else if (path.charAt(i) == '.') {
                i++;
                StringBuilder name = new StringBuilder();
                if (i < path.length() && path.charAt(i) == '"') {
                    i++;
                    while (true) {
                        if (i >= path.length()) {
                            throw bad(path, "unterminated quoted name");
                        }
                        char c = path.charAt(i++);
                        if (c == '"') {
                            if (i < path.length() && path.charAt(i) == '"') {
                                name.append('"');
                                i++;
                            } else {
                                break;
                            }
                        } else {
                            name.append(c);
                        }
                    }
                } else {
                    while (i < path.length()
                            && (Character.isLetterOrDigit(path.charAt(i))
                                    || path.charAt(i) == '_')) {
                        name.append(path.charAt(i++));
                    }
                }
                if (name.isEmpty()) {
                    throw bad(path, "a field name is missing after '.'");
                }
                steps.add(new Step(Kind.FIELD, name.toString()));
            } else {
                throw bad(path, "unexpected '" + path.charAt(i) + "' at position " + i);
            }
        }
        return new TypePath(path, List.copyOf(steps));
    }

    private static IllegalArgumentException bad(String path, String why) {
        return new IllegalArgumentException("invalid type path '" + path + "': " + why);
    }

    /** The descriptor at this path, or an error naming the path and the type that does not fit. */
    public TypeDescriptor get(TypeDescriptor root) {
        TypeDescriptor[] found = new TypeDescriptor[1];
        replace(
                root,
                leaf -> {
                    found[0] = leaf;
                    return leaf;
                });
        return found[0];
    }

    /** A copy of {@code root} with the descriptor at this path replaced by {@code f}'s result. */
    public TypeDescriptor replace(TypeDescriptor root, UnaryOperator<TypeDescriptor> f) {
        return replace(root, 0, f);
    }

    private TypeDescriptor replace(
            TypeDescriptor node, int depth, UnaryOperator<TypeDescriptor> f) {
        if (depth == steps.size()) {
            return f.apply(node);
        }
        Step step = steps.get(depth);
        int child = childIndex(node, step);
        List<TypeDescriptor> children = new ArrayList<>(node.children());
        children.set(child, replace(children.get(child), depth + 1, f));
        return node.withChildren(children);
    }

    private int childIndex(TypeDescriptor node, Step step) {
        CoreTypeFamily family = node.getTypeFamily() instanceof CoreTypeFamily c ? c : null;
        if (step.kind() == Kind.ELEMENT && family == CoreTypeFamily.ARRAY) {
            return 0;
        } else if (step.kind() == Kind.KEY && family == CoreTypeFamily.MAP) {
            return 0;
        } else if (step.kind() == Kind.VALUE && family == CoreTypeFamily.MAP) {
            return 1;
        } else if (step.kind() == Kind.FIELD && family == CoreTypeFamily.STRUCT) {
            int i = node.fieldIndex(step.name());
            if (i >= 0) {
                return i;
            }
            throw new IllegalArgumentException(
                    "type path '"
                            + text
                            + "': "
                            + node.portable()
                            + " has no field '"
                            + step.name()
                            + "'");
        }
        throw new IllegalArgumentException(
                "type path '"
                        + text
                        + "': step "
                        + describe(step)
                        + " does not fit "
                        + node.portable());
    }

    private static String describe(Step step) {
        return switch (step.kind()) {
            case ELEMENT -> "[]";
            case KEY -> "<>";
            case VALUE -> "{}";
            case FIELD -> "." + step.name();
        };
    }

    @Override
    public String toString() {
        return text;
    }
}
