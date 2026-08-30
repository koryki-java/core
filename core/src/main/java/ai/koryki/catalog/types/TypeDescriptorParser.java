package ai.koryki.catalog.types;

import ai.koryki.catalog.schema.Column;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface TypeDescriptorParser {

    public static final Pattern TYPE_PATTERN =
            Pattern.compile("^\\s*([A-Z_ 0-9]+)\\s*(?:\\(([^)]*)\\))?\\s*,?\\s*$", Pattern.CASE_INSENSITIVE);


    public static final Pattern INTERVAL_PATTERN =
            Pattern.compile(
                    "^INTERVAL\\s+" +
                            "([A-Z]+)" +                  // Starttyp
                            "(?:\\((\\d+)\\))?" +         // optionale Präzision Start
                            "\\s+TO\\s+" +
                            "([A-Z]+)" +                  // Endtyp
                            "(?:\\((\\d+)\\))?" +         // optionale Präzision Ende
                            "$",
                    Pattern.CASE_INSENSITIVE
            );


    default TypeDescriptor parse(Column column) {
        TypeFamily family = TypeFamilyRegistry.of(column.getTypeFamily());

        String dialectType = column.getDialectType();
        TypeEncoding typeEncoding = TypeEncodingRegistry.ofNullable(column.getTypeEncoding());

        // Invariant: an encoding binds to exactly one family, which must be the
        // column's declared family (see TypeEncoding#family).
        if (typeEncoding != null && !typeEncoding.family().equals(family)) {
            throw new IllegalArgumentException("column '" + column.getName() + "': encoding "
                    + typeEncoding.name() + " is a " + typeEncoding.family().name()
                    + " but the column declares family " + family.name());
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
                case BLOB      -> parseBlob(dialectType, typeEncoding);
                case BOOLEAN   -> parseBoolean(dialectType, typeEncoding);
                case DATE      -> parseDate(dialectType, typeEncoding);
                case DECIMAL   -> parseDecimal(dialectType, typeEncoding, params);
                case FLOAT     -> parseFloat(dialectType, typeName, typeEncoding);
                case INTEGER   -> parseInteger(dialectType, typeName, typeEncoding, params);
                case TIME      -> parseTime(dialectType, typeName, typeEncoding, params);
                case INTERVAL  -> parseInterval(dialectType, typeName, typeEncoding);
                case TIMESTAMP -> parseTimestamp(dialectType, typeName, typeEncoding, params);
                case TEXT      -> parseText(dialectType, typeName, typeEncoding, params);
                case JSON      -> parseJson(dialectType, typeName, typeEncoding);
                case UUID      -> parseUuid(dialectType, typeName, typeEncoding);
            };
        }
        return parseExtended(column, family, dialectType, typeName, typeEncoding, params);
    }

    default TypeDescriptor parseExtended(Column column, TypeFamily family,
            String dialectType, String typeName, TypeEncoding typeEncoding,
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

    default TypeDescriptor parseDecimal(String dialectType, TypeEncoding typeEncoding, List<Integer> params) {

        return numericType(CoreTypeFamily.DECIMAL, dialectType, typeEncoding, params);
    }

    private static TypeDescriptor numericType(TypeFamily family, String dialectType, TypeEncoding typeEncoding, List<Integer> params) {
        if (params.isEmpty()) {
            return new TypeDescriptor(dialectType, typeEncoding, family);
        } else if  (params.size() == 1) {
            return new TypeDescriptor(dialectType, typeEncoding, family, params.get(0), -1);
        } else if  (params.size() == 2) {
            return new TypeDescriptor(dialectType, typeEncoding, family, params.get(0), params.get(1));
        } else {
            throw new IllegalArgumentException("'" + dialectType + "' carries " + params.size()
                    + " size parameters; a numeric type takes at most two (precision, scale)");
        }
    }

    /** Single optional size parameter (char length / temporal fractional-seconds precision) -> precision field. */
    private static TypeDescriptor sized(TypeFamily family, String dialectType, TypeEncoding typeEncoding, List<Integer> params) {
        return params.isEmpty()
                ? new TypeDescriptor(dialectType, typeEncoding, family)
                : new TypeDescriptor(dialectType, typeEncoding, family, params.get(0), -1);
    }

    /**
     * The approximate-numeric spellings. All of them are one family — how wide the engine's float is
     * shows in the value, not in the type KQL reasons about.
     *
     * <p>{@code DOUBLE PRECISION} and {@code BINARY_DOUBLE} were missing, which is only visible once
     * a column actually declares family FLOAT: until then no tested schema used it, and PostgreSQL's
     * and Oracle's own spellings for their 8-byte float were unparseable. The failure was a bare
     * {@code IllegalArgumentException} with no message — it named neither the type nor the column,
     * and surfaced as an initializationError with nothing to act on.
     */
    default TypeDescriptor parseFloat(String dialectType, String type, TypeEncoding typeEncoding) {

        for (String known : List.of(TypeNames.TYPE_FLOAT, TypeNames.TYPE_REAL, TypeNames.TYPE_DOUBLE,
                TypeNames.TYPE_DOUBLE_PRECISION, TypeNames.TYPE_BINARY_DOUBLE,
                TypeNames.TYPE_BINARY_FLOAT)) {
            if (type.equalsIgnoreCase(known)) {
                return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.FLOAT);
            }
        }
        throw new IllegalArgumentException("'" + dialectType + "' is declared as an approximate "
                + "numeric, but '" + type + "' is not a float type this parser knows");
    }

    default TypeDescriptor parseInteger(String dialectType, String type, TypeEncoding typeEncoding, List<Integer> params) {

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
        }  else {
            throw new IllegalArgumentException(type);
        }
    }

    default TypeDescriptor parseTime(String dialectType, String type, TypeEncoding typeEncoding, List<Integer> params) {
        return sized(CoreTypeFamily.TIME, dialectType, typeEncoding, params);
    }

    default TypeDescriptor parseInterval(String dialectType, String type, TypeEncoding typeEncoding) {

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
                return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.INTERVAL, precision, scale);
            } else if (startUnit.equalsIgnoreCase("DAY") && endUnit.equalsIgnoreCase("SECOND")) {
                return new TypeDescriptor(dialectType, typeEncoding, CoreTypeFamily.INTERVAL, precision, scale);
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

    default TypeDescriptor parseTimestamp(String dialectType, String type, TypeEncoding typeEncoding, List<Integer> params) {

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

    default TypeDescriptor parseText(String dialectType, String string, TypeEncoding typeEncoding, List<Integer> params) {
        return sized(CoreTypeFamily.TEXT, dialectType, typeEncoding, params);
    }
}
