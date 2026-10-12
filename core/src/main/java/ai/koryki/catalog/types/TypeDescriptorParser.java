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

import ai.koryki.catalog.schema.Column;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface TypeDescriptorParser {

    public static final Pattern TYPE_PATTERN =
            Pattern.compile(
                    "^\\s*([A-Z_ 0-9]+)\\s*(?:\\(([^)]*)\\))?\\s*,?\\s*$",
                    Pattern.CASE_INSENSITIVE);

    public static final Pattern INTERVAL_PATTERN =
            Pattern.compile(
                    "^INTERVAL\\s+"
                            + "([A-Z]+)"
                            + // start field
                            "(?:\\((\\d+)\\))?"
                            + // optional precision of the start field
                            "\\s+TO\\s+"
                            + "([A-Z]+)"
                            + // end field
                            "(?:\\((\\d+)\\))?"
                            + // optional precision of the end field
                            "$",
                    Pattern.CASE_INSENSITIVE);

    default TypeDescriptor parse(Column column) {
        TypeFamily family = TypeFamilyRegistry.of(column.getTypeFamily());

        String dialectType = column.getDialectType();
        TypeEncoding typeEncoding = TypeEncodingRegistry.ofNullable(column.getTypeEncoding());

        // Invariant: an encoding binds to exactly one family, which must be the
        // column's declared family (see TypeEncoding#family).
        if (typeEncoding != null && !typeEncoding.family().equals(family)) {
            throw new IllegalArgumentException(
                    "column '"
                            + column.getName()
                            + "': encoding "
                            + typeEncoding.name()
                            + " is a "
                            + typeEncoding.family().name()
                            + " but the column declares family "
                            + family.name());
        }

        // A column that carries its structure (fields, element, key, value) is read from it; the
        // spelling, if it has one, has to agree.
        if (TypeTree.hasTree(column)) {
            return parseTree(column, family, typeEncoding, dialectType);
        }

        // A nested spelling (INTEGER[], STRUCT(...), MAP(...)) is a tree and has its own reader; a
        // column that declares a container family goes there too, so that a flat name under a
        // container family reports the mismatch instead of "Invalid dialectType format".
        if (TypeSyntax.looksComplex(dialectType) || isContainer(family)) {
            return parseComplex(column, family, typeEncoding, dialectType);
        }

        Matcher matcher = TYPE_PATTERN.matcher(dialectType);

        Matcher intervalmatcher = INTERVAL_PATTERN.matcher(dialectType);
        if (family.equals(CoreTypeFamily.INTERVAL) && intervalmatcher.matches()) {
            // special case for oracle-interval
            return parseInterval(dialectType, null, typeEncoding);
        }

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid dialectType format: " + dialectType);
        }

        String typeName = matcher.group(1).toUpperCase();
        List<Integer> params = new ArrayList<>();

        String paramGroup = matcher.group(2);
        if (paramGroup != null && !paramGroup.trim().isEmpty()) {
            String[] parts = paramGroup.split(",");
            for (String part : parts) {
                params.add(Integer.parseInt(part.trim()));
            }
        }

        if (family instanceof CoreTypeFamily core) {
            return switch (core) {
                case BLOB -> parseBlob(dialectType, typeEncoding);
                case BOOLEAN -> parseBoolean(dialectType, typeEncoding);
                case DATE -> parseDate(dialectType, typeEncoding);
                case DECIMAL -> parseDecimal(dialectType, typeEncoding, params);
                case FLOAT -> parseFloat(dialectType, typeName, typeEncoding);
                case INTEGER -> parseInteger(dialectType, typeName, typeEncoding, params);
                case TIME -> parseTime(dialectType, typeName, typeEncoding, params);
                case INTERVAL -> parseInterval(dialectType, typeName, typeEncoding);
                case TIMESTAMP -> parseTimestamp(dialectType, typeName, typeEncoding, params);
                case TEXT -> parseText(dialectType, typeName, typeEncoding, params);
                case JSON -> parseJson(dialectType, typeName, typeEncoding);
                case UUID -> parseUuid(dialectType, typeName, typeEncoding);
                case ARRAY, STRUCT, MAP ->
                        throw new IllegalStateException("containers are parsed by parseComplex");
            };
        }
        return parseExtended(column, family, dialectType, typeName, typeEncoding, params);
    }

    private static boolean isContainer(TypeFamily family) {
        return CoreTypeFamily.ARRAY.equals(family)
                || CoreTypeFamily.STRUCT.equals(family)
                || CoreTypeFamily.MAP.equals(family);
    }

    /**
     * A nested column: the structure comes from the spelling in {@code dialectType}, the leaves are
     * typed by {@link #parseLeaf}, and {@code column.getNested()} may then refine single leaves
     * with an encoding. The family the column declares must be the family of the root.
     */
    default TypeDescriptor parseComplex(
            Column column, TypeFamily declared, TypeEncoding encoding, String dialectType) {
        if (encoding != null) {
            throw new IllegalArgumentException(
                    "column '"
                            + column.getName()
                            + "': a nested column has no encoding of its own; "
                            + "an encoding belongs to a leaf, declared under 'nested'");
        }
        TypeDescriptor root;
        try {
            root = TypeSyntax.parse(dialectType, this::parseLeaf);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "column '" + column.getName() + "': " + e.getMessage(), e);
        }
        if (!declared.equals(root.getTypeFamily())) {
            throw new IllegalArgumentException(
                    "column '"
                            + column.getName()
                            + "': declared "
                            + declared.name()
                            + " but '"
                            + dialectType
                            + "' is a "
                            + root.getTypeFamily().name());
        }
        return root;
    }

    /**
     * A column that describes its own structure: the type is built from its nodes, to any depth,
     * and a spelling in {@code dialectType}, if there is one, has to be the same type.
     */
    default TypeDescriptor parseTree(
            Column column, TypeFamily declared, TypeEncoding encoding, String dialectType) {
        if (encoding != null && !CoreTypeFamily.JSON.equals(declared)) {
            throw new IllegalArgumentException(
                    "column '"
                            + column.getName()
                            + "': a nested column has no encoding of its own; "
                            + "an encoding belongs to a leaf, declared on its node");
        }
        TypeDescriptor type;
        try {
            if (CoreTypeFamily.JSON.equals(declared)) {
                type =
                        parseJson(dialectType == null ? "JSON" : dialectType, "JSON", encoding)
                                .withShape(members(column, column.getName()));
            } else {
                type = node(column, column.getName());
            }
        } catch (IllegalArgumentException e) {
            throw e.getMessage() != null && e.getMessage().startsWith("column '")
                    ? e
                    : new IllegalArgumentException(
                            "column '" + column.getName() + "': " + e.getMessage(), e);
        }
        if (!CoreTypeFamily.JSON.equals(declared)
                && dialectType != null
                && TypeSyntax.looksComplex(dialectType)) {
            TypeDescriptor spelled;
            try {
                spelled = TypeSyntax.parse(dialectType, this::parseLeaf);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "column '" + column.getName() + "': " + e.getMessage(), e);
            }
            if (!agrees(column, spelled)) {
                throw new IllegalArgumentException(
                        "column '"
                                + column.getName()
                                + "': its fields describe "
                                + type.portable()
                                + " but dialectType '"
                                + dialectType
                                + "' spells "
                                + spelled.portable());
            }
        }
        return type;
    }

    /**
     * Whether a spelling and the nodes describe the same structure: the same containers, field
     * names and sizes, and for a leaf that says its dialect type, the same physical type. The
     * family of a leaf may differ, because a leaf is refined by an encoding (a BIGINT that is a
     * timestamp).
     */
    private static boolean agrees(Column node, TypeDescriptor spelled) {
        TypeFamily family = TypeFamilyRegistry.of(node.getTypeFamily());
        if (CoreTypeFamily.ARRAY.equals(family)) {
            return CoreTypeFamily.ARRAY.equals(spelled.getTypeFamily())
                    && (node.getLength() == null ? -1 : node.getLength()) == spelled.length()
                    && node.getElement() != null
                    && agrees(node.getElement(), spelled.element());
        } else if (CoreTypeFamily.MAP.equals(family)) {
            return CoreTypeFamily.MAP.equals(spelled.getTypeFamily())
                    && node.getKey() != null
                    && node.getValue() != null
                    && agrees(node.getKey(), spelled.key())
                    && agrees(node.getValue(), spelled.value());
        } else if (CoreTypeFamily.STRUCT.equals(family)) {
            if (!CoreTypeFamily.STRUCT.equals(spelled.getTypeFamily())
                    || node.getFields() == null
                    || node.getFields().size() != spelled.children().size()) {
                return false;
            }
            for (int i = 0; i < node.getFields().size(); i++) {
                if (!node.getFields().get(i).getName().equalsIgnoreCase(spelled.fieldNames().get(i))
                        || !agrees(node.getFields().get(i), spelled.children().get(i))) {
                    return false;
                }
            }
            return true;
        }
        return spelled.isComplex()
                ? false
                : node.getDialectType() == null
                        || node.getDialectType()
                                .replaceAll("\\s+", "")
                                .equalsIgnoreCase(
                                        spelled.getPhysicalTypeName().replaceAll("\\s+", ""));
    }

    /** One node of a nested column as a type: a container from its children, a leaf as a column. */
    private TypeDescriptor node(Column node, String where) {
        if (node.getTypeFamily() == null) {
            throw new IllegalArgumentException(
                    "column '" + where + "': a node of a nested column has no typeFamily");
        }
        TypeFamily family = TypeFamilyRegistry.of(node.getTypeFamily());
        if (CoreTypeFamily.ARRAY.equals(family)) {
            if (node.getElement() == null) {
                throw new IllegalArgumentException(
                        "column '" + where + "': an ARRAY has an element");
            }
            return TypeDescriptor.array(
                    node(node.getElement(), where + "[]"),
                    node.getLength() == null ? -1 : node.getLength());
        } else if (CoreTypeFamily.STRUCT.equals(family)) {
            if (node.getFields() == null || node.getFields().isEmpty()) {
                throw new IllegalArgumentException("column '" + where + "': a STRUCT has fields");
            }
            List<String> names = new ArrayList<>();
            List<TypeDescriptor> types = new ArrayList<>();
            for (Column field : node.getFields()) {
                if (field.getName() == null || field.getName().isBlank()) {
                    throw new IllegalArgumentException(
                            "column '" + where + "': a field of a STRUCT has a name");
                }
                names.add(field.getName());
                types.add(node(field, where + "." + field.getName()));
            }
            return TypeDescriptor.struct(names, types);
        } else if (CoreTypeFamily.MAP.equals(family)) {
            if (node.getKey() == null || node.getValue() == null) {
                throw new IllegalArgumentException(
                        "column '" + where + "': a MAP has a key and a value");
            }
            return TypeDescriptor.map(
                    node(node.getKey(), where + "<>"), node(node.getValue(), where + "{}"));
        } else if (CoreTypeFamily.JSON.equals(family) && TypeTree.hasTree(node)) {
            return parseJson(
                            node.getDialectType() == null ? "JSON" : node.getDialectType(),
                            "JSON",
                            TypeEncodingRegistry.ofNullable(node.getTypeEncoding()))
                    .withShape(members(node, where));
        }
        Column leaf = new Column(where);
        leaf.setTypeFamily(node.getTypeFamily());
        leaf.setTypeEncoding(node.getTypeEncoding());
        leaf.setDialectType(
                node.getDialectType() != null ? node.getDialectType() : defaultType(family));
        return parse(leaf);
    }

    /** The physical type of a leaf that does not say: the plain name of its family. */
    private static String defaultType(TypeFamily family) {
        if (CoreTypeFamily.TEXT.equals(family)) {
            return "VARCHAR";
        } else if (CoreTypeFamily.FLOAT.equals(family)) {
            return "DOUBLE";
        }
        return family.name();
    }

    /**
     * What a JSON node says about its content: a STRUCT of the members it lists, an ARRAY of its
     * element, a MAP with text keys of its value. The members are scalars of the families a JSON
     * value can be read as, or such a shape again. Nothing is said about members not listed.
     */
    private TypeDescriptor members(Column json, String where) {
        if (json.getFields() != null && !json.getFields().isEmpty()) {
            List<String> names = new ArrayList<>();
            List<TypeDescriptor> types = new ArrayList<>();
            for (Column field : json.getFields()) {
                names.add(field.getName());
                types.add(member(field, where + "." + field.getName()));
            }
            return TypeDescriptor.struct(names, types);
        } else if (json.getElement() != null) {
            return TypeDescriptor.array(member(json.getElement(), where + "[]"));
        } else if (json.getValue() != null) {
            return TypeDescriptor.map(TypeDescriptor.TEXT, member(json.getValue(), where + "{}"));
        } else if (json.getKey() != null) {
            throw new IllegalArgumentException(
                    "column '"
                            + where
                            + "': a JSON object has text keys, a key cannot be declared");
        }
        throw new IllegalArgumentException(
                "column '" + where + "': a JSON node declares no members");
    }

    private TypeDescriptor member(Column node, String where) {
        TypeFamily family = TypeFamilyRegistry.of(node.getTypeFamily());
        if (TypeTree.hasTree(node)) {
            return members(node, where);
        } else if (CoreTypeFamily.BOOLEAN.equals(family)) {
            return TypeDescriptor.BOOLEAN;
        } else if (CoreTypeFamily.INTEGER.equals(family)) {
            return TypeDescriptor.INTEGER;
        } else if (CoreTypeFamily.FLOAT.equals(family)) {
            return TypeDescriptor.DOUBLE;
        } else if (CoreTypeFamily.DECIMAL.equals(family)) {
            return TypeDescriptor.DECIMAL;
        } else if (CoreTypeFamily.TEXT.equals(family)) {
            return TypeDescriptor.TEXT;
        }
        throw new IllegalArgumentException(
                "column '"
                        + where
                        + "': a JSON member can be declared BOOLEAN, INTEGER, FLOAT, DECIMAL or TEXT,"
                        + " or have members of its own, not "
                        + node.getTypeFamily());
    }

    /**
     * One scalar inside a nested spelling. It has no declared family, so the family is inferred
     * from the name, and the scalar rules above ({@code parseDecimal}, {@code parseText}, ...) do
     * the rest, so precision, scale and the known names stay in one place.
     */
    default TypeDescriptor parseLeaf(String spelling, String name, List<Integer> params) {
        TypeFamily family = inferFamily(name);
        if (family instanceof CoreTypeFamily core) {
            return switch (core) {
                case BLOB -> parseBlob(spelling, null);
                case BOOLEAN -> parseBoolean(spelling, null);
                case DATE -> parseDate(spelling, null);
                case DECIMAL -> parseDecimal(spelling, null, params);
                case FLOAT -> parseFloat(spelling, name, null);
                case INTEGER -> parseInteger(spelling, name, null, params);
                case TIME -> parseTime(spelling, name, null, params);
                case INTERVAL -> parseInterval(spelling, name, null);
                case TIMESTAMP -> parseTimestamp(spelling, name, null, params);
                case TEXT -> parseText(spelling, name, null, params);
                case JSON -> parseJson(spelling, name, null);
                case UUID -> parseUuid(spelling, name, null);
                case ARRAY, STRUCT, MAP ->
                        throw new IllegalStateException("'" + name + "' is not a scalar");
            };
        }
        throw new IllegalArgumentException(
                "'" + spelling + "' is a " + family.name() + ", which has no leaf parser");
    }

    /**
     * The family of a scalar type name, upper case. The names DuckDB prints, plus the common
     * spellings of the other dialects; a dialect parser overrides this to add its own.
     */
    default TypeFamily inferFamily(String name) {
        if (name.startsWith("TIMESTAMP") || name.startsWith("DATETIME")) {
            return CoreTypeFamily.TIMESTAMP;
        } else if (name.equals("TIME") || name.startsWith("TIME ") || name.equals("TIMETZ")) {
            return CoreTypeFamily.TIME;
        } else if (name.startsWith("INTERVAL")) {
            return CoreTypeFamily.INTERVAL;
        }
        return switch (name) {
            case "TINYINT",
                    "SMALLINT",
                    "INTEGER",
                    "INT",
                    "BIGINT",
                    "HUGEINT",
                    "UTINYINT",
                    "USMALLINT",
                    "UINTEGER",
                    "UBIGINT" ->
                    CoreTypeFamily.INTEGER;
            case "DECIMAL", "NUMERIC", "NUMBER" -> CoreTypeFamily.DECIMAL;
            case "FLOAT", "REAL", "DOUBLE", "DOUBLE PRECISION", "BINARY_DOUBLE", "BINARY_FLOAT" ->
                    CoreTypeFamily.FLOAT;
            case "BOOLEAN", "BOOL" -> CoreTypeFamily.BOOLEAN;
            case "VARCHAR",
                    "VARCHAR2",
                    "NVARCHAR2",
                    "NCHAR",
                    "TEXT",
                    "STRING",
                    "CHAR",
                    "BPCHAR",
                    "NVARCHAR" ->
                    CoreTypeFamily.TEXT;
            case "DATE" -> CoreTypeFamily.DATE;
            case "UUID" -> CoreTypeFamily.UUID;
            case "JSON" -> CoreTypeFamily.JSON;
            case "BLOB", "BYTEA", "VARBINARY", "BINARY" -> CoreTypeFamily.BLOB;
            default -> throw new IllegalArgumentException("unknown type name '" + name + "'");
        };
    }

    default TypeDescriptor parseExtended(
            Column column,
            TypeFamily family,
            String dialectType,
            String typeName,
            TypeEncoding typeEncoding,
            List<Integer> params) {
        throw new IllegalArgumentException("Unknown type family: " + family.name());
    }

    default TypeDescriptor parseBlob(String dialectType, TypeEncoding typeEncoding) {
        return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.BLOB);
    }

    default TypeDescriptor parseBoolean(String dialectType, TypeEncoding typeEncoding) {
        return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.BOOLEAN);
    }

    default TypeDescriptor parseDate(String dialectType, TypeEncoding typeEncoding) {
        return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.DATE);
    }

    default TypeDescriptor parseDecimal(
            String dialectType, TypeEncoding typeEncoding, List<Integer> params) {

        return numericType(CoreTypeFamily.DECIMAL, dialectType, typeEncoding, params);
    }

    private static TypeDescriptor numericType(
            TypeFamily family,
            String dialectType,
            TypeEncoding typeEncoding,
            List<Integer> params) {
        if (params.isEmpty()) {
            return new TypeDescriptor(dialectType, typeEncoding, family);
        } else if (params.size() == 1) {
            return new TypeDescriptor(dialectType, typeEncoding, family, params.get(0), -1);
        } else if (params.size() == 2) {
            return new TypeDescriptor(
                    dialectType, typeEncoding, family, params.get(0), params.get(1));
        } else {
            throw new IllegalArgumentException(
                    "'"
                            + dialectType
                            + "' carries "
                            + params.size()
                            + " size parameters; a numeric type takes at most two (precision, scale)");
        }
    }

    /**
     * Single optional size parameter (char length / temporal fractional-seconds precision) ->
     * precision field.
     */
    private static TypeDescriptor sized(
            TypeFamily family,
            String dialectType,
            TypeEncoding typeEncoding,
            List<Integer> params) {
        return params.isEmpty()
                ? new TypeDescriptor(dialectType, typeEncoding, family)
                : new TypeDescriptor(dialectType, typeEncoding, family, params.get(0), -1);
    }

    /**
     * The approximate-numeric spellings. All of them are one family — how wide the engine's float
     * is shows in the value, not in the type KQL reasons about.
     *
     * <p>{@code DOUBLE PRECISION} and {@code BINARY_DOUBLE} were missing, which is only visible
     * once a column actually declares family FLOAT: until then no tested schema used it, and
     * PostgreSQL's and Oracle's own spellings for their 8-byte float were unparseable. The failure
     * was a bare {@code IllegalArgumentException} with no message — it named neither the type nor
     * the column, and surfaced as an initializationError with nothing to act on.
     */
    default TypeDescriptor parseFloat(String dialectType, String type, TypeEncoding typeEncoding) {

        for (String known :
                List.of(
                        TypeNames.TYPE_FLOAT,
                        TypeNames.TYPE_REAL,
                        TypeNames.TYPE_DOUBLE,
                        TypeNames.TYPE_DOUBLE_PRECISION,
                        TypeNames.TYPE_BINARY_DOUBLE,
                        TypeNames.TYPE_BINARY_FLOAT)) {
            if (type.equalsIgnoreCase(known)) {
                return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.FLOAT);
            }
        }
        throw new IllegalArgumentException(
                "'"
                        + dialectType
                        + "' is declared as an approximate "
                        + "numeric, but '"
                        + type
                        + "' is not a float type this parser knows");
    }

    default TypeDescriptor parseInteger(
            String dialectType, String type, TypeEncoding typeEncoding, List<Integer> params) {

        if (type.equalsIgnoreCase(TypeNames.TYPE_TINYINT)) {
            return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.INTEGER);
        } else if (type.equalsIgnoreCase(TypeNames.TYPE_SMALLINT)) {
            return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.INTEGER);
        } else if (type.equalsIgnoreCase(TypeNames.TYPE_INTEGER)) {
            return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.INTEGER);
        } else if (type.equalsIgnoreCase(TypeNames.TYPE_INT)) {
            return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.INTEGER);
        } else if (type.equalsIgnoreCase(TypeNames.TYPE_BIGINT)) {
            return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.INTEGER);
        } else if (type.equalsIgnoreCase(TypeNames.TYPE_HUGEINT)) {
            return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.INTEGER);
        } else if (type.equalsIgnoreCase(TypeNames.TYPE_UTINYINT)) {
            return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.INTEGER);
        } else if (type.equalsIgnoreCase(TypeNames.TYPE_USMALLINT)) {
            return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.INTEGER);
        } else if (type.equalsIgnoreCase(TypeNames.TYPE_UINTEGER)) {
            return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.INTEGER);
        } else if (type.equalsIgnoreCase(TypeNames.TYPE_UBIGINT)) {
            return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.INTEGER);
        } else if (type.equalsIgnoreCase(TypeNames.TYPE_NUMERIC)) {
            return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.INTEGER);
        } else if (type.toLowerCase().startsWith("decimal")) {
            return numericType(CoreTypeFamily.INTEGER, dialectType, typeEncoding, params);
        } else if (type.toLowerCase().startsWith("number")) {
            return numericType(CoreTypeFamily.INTEGER, dialectType, typeEncoding, params);
        } else {
            throw new IllegalArgumentException(type);
        }
    }

    default TypeDescriptor parseTime(
            String dialectType, String type, TypeEncoding typeEncoding, List<Integer> params) {
        return sized(CoreTypeFamily.TIME, dialectType, typeEncoding, params);
    }

    default TypeDescriptor parseInterval(
            String dialectType, String type, TypeEncoding typeEncoding) {

        Matcher matcher = INTERVAL_PATTERN.matcher(dialectType);

        if (matcher.matches()) {

            String startUnit = matcher.group(1).toUpperCase();
            String startPrecision = matcher.group(2);

            String endUnit = matcher.group(3).toUpperCase();
            String endPrecision = matcher.group(4);

            int scale = -1;
            if (startPrecision != null) {
                scale = Integer.parseInt(startPrecision);
            }
            int precision = -1;
            if (endPrecision != null) {
                precision = Integer.parseInt(endPrecision);
            }

            if (startUnit.equalsIgnoreCase("YEAR") && endUnit.equalsIgnoreCase("MONTH")) {
                return new TypeDescriptor(
                        dialectType, typeEncoding, CoreTypeFamily.INTERVAL, precision, scale);
            } else if (startUnit.equalsIgnoreCase("DAY") && endUnit.equalsIgnoreCase("SECOND")) {
                return new TypeDescriptor(
                        dialectType, typeEncoding, CoreTypeFamily.INTERVAL, precision, scale);
            } else {
                throw new IllegalArgumentException(type);
            }
        }

        if (type.equalsIgnoreCase(TypeNames.TYPE_INTERVAL)) {
            return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.INTERVAL);
        } else if (type.equalsIgnoreCase(TypeNames.TYPE_SMALLINT)) {
            return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.INTERVAL);
        } else if (type.equalsIgnoreCase(TypeNames.TYPE_INTEGER)) {
            return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.INTERVAL);
        } else if (type.equalsIgnoreCase(TypeNames.TYPE_BIGINT)) {
            return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.INTERVAL);
        } else if (type.equalsIgnoreCase(TypeNames.TYPE_VARCHAR)) {
            return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.INTERVAL);
        } else {
            throw new IllegalArgumentException(type);
        }
    }

    default TypeDescriptor parseTimestamp(
            String dialectType, String type, TypeEncoding typeEncoding, List<Integer> params) {

        // An explicit encoding defines the timestamp semantics; the physical
        // storage type is then free-form metadata (epoch integer, ISO text,
        // zone-aware, ...), like the date/boolean/uuid families already allow.
        if (typeEncoding != null) {
            return sized(CoreTypeFamily.TIMESTAMP, dialectType, typeEncoding, params);
        }

        if (type.equalsIgnoreCase(TypeNames.TYPE_TIMESTAMP)
                || type.equalsIgnoreCase(TypeNames.TYPE_TIMESTAMP_S)
                || type.equalsIgnoreCase(TypeNames.TYPE_TIMESTAMP_MS)
                || type.equalsIgnoreCase(TypeNames.TYPE_TIMESTAMP_NS)
                || type.equalsIgnoreCase(TypeNames.TYPE_TIMESTAMP_NTZ)
                || type.equalsIgnoreCase(TypeNames.TYPE_DATETIME)
                || type.equalsIgnoreCase(TypeNames.TYPE_DATETIME2)
                || type.equalsIgnoreCase(TypeNames.TYPE_TIMESTAMPTZ)
                || type.equalsIgnoreCase(TypeNames.TYPE_TIMESTAM_WITH_TIMEZONE)
                || type.equalsIgnoreCase(TypeNames.TYPE_TIMESTAMP_WITH_LOCAL_TIME_ZONE)) {
            return sized(CoreTypeFamily.TIMESTAMP, dialectType, typeEncoding, params);
        }
        throw new IllegalArgumentException(dialectType);
    }

    default TypeDescriptor parseJson(String dialectType, String type, TypeEncoding typeEncoding) {
        return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.JSON);
    }

    default TypeDescriptor parseUuid(String dialectType, String type, TypeEncoding typeEncoding) {
        return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.UUID);
    }

    default TypeDescriptor parseText(
            String dialectType, String string, TypeEncoding typeEncoding, List<Integer> params) {
        return sized(CoreTypeFamily.TEXT, dialectType, typeEncoding, params);
    }
}
