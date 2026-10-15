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

    /**
     * The type of a scalar: its family, its encoding if it has one, and the type in the database as
     * the database spells it ({@code SMALLINT}, {@code DECIMAL(10,2)}, {@code VARCHAR(MAX)}). The
     * containers are typed from the entries of their concepts, see {@link ConceptResolver}.
     *
     * @param where what is typed, for the message of a mistake: {@code column 't.c'}
     */
    default TypeDescriptor parse(
            String where, String typeFamily, String typeEncodingName, String dialectType) {
        TypeFamily family = TypeFamilyRegistry.of(typeFamily);

        TypeEncoding typeEncoding = TypeEncodingRegistry.ofNullable(typeEncodingName);

        // Invariant: an encoding binds to exactly one family, which must be the
        // declared family (see TypeEncoding#family).
        if (typeEncoding != null && !typeEncoding.family().equals(family)) {
            throw new IllegalArgumentException(
                    where
                            + ": encoding "
                            + typeEncoding.name()
                            + " is a "
                            + typeEncoding.family().name()
                            + " but it declares family "
                            + family.name());
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
                if (parts.length > 1 && part.trim().equalsIgnoreCase("MAX")) {
                    throw new IllegalArgumentException(
                            "MAX is the only size of a type, not one of several, in dialectType '"
                                    + dialectType
                                    + "'");
                }
                Integer size = size(part, dialectType);
                if (size != null) {
                    params.add(size);
                }
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
                        throw new IllegalArgumentException(
                                where
                                        + ": a "
                                        + core.name()
                                        + " is typed from its entries, not from a dialect type");
            };
        }
        return parseExtended(where, family, dialectType, typeName, typeEncoding, params);
    }

    /**
     * One size parameter of a flat type name: a number, optionally with the unit Oracle puts behind
     * it ({@code VARCHAR2(10 CHAR)}), or {@code MAX} ({@code NVARCHAR(MAX)}), which is no size: the
     * type is unbounded. Null for {@code MAX}.
     */
    private static Integer size(String part, String dialectType) {
        String text = part.trim();
        if (text.equalsIgnoreCase("MAX")) {
            return null;
        }
        java.util.regex.Matcher sized =
                java.util.regex.Pattern.compile(
                                "(\\d+)(?:\\s+(?:CHAR|BYTE))?",
                                java.util.regex.Pattern.CASE_INSENSITIVE)
                        .matcher(text);
        if (!sized.matches()) {
            throw new IllegalArgumentException(
                    "'" + text + "' is not a size in dialectType '" + dialectType + "'");
        }
        return Integer.parseInt(sized.group(1));
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
            String where,
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
