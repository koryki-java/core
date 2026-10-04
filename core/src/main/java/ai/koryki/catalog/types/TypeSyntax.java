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
import java.util.regex.Pattern;

/**
 * Reads the nested spelling of a type, the one DuckDB prints in {@code duckdb_columns()}:
 *
 * <pre>
 * type  := base ( '[' INT? ']' )*              DOUBLE[3][2] is two rows of DOUBLE[3]
 * base  := STRUCT '(' field (',' field)* ')'   ROW(...) and OBJECT(...) (Snowflake) are accepted too
 *        | MAP '(' type ',' type ')'
 *        | LIST '(' type ')' | ARRAY '(' type [',' INT] ')'
 *        | name [ '(' INT (',' INT)* ')' ]     a scalar: DECIMAL(10,2), DOUBLE PRECISION
 * field := ( IDENT | '"' ... '"' ) type        "" inside quotes is one quote
 * </pre>
 *
 * It owns the structure only. A scalar leaf is handed to a {@link Leaf}, so the scalar rules
 * (precision, scale, the known names) stay in {@link TypeDescriptorParser}. Every error names the
 * whole type and the position, because a nested type that fails somewhere in the middle is
 * unreadable otherwise.
 */
public final class TypeSyntax {

    /** Turns the spelling of one scalar into its descriptor. */
    @FunctionalInterface
    public interface Leaf {
        /**
         * @param spelling the leaf as written, {@code DECIMAL(10,2)}
         * @param name the type name, upper case, words joined by one space: {@code DOUBLE
         *     PRECISION}
         * @param params the integers in parentheses, possibly none
         */
        TypeDescriptor leaf(String spelling, String name, List<Integer> params);
    }

    private static final Pattern OPENS_COMPLEX =
            Pattern.compile(
                    "^\\s*(STRUCT|ROW|OBJECT|MAP|LIST|ARRAY)\\s*\\(", Pattern.CASE_INSENSITIVE);

    private TypeSyntax() {}

    /** Whether the text is a nested spelling: brackets, or a constructor in front of a paren. */
    public static boolean looksComplex(String type) {
        return type != null && (type.indexOf('[') >= 0 || OPENS_COMPLEX.matcher(type).find());
    }

    public static TypeDescriptor parse(String type, Leaf leaf) {
        Reader in = new Reader(type, leaf);
        TypeDescriptor result = in.type();
        in.skipSpaces();
        if (!in.atEnd()) {
            throw in.error("unexpected '" + in.peek() + "' after the type");
        }
        return result;
    }

    private static final class Reader {
        private final String text;
        private final Leaf leaf;
        private int pos;

        Reader(String text, Leaf leaf) {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("empty type");
            }
            this.text = text;
            this.leaf = leaf;
        }

        TypeDescriptor type() {
            TypeDescriptor t = base();
            skipSpaces();
            while (!atEnd() && peek() == '[') {
                pos++;
                skipSpaces();
                int size = -1;
                if (!atEnd() && Character.isDigit(peek())) {
                    size = integer();
                    if (size == 0) {
                        throw error("an array size must be positive");
                    }
                }
                expect(']');
                t = TypeDescriptor.array(t, size);
                skipSpaces();
            }
            return t;
        }

        private TypeDescriptor base() {
            skipSpaces();
            int start = pos;
            String name = words();
            if (name.isEmpty()) {
                throw error(atEnd() ? "a type is missing" : "expected a type name");
            }
            skipSpaces();
            boolean open = !atEnd() && peek() == '(';
            switch (name) {
                case "STRUCT", "ROW", "OBJECT" -> {
                    requireOpen(open, name);
                    return struct();
                }
                case "MAP" -> {
                    requireOpen(open, name);
                    pos++;
                    TypeDescriptor key = type();
                    expect(',');
                    TypeDescriptor value = type();
                    expect(')');
                    return TypeDescriptor.map(key, value);
                }
                case "LIST", "ARRAY" -> {
                    requireOpen(open, name);
                    pos++;
                    TypeDescriptor element = type();
                    int size = -1;
                    if (comma()) {
                        skipSpaces();
                        size = integer();
                    }
                    expect(')');
                    return TypeDescriptor.array(element, size);
                }
                default -> {
                    List<Integer> params = new ArrayList<>();
                    if (open) {
                        pos++;
                        do {
                            skipSpaces();
                            params.add(integer());
                        } while (comma());
                        expect(')');
                    }
                    int end = pos;
                    try {
                        return leaf.leaf(text.substring(start, end).strip(), name, params);
                    } catch (IllegalArgumentException e) {
                        // the scalar rules know the leaf, not where it sits in the whole type
                        pos = start;
                        throw error(e.getMessage());
                    }
                }
            }
        }

