--
-- Self-contained (DROP + CREATE + data): postgres tables.sql does not define check_temporal
-- and drop.sql does not drop it. Columns, order and types follow the temporal db.json
-- (ai/koryki/postgresql/databases/temporal/db.json); the fixture rows mirror the DuckDB blueprint
-- (temporal goldens are shared across dialects). PostgreSQL has native TIME / BOOLEAN / INTERVAL /
-- TIMESTAMPTZ / BYTEA, so the mapping stays close to DuckDB; only nr is NOT NULL so the partial
-- rows 3-5 load. (Note: no time_from_date column in the postgres model.)

DROP TABLE IF EXISTS check_temporal;

CREATE TABLE check_temporal (
    nr                    SMALLINT NOT NULL PRIMARY KEY,
    time_date             TIME,                          -- native TIME
    time_sec_from_midnight DECIMAL(5,0),                 -- TIME_SECONDS_FROM_MIDNIGHT
    ts_new_york           TIMESTAMP,                     -- TIMESTAMP_WALLCLOCK:America/New_York
    date_new_york         DATE,                          -- DATE_WALLCLOCK:America/New_York
    date_date             DATE,
    time_from_integer     INTEGER,                       -- TIME_FROM_INTEGER (HHMMSS)
    time_from_string      VARCHAR(8),                    -- TIME_FROM_STRING ('HH:MM:SS')
    time_from_timestamp   TIMESTAMP,                     -- TIME_FROM_TIMESTAMP
    date_epoch_day        INTEGER,                       -- DATE_FROM_EPOCH_DAY
    bool_from_int         SMALLINT,                      -- BOOLEAN_FROM_INTEGER
    uuid_binary           BYTEA,                         -- UUID_FROM_BINARY
    uuid_string           VARCHAR(36),                   -- UUID_FROM_STRING
    timestamp_zoned       TIMESTAMPTZ,                   -- INSTANT
    timestamp_unix_epoche BIGINT,                        -- EPOCH:SECONDS
    timestamp_java_epoche BIGINT,                        -- EPOCH:MILLIS
    timestamp_timestamp   TIMESTAMP,                     -- wall-clock UTC
    ts_diff_base          TIMESTAMP,                     -- elapsed-span anchor (2024-06-01 09:00:00)
    ts_diff_intraday      TIMESTAMP,                     --   anchor + 03:20:30 (< 1 day)
    ts_diff_multiday      TIMESTAMP,                     --   anchor + 50:00:00 (> 1 day)
    money_scaled          BIGINT,                        -- SCALED:2 (minor units, 1299 -> 12.99)
    interval_seconds      INTEGER,                       -- INTERVAL:SECONDS
    interval_millis       BIGINT,                        -- INTERVAL:MILLIS
    interval_year_month   INTERVAL YEAR TO MONTH,        -- calendar amount (Period)
    interval_day_second   INTERVAL DAY TO SECOND         -- exact-time amount (Duration)
);

INSERT INTO check_temporal (
    nr, time_date, time_sec_from_midnight, ts_new_york, date_new_york, date_date,
    time_from_integer, time_from_string, time_from_timestamp, date_epoch_day, bool_from_int,
    uuid_binary, uuid_string, timestamp_zoned, timestamp_unix_epoche, timestamp_java_epoche,
    timestamp_timestamp, ts_diff_base, ts_diff_intraday, ts_diff_multiday, money_scaled,
    interval_seconds, interval_millis, interval_year_month, interval_day_second
) VALUES
(
    1,
    TIME '12:30:50', 48640,
    TIMESTAMP '2024-03-15 14:30:50',                            -- wall-clock America/New_York (EDT)
    DATE '2024-03-15', DATE '2024-04-12',
    123050, '12:30:50', TIMESTAMP '1970-01-01 12:30:50',
    19825,                                                      -- DATE_FROM_EPOCH_DAY (2024-04-12)
    1,                                                          -- BOOLEAN_FROM_INTEGER
    '\x550e8400e29b41d4a716446655440000',                       -- UUID_FROM_BINARY
    '550e8400-e29b-41d4-a716-446655440000',                     -- UUID_FROM_STRING
    TIMESTAMPTZ '2024-04-12 12:14:40+00',                       -- INSTANT
    1712924080, 1712924080000,                                  -- EPOCH:SECONDS / MILLIS
    TIMESTAMP '2024-04-12 12:14:40',
    TIMESTAMP '2024-06-01 09:00:00',                            -- ts_diff_base
    TIMESTAMP '2024-06-01 12:20:30',                            -- ts_diff_intraday (base + 03:20:30)
    TIMESTAMP '2024-06-03 11:00:00',                            -- ts_diff_multiday (base + 50:00:00)
    1299,                                                       -- SCALED:2 (12.99)
    7384, 7384000,                                              -- INTERVAL:SECONDS / MILLIS (2h3m4s)
    INTERVAL '2 years 3 months',
    INTERVAL '4 days 5 hours 6 minutes 7 seconds'
),
(
    2,
    TIME '23:59:59', 86399,
    TIMESTAMP '2025-12-31 23:59:59',
    DATE '2025-12-31', DATE '2025-12-31',
    235959, '23:59:59', TIMESTAMP '1970-01-01 23:59:59',
    20453,                                                      -- DATE_FROM_EPOCH_DAY (2025-12-31)
    0,
    '\x6ba7b8109dad11d180b400c04fd430c8',
    '6ba7b810-9dad-11d1-80b4-00c04fd430c8',
    TIMESTAMPTZ '2025-12-31 23:59:59+00',
    1767225599, 1767225599000,
    TIMESTAMP '2025-12-31 23:59:59',
    NULL, NULL, NULL,                                           -- ts_diff_* (only nr=1 drives the fixture)
    9999,                                                       -- SCALED:2 (99.99)
    600, 600000,                                                -- INTERVAL:SECONDS / MILLIS (10min)
    INTERVAL '1 year 6 months',
    INTERVAL '10 days 12 hours 30 minutes 45 seconds'
);

-- DST fall-back, America/New_York 2024-11-03: 01:30 local occurs twice (EDT then EST) — same
-- ts_new_york wall-clock, two instants one hour apart. nr 5 has a NULL instant.
INSERT INTO check_temporal (nr, ts_new_york, date_new_york, timestamp_zoned) VALUES
  (3, TIMESTAMP '2024-11-03 01:30:00', DATE '2024-11-03', TIMESTAMPTZ '2024-11-03 05:30:00+00'),
  (4, TIMESTAMP '2024-11-03 01:30:00', DATE '2024-11-03', TIMESTAMPTZ '2024-11-03 06:30:00+00');
INSERT INTO check_temporal (nr) VALUES (5);
