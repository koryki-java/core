package ai.koryki.catalog.schema.types;

/**
 * Physical type-name vocabulary: the dialect type-name strings (the value of a
 * {@link TypeDescriptor#getPhysicalTypeName()}). Extracted from
 * {@link TypeDescriptor} because these names are a separate concern from the
 * descriptor value type — they are the vocabulary consumed by
 * {@link TypeDescriptorParser} and the dialects.
 */
public final class TypeNames {

    private TypeNames() {
    }

    public static final String TYPE_BLOB = "BLOB";
    public static final String TYPE_BOOLEAN = "BOOLEAN";
    public static final String TYPE_FLOAT = "FLOAT";
    public static final String TYPE_REAL = "REAL";
    public static final String TYPE_DOUBLE = "DOUBLE";

    public static final String TYPE_TINYINT ="TINYINT";
    public static final String TYPE_SMALLINT ="SMALLINT";
    public static final String TYPE_INTEGER ="INTEGER";
    public static final String TYPE_INT ="INT";
    public static final String TYPE_BIGINT ="BIGINT";
    public static final String TYPE_HUGEINT ="HUGEINT";
    public static final String TYPE_UTINYINT ="UTINYINT";
    public static final String TYPE_USMALLINT ="USMALLINT";
    public static final String TYPE_UINTEGER ="UINTEGER";
    public static final String TYPE_UBIGINT ="UBIGINT";

    public static final String TYPE_DECIMAL ="DECIMAL";
    public static final String TYPE_NUMBER ="NUMBER";
    public static final String TYPE_NUMERIC ="NUMERIC";

    public static final String TYPE_DATE = "DATE";

    public static final String TYPE_TIME = "TIME";
    public static final String TYPE_TIME_SECONDS_FROM_MIDNIGHT = "TIME_SECONDS_FROM_MIDNIGHT";
    public static final String TYPE_TIME_FROM_DATE = "TIME_FROM_DATE";
    public static final String TYPE_TIME_FROM_TIMESTAMP = "TIME_FROM_TIMESTAMP";

    public static final String TYPE_TIMESTAMP = "TIMESTAMP";
    public static final String TYPE_TIMESTAMP_S = "TIMESTAMP_S";
    public static final String TYPE_TIMESTAMP_MS = "TIMESTAMP_MS";
    public static final String TYPE_TIMESTAMP_NS = "TIMESTAMP_NS";
    public static final String TYPE_TIMESTAMPTZ = "TIMESTAMPTZ";
    /** Snowflake's naive (wall-clock) timestamp — plain TIMESTAMP family. */
    public static final String TYPE_TIMESTAMP_NTZ = "TIMESTAMP_NTZ";
    /** MariaDB/MySQL naive timestamp (their TIMESTAMP type is session-zone-converted — see INSTANT encoding). */
    public static final String TYPE_DATETIME = "DATETIME";
    /** T-SQL naive timestamp. */
    public static final String TYPE_DATETIME2 = "DATETIME2";
    public static final String TYPE_TIMESTAM_WITH_TIMEZONE = "TIMESTAMP WITH TIME ZONE";

    public static final String TYPE_TIMESTAMP_WITH_LOCAL_TIME_ZONE = "TIMESTAMP WITH LOCAL TIME ZONE";
    public static final String TYPE_INTERVAL = "INTERVAL";
    public static final String TYPE_INTERVAL_SECONDS = "INTERVAL_SECONDS";
    public static final String TYPE_INTERVAL_YEAR_MONTH = "INTERVAL_YEAR_MONTH";
    public static final String TYPE_INTERVAL_DAY_SECOND = "INTERVAL_DAY_SECOND";
    public static final String TYPE_TEXT = "TEXT";
    public static final String TYPE_VARCHAR = "VARCHAR";

    public static final String TYPE_UUID = "UUID";
    public static final String TYPE_JSON = "JSON";
    public static final String TYPE_NULL = "NULL";
}
