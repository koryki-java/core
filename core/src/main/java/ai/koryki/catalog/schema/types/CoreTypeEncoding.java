package ai.koryki.catalog.schema.types;

public enum CoreTypeEncoding implements TypeEncoding {
    TIME_SECONDS_FROM_MIDNIGHT(CoreTypeFamily.TIME), // TIME as INTEGER seconds since midnight
    TIME_FROM_DATE(CoreTypeFamily.TIME),             // TIME truncated from a DATE column
    TIME_FROM_INTEGER(CoreTypeFamily.TIME),          // TIME as HHMMSS integer: 12:30:50 -> 123050
    TIME_FROM_TIMESTAMP(CoreTypeFamily.TIME),        // TIME truncated from a TIMESTAMP column
    TIME_FROM_STRING(CoreTypeFamily.TIME),           // TIME as text, requires Format

    DATE_FROM_EPOCH_DAY(CoreTypeFamily.DATE),        // DATE as INTEGER days since 1970-01-01
    BOOLEAN_FROM_INTEGER(CoreTypeFamily.BOOLEAN),    // BOOLEAN as 0/1 INTEGER (MySQL TINYINT(1), Oracle NUMBER(1), SQLite)
    BOOLEAN_FROM_TEXT(CoreTypeFamily.BOOLEAN),       // BOOLEAN as Y/N
    UUID_FROM_BINARY(CoreTypeFamily.UUID),           // UUID as 16-byte binary (Oracle RAW(16), Postgres bytea, T-SQL uniqueidentifier)
    UUID_FROM_STRING(CoreTypeFamily.UUID),           // UUID as CHAR(36) text

    INTERVAL_FROM_STRING(CoreTypeFamily.INTERVAL),   // INTERVAL stored as a clock-time/ISO-8601 string
                                                     // ("01:00:00" -> 1h; "P1Y2M" -> 1y2mo, P3Y6M4DT12H30M17S)
                                                     // Leading P is optional
    CALENDAR_DISTANCE(CoreTypeFamily.INTERVAL),      // calendar_distance() wire form "startEpoch;endEpoch"
                                                     // (two epoch-seconds); decoded to a calendar Interval
                                                     // via java.time Period.between (the reference semantic)
    INTERVAL_INTERVAL(CoreTypeFamily.INTERVAL),     // INTERVAL stored in DUCKDB
    INTERVAL_YEAR_MONTH(CoreTypeFamily.INTERVAL),    // INTERVAL stored in ORACLE YEAR TO MONTH
    INTERVAL_DAY_SECOND(CoreTypeFamily.INTERVAL),   // INTERVAL stored  in ORACLE DAY TO SECOND

    TEXT_FROM_BOOLEAN(CoreTypeFamily.TEXT),          // output marker: a boolean rendered to text ("true"/"false"), normalized by Format

    /**
     * Storage declaration "instant" of docs/TEMPORAL.md: the column stores a
     * point in time (e.g. timestamptz, MariaDB/MySQL TIMESTAMP); reads are
     * normalized to the model zone at the boundary.
     */
    INSTANT(CoreTypeFamily.TIMESTAMP);

    private final TypeFamily family;

    CoreTypeEncoding(TypeFamily family) {
        this.family = family;
    }

    @Override
    public TypeFamily family() {
        return family;
    }
}
