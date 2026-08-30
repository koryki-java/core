--
-- Self-contained (DROP + CREATE + data): postgres tables.sql does not define check_type
-- and drop.sql does not drop it. Columns, order and types follow the typecheck db.json
-- (ai/koryki/postgresql/databases/typecheck/db.json); the row mirrors the DuckDB blueprint.
-- PostgreSQL has native BIGINT / DOUBLE PRECISION / BOOLEAN / TIME.

DROP TABLE IF EXISTS check_type;

CREATE TABLE check_type (
    nr              SMALLINT NOT NULL PRIMARY KEY,
    type_smallint   SMALLINT,
    type_integer    INTEGER,
    type_bigint     BIGINT,
    type_decimal    DECIMAL(18,4),
    type_double     DOUBLE PRECISION,
    type_float      REAL,
    type_boolean    BOOLEAN,
    bool_from_int   SMALLINT,      -- BOOLEAN_FROM_INTEGER (0/1)
    bool_from_text  CHAR(1),       -- BOOLEAN_FROM_TEXT ('Y'/'N')
    type_date       DATE,
    type_time       TIME,
    type_timestamp  TIMESTAMP
);

INSERT INTO check_type (
    nr, type_smallint, type_integer, type_bigint, type_decimal, type_double, type_float,
    type_boolean, bool_from_int, bool_from_text, type_date, type_time, type_timestamp
) VALUES (
    1,
    32000,
    2147483647,
    9223372036854775807,
    12345.6789,
    1.618033988749,
    1.618033988749,
    TRUE,
    1,
    'Y',
    DATE '2026-05-17',
    TIME '14:30:45',
    TIMESTAMP '2026-05-17 14:30:45'
);
