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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The type of a column or an expression. Scalars are a leaf: family, encoding, precision and scale.
 * The complex families ARRAY, STRUCT and MAP are a tree: their {@link #children()} are descriptors
 * again, to any depth, and each leaf keeps its own encoding.
 *
 * <p>Immutable. The physical name of a container is derived from its children, so the tree and its
 * spelling cannot drift apart and equality is structural.
 */
public class TypeDescriptor {

    /** A struct field name that needs no quoting in the canonical spelling. */
    private static final Pattern SIMPLE_NAME = Pattern.compile("[a-z_][a-z0-9_]*");

    public static final TypeDescriptor DATE =
            new TypeDescriptor(TypeNames.TYPE_DATE, null, CoreTypeFamily.DATE);
    public static final TypeDescriptor TIME =
            new TypeDescriptor(TypeNames.TYPE_TIME, null, CoreTypeFamily.TIME);
    public static final TypeDescriptor TIMESTAMP =
            new TypeDescriptor(TypeNames.TYPE_TIMESTAMP, null, CoreTypeFamily.TIMESTAMP);
    public static final TypeDescriptor TEXT =
            new TypeDescriptor(TypeNames.TYPE_TEXT, null, CoreTypeFamily.TEXT);
    public static final TypeDescriptor BOOLEAN =
            new TypeDescriptor(TypeNames.TYPE_BOOLEAN, null, CoreTypeFamily.BOOLEAN);
    public static final TypeDescriptor SMALLINT =
            new TypeDescriptor(TypeNames.TYPE_SMALLINT, null, CoreTypeFamily.INTEGER);
    public static final TypeDescriptor INTEGER =
            new TypeDescriptor(TypeNames.TYPE_INTEGER, null, CoreTypeFamily.INTEGER);
    public static final TypeDescriptor BIGINT =
            new TypeDescriptor(TypeNames.TYPE_BIGINT, null, CoreTypeFamily.INTEGER);
    public static final TypeDescriptor FLOAT =
            new TypeDescriptor(TypeNames.TYPE_FLOAT, null, CoreTypeFamily.FLOAT);
    public static final TypeDescriptor DOUBLE =
            new TypeDescriptor(TypeNames.TYPE_DOUBLE, null, CoreTypeFamily.FLOAT);
    public static final TypeDescriptor DECIMAL =
            new TypeDescriptor(TypeNames.TYPE_DECIMAL, null, CoreTypeFamily.DECIMAL);
    public static final TypeDescriptor INTERVAL =
            new TypeDescriptor(TypeNames.TYPE_INTERVAL, null, CoreTypeFamily.INTERVAL);

    /**
     * Dialect-neutral NULL literal type: no family, so it never dominates in type widening
     * (numericRank 0).
     */
    public static final TypeDescriptor NULL = new TypeDescriptor(TypeNames.TYPE_NULL, null, null);

    private final int precision;
    private final int scale;
    private final TypeFamily typeFamily;
    private final String physicalTypeName;
    private final TypeEncoding typeEncoding;

    /** ARRAY: the element; MAP: key and value; STRUCT: the field types in order; scalars: none. */
    private final List<TypeDescriptor> children;

    /** STRUCT only: the field names, parallel to {@link #children}. */
    private final List<String> fieldNames;

    /** ARRAY only: the fixed size of a DuckDB {@code T[n]}, or -1 for a list. */
    private final int length;

    /**
     * JSON only: what the catalog declares about the content, a tree of ARRAY, STRUCT, MAP and
     * scalars, or null for JSON of unknown content. The value stays JSON in the database; the shape
     * is what a selector chain may rely on.
     */
    private final TypeDescriptor shape;

    public TypeDescriptor(
            String physicalTypeName, TypeEncoding typeEncoding, TypeFamily typeFamily) {
        this(physicalTypeName, typeEncoding, typeFamily, -1, -1);
    }

    public TypeDescriptor(
            String physicalTypeName,
            TypeEncoding typeEncoding,
            TypeFamily typeFamily,
            int precision,
            int scale) {
        this(
                physicalTypeName,
                typeEncoding,
                typeFamily,
                precision,
                scale,
                List.of(),
                List.of(),
                -1);
    }

    private TypeDescriptor(
            String physicalTypeName,
            TypeEncoding typeEncoding,
            TypeFamily typeFamily,
            int precision,
            int scale,
            List<TypeDescriptor> children,
            List<String> fieldNames,
            int length) {
        this(
                physicalTypeName,
                typeEncoding,
                typeFamily,
                precision,
                scale,
                children,
                fieldNames,
                length,
                null);
    }

    private TypeDescriptor(
            String physicalTypeName,
            TypeEncoding typeEncoding,
            TypeFamily typeFamily,
            int precision,
            int scale,
            List<TypeDescriptor> children,
            List<String> fieldNames,
            int length,
            TypeDescriptor shape) {
        this.shape = shape;
        this.children = List.copyOf(children);
        this.fieldNames = List.copyOf(fieldNames);
        this.length = length;
        this.physicalTypeName = physicalTypeName;
        this.typeFamily = typeFamily;
        this.precision = precision;
        this.scale = scale;
        // An absent encoding means "stored in the family's natural physical type": resolve it to
        // the
        // per-family NATIVE so the (family, encoding) pair is always meaningful — never null — at
        // output
        // time (see NativeEncoding). The typeless NULL literal (no family) keeps a null encoding.
        this.typeEncoding =
                (typeEncoding == null && typeFamily != null)
                        ? NativeEncoding.of(typeFamily)
                        : typeEncoding;
    }

    public TypeFamily getTypeFamily() {
        return typeFamily;
    }

    public int getPrecision() {
        return precision;
    }

    public int getScale() {
        return scale;
    }

    public String getPhysicalTypeName() {
        return physicalTypeName;
    }

    public TypeEncoding getTypeEncoding() {
        return typeEncoding;
    }

    // ---- complex types ------------------------------------------------------------------------

    /** A list of {@code element}: DuckDB {@code T[]}. */
    public static TypeDescriptor array(TypeDescriptor element) {
        return array(element, -1);
    }

    /** A fixed-size array of {@code element}: DuckDB {@code T[n]}; {@code length} -1 is a list. */
    public static TypeDescriptor array(TypeDescriptor element, int length) {
        Objects.requireNonNull(element, "element");
        if (length == 0 || length < -1) {
            throw new IllegalArgumentException("array length must be positive, or -1: " + length);
        }
        return new TypeDescriptor(
                element.getPhysicalTypeName() + (length < 0 ? "[]" : "[" + length + "]"),
                null,
                CoreTypeFamily.ARRAY,
                -1,
                -1,
                List.of(element),
                List.of(),
                length);
    }

    public static TypeDescriptor map(TypeDescriptor key, TypeDescriptor value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        return new TypeDescriptor(
                "MAP(" + key.getPhysicalTypeName() + ", " + value.getPhysicalTypeName() + ")",
                null,
                CoreTypeFamily.MAP,
                -1,
                -1,
                List.of(key, value),
                List.of(),
                -1);
    }

    /** A struct; {@code names} and {@code types} are parallel, names unique ignoring case. */
    public static TypeDescriptor struct(List<String> names, List<TypeDescriptor> types) {
        if (names.isEmpty() || names.size() != types.size()) {
            throw new IllegalArgumentException(
                    "a struct needs one name per field and at least one field: "
                            + names
                            + " / "
                            + types.size()
                            + " types");
        }
        if (names.stream().map(String::toLowerCase).distinct().count() != names.size()) {
            throw new IllegalArgumentException("duplicate struct field name in " + names);
        }
        String spelling =
                IntStream.range(0, names.size())
                        .mapToObj(
                                i ->
                                        quoteName(names.get(i))
                                                + " "
                                                + types.get(i).getPhysicalTypeName())
                        .collect(Collectors.joining(", ", "STRUCT(", ")"));
        return new TypeDescriptor(spelling, null, CoreTypeFamily.STRUCT, -1, -1, types, names, -1);
    }

    private static String quoteName(String name) {
        return SIMPLE_NAME.matcher(name).matches() ? name : '"' + name.replace("\"", "\"\"") + '"';
    }

    /** The same JSON type with a declared content; null removes it. */
    public TypeDescriptor withShape(TypeDescriptor shape) {
        if (typeFamily != CoreTypeFamily.JSON) {
            throw new IllegalStateException("only JSON has a shape, this is " + portable());
        }
        return new TypeDescriptor(
                physicalTypeName,
                typeEncoding,
                typeFamily,
                precision,
                scale,
                children,
                fieldNames,
                length,
                shape);
    }

    /** The declared content of a JSON value, or null when nothing is declared. */
    public TypeDescriptor shape() {
        return shape;
    }

    public boolean isComplex() {
        return typeFamily == CoreTypeFamily.ARRAY
                || typeFamily == CoreTypeFamily.STRUCT
                || typeFamily == CoreTypeFamily.MAP;
    }

    /** Element, key and value, or the field types in order; empty for a scalar. */
    public List<TypeDescriptor> children() {
        return children;
    }

    /** The field names of a struct, parallel to {@link #children()}; empty otherwise. */
    public List<String> fieldNames() {
        return fieldNames;
    }

    /** The fixed size of an ARRAY, or -1 for a list and for every other type. */
    public int length() {
        return length;
    }

    public TypeDescriptor element() {
        require(CoreTypeFamily.ARRAY, "element");
        return children.get(0);
    }

    public TypeDescriptor key() {
        require(CoreTypeFamily.MAP, "key");
        return children.get(0);
    }

    public TypeDescriptor value() {
        require(CoreTypeFamily.MAP, "value");
        return children.get(1);
    }

    /** A struct field by name: exact match first, then ignoring case (DuckDB is case-blind). */
    public Optional<TypeDescriptor> field(String name) {
        int i = fieldIndex(name);
        return i < 0 ? Optional.empty() : Optional.of(children.get(i));
    }

    /** Position of a struct field, exact match first, then ignoring case; -1 when absent. */
    int fieldIndex(String name) {
        require(CoreTypeFamily.STRUCT, "field");
        int i = fieldNames.indexOf(name);
        for (int k = 0; i < 0 && k < fieldNames.size(); k++) {
            if (fieldNames.get(k).equalsIgnoreCase(name)) {
                i = k;
            }
        }
        return i;
    }

    private void require(CoreTypeFamily expected, String what) {
        if (typeFamily != expected) {
            throw new IllegalStateException(
                    what + "() needs an " + expected + ", this is " + this.portable());
        }
    }

    /**
     * The same container over other children: names, length and kind are kept, the spelling is
     * derived again. This is how a leaf deep inside a tree is replaced.
     */
    TypeDescriptor withChildren(List<TypeDescriptor> replaced) {
        if (replaced.size() != children.size()) {
            throw new IllegalArgumentException(
                    portable() + " has " + children.size() + " children, not " + replaced.size());
        }
        if (typeFamily == CoreTypeFamily.ARRAY) {
            return array(replaced.get(0), length);
        } else if (typeFamily == CoreTypeFamily.MAP) {
            return map(replaced.get(0), replaced.get(1));
        } else if (typeFamily == CoreTypeFamily.STRUCT) {
            return struct(fieldNames, replaced);
        }
        throw new IllegalStateException(portable() + " has no children");
    }

    /**
     * A dialect-neutral notation, for goldens and prompts: the family of a scalar, {@code
     * ARRAY(TEXT)}, {@code ARRAY(FLOAT,3)}, {@code MAP(TEXT,INTEGER)}, {@code
     * STRUCT(w:FLOAT,h:FLOAT)}. Encodings are not shown; the family is what queries reason about.
     */
    public String portable() {
        if (typeFamily == null) {
            return TypeNames.TYPE_NULL;
        }
        if (typeFamily == CoreTypeFamily.ARRAY) {
            return "ARRAY(" + children.get(0).portable() + (length < 0 ? "" : "," + length) + ")";
        } else if (typeFamily == CoreTypeFamily.MAP) {
            return "MAP(" + children.get(0).portable() + "," + children.get(1).portable() + ")";
        } else if (typeFamily == CoreTypeFamily.STRUCT) {
            return IntStream.range(0, children.size())
                    .mapToObj(i -> fieldNames.get(i) + ":" + children.get(i).portable())
                    .collect(Collectors.joining(",", "STRUCT(", ")"));
        }
        if (shape != null) {
            return "JSON(" + shape.portable() + ")";
        }
        return typeFamily.name();
    }

    @Override
    public String toString() {
        return "TypeDescriptor{"
                + "typeFamily="
                + typeFamily
                + ", physicalTypeName='"
                + physicalTypeName
                + '\''
                + ", typeEncoding="
                + typeEncoding
                + ", precision="
                + precision
                + ", scale="
                + scale
                // a scalar prints exactly as before; only a container shows its tree
                + (isComplex() || shape != null ? ", type=" + portable() : "")
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        TypeDescriptor that = (TypeDescriptor) o;
        return precision == that.precision
                && scale == that.scale
                && length == that.length
                && Objects.equals(typeFamily, that.typeFamily)
                && Objects.equals(physicalTypeName, that.physicalTypeName)
                && Objects.equals(typeEncoding, that.typeEncoding)
                && children.equals(that.children)
                && fieldNames.equals(that.fieldNames)
                && Objects.equals(shape, that.shape);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                precision,
                scale,
                typeFamily,
                physicalTypeName,
                typeEncoding,
                children,
                fieldNames,
                length,
                shape);
    }
}
