--
-- Self-contained (DROP + CREATE + data): oracle tables.sql does not define check_type
-- and drop.sql does not drop it. Columns, order and Oracle types follow the typecheck db.json
-- (ai/koryki/oracle/databases/typecheck/db.json); the row mirrors the DuckDB blueprint.
-- Oracle specifics: no BIGINT -> NUMBER(19); DOUBLE -> BINARY_DOUBLE; BOOLEAN -> NUMBER(1);
-- no native TIME -> type_time is a DATE carrying the time of day.

DROP TABLE IF EXISTS check_type;

CREATE TABLE check_type (
    nr              SMALLINT NOT NULL PRIMARY KEY,
    type_smallint   SMALLINT,
    type_integer    INTEGER,
    type_bigint     NUMBER(19),      -- db.json BIGINT -> NUMBER(19)
    type_decimal    DECIMAL(18,4),
    type_double     BINARY_DOUBLE,
    type_boolean    NUMBER(1),
    type_date       DATE,
    type_time       DATE,            -- Oracle has no TIME type
    type_timestamp  TIMESTAMP
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
    1,
    DATE '2026-05-17',
    TO_DATE('1970-01-01 14:30:45', 'YYYY-MM-DD HH24:MI:SS'),
    TIMESTAMP '2026-05-17 14:30:45'
);
