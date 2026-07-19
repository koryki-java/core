--
-- Self-contained (DROP + CREATE + data): snowflake tables.sql does not define check_type
-- and drop.sql does not drop it. Columns, order and types follow the typecheck db.json
-- (ai/koryki/snowflake/databases/typecheck/db.json); the row mirrors the DuckDB blueprint.
-- Snowflake: DOUBLE, BOOLEAN, TIMESTAMP_NTZ are native. Temporal/binary values are plain string
-- literals — Snowflake implicitly converts them to the target column type on INSERT
-- (BINARY_INPUT_FORMAT defaults to HEX), avoiding functions in the VALUES clause.

DROP TABLE IF EXISTS check_type;

CREATE TABLE check_type (
    nr              SMALLINT NOT NULL PRIMARY KEY,
    type_smallint   SMALLINT,
    type_integer    INTEGER,
    type_bigint     BIGINT,
    type_decimal    DECIMAL(18,4),
    type_double     DOUBLE,
    type_boolean    BOOLEAN,
    type_date       DATE,
    type_time       TIME,
    type_timestamp  TIMESTAMP_NTZ
);

INSERT INTO check_type (
    nr, type_smallint, type_integer, type_bigint, type_decimal, type_double,
    type_boolean, type_date, type_time, type_timestamp
) VALUES (
    1,
    32000,
    2147483647,
    9223372036854775807,
    12345.6789,
    1.618033988749,
    TRUE,
    '2026-05-17',
    '14:30:45',
    '2026-05-17 14:30:45'
);
