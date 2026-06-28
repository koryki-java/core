INSERT INTO check_type (
    nr,
    type_smallint,
    type_integer,
    type_bigint,
    type_decimal,
    type_double,
    type_boolean,
    type_date,
    type_time,
    type_timestamp
)
VALUES (
    1,
    32000,
    2147483647,
    9223372036854775807,
    12345.6789,
    1.618033988749,
    TRUE,
    DATE '2026-05-17',
    TIME '14:30:45',
    TIMESTAMP '2026-05-17 14:30:45'
);