        private TypeDescriptor struct() {
            pos++; // the opening paren
            List<String> names = new ArrayList<>();
            List<TypeDescriptor> types = new ArrayList<>();
            do {
                skipSpaces();
                int at = pos;
                String field = fieldName();
                if (names.stream().anyMatch(field::equalsIgnoreCase)) {
                    pos = at;
                    throw error("duplicate struct field '" + field + "'");
                }
                names.add(field);
                types.add(type());
            } while (comma());
            expect(')');
            return TypeDescriptor.struct(names, types);
        }

        private String fieldName() {
            if (atEnd()) {
                throw error("a struct field name is missing");
            }
            if (peek() == '"') {
                pos++;
                StringBuilder name = new StringBuilder();
                while (true) {
                    if (atEnd()) {
                        throw error("unterminated quoted field name");
                    }
                    char c = text.charAt(pos++);
                    if (c == '"') {
                        if (!atEnd() && peek() == '"') {
                            name.append('"');
                            pos++;
                        } else {
                            break;
                        }
                    } else {
                        name.append(c);
                    }
                }
                if (name.isEmpty()) {
                    throw error("an empty field name");
                }
                return name.toString();
            }
            int start = pos;
            while (!atEnd() && (Character.isLetterOrDigit(peek()) || peek() == '_')) {
                pos++;
            }
            if (start == pos) {
                throw error("expected a struct field name, found '" + peek() + "'");
            }
            return text.substring(start, pos);
        }

        /** One or more words separated by blanks: {@code DOUBLE PRECISION}. Upper case. */
        private String words() {
            StringBuilder name = new StringBuilder();
            while (true) {
                int start = pos;
                while (!atEnd() && (Character.isLetterOrDigit(peek()) || peek() == '_')) {
                    pos++;
                }
                if (start == pos) {
                    break;
                }
                if (name.length() > 0) {
                    name.append(' ');
                }
                name.append(text, start, pos);
                int afterWord = pos;
                skipSpaces();
                if (atEnd() || !(Character.isLetter(peek()) || peek() == '_')) {
                    pos = afterWord;
                    break;
                }
            }
            return name.toString().toUpperCase();
        }

        private int integer() {
            int start = pos;
            while (!atEnd() && Character.isDigit(peek())) {
                pos++;
            }
            if (start == pos) {
                throw error("expected a number");
            }
            try {
                return Integer.parseInt(text.substring(start, pos));
            } catch (NumberFormatException e) {
                pos = start;
                throw error("number out of range");
            }
        }

        /** Consumes a separating comma, if there is one. */
        private boolean comma() {
            skipSpaces();
            if (!atEnd() && peek() == ',') {
                pos++;
                return true;
            }
            return false;
        }

        private void requireOpen(boolean open, String name) {
            if (!open) {
                throw error(name + " needs a parenthesised body");
            }
        }

        private void expect(char c) {
            skipSpaces();
            if (atEnd() || peek() != c) {
                throw error("expected '" + c + "'" + (atEnd() ? " at the end" : ""));
            }
            pos++;
        }

        void skipSpaces() {
            while (!atEnd() && Character.isWhitespace(peek())) {
                pos++;
            }
        }

        boolean atEnd() {
            return pos >= text.length();
        }

        char peek() {
            return text.charAt(pos);
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(
                    "invalid type '" + text + "' at position " + pos + ": " + message);
        }
    }
}
