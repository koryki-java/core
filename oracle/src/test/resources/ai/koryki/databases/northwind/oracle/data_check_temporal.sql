--
-- Self-contained (DROP + CREATE + data): oracle tables.sql does not define check_temporal
-- and drop.sql does not drop it. Columns, order and Oracle types follow the temporal db.json
-- (ai/koryki/oracle/databases/temporal/db.json); the fixture rows mirror the DuckDB blueprint
-- (temporal goldens are shared across dialects). Oracle specifics:
--   * no native TIME  -> TIME_FROM_DATE columns are DATE (time carried in the time part);
--                        seconds-from-midnight / HHMMSS are DECIMAL.
--   * no BIGINT       -> interval_millis is NUMBER(19).
--   * INSTANT         -> TIMESTAMP WITH TIME ZONE.
--   * calendar / exact-time intervals use native INTERVAL YEAR TO MONTH / DAY TO SECOND.
--   * only nr is NOT NULL so the partial rows 3-5 (from the DuckDB fixture) load.
--   * Oracle has no multi-row VALUES: one INSERT per row.

DROP TABLE IF EXISTS check_temporal;

CREATE TABLE check_temporal (
    nr                    DECIMAL(3,0) NOT NULL PRIMARY KEY,
    time_date             DATE,                          -- TIME_FROM_DATE
    time_sec_from_midnight DECIMAL(5,0),                 -- TIME_SECONDS_FROM_MIDNIGHT
    ts_new_york           TIMESTAMP,                     -- TIMESTAMP_WALLCLOCK:America/New_York
    date_new_york         DATE,                          -- DATE_WALLCLOCK:America/New_York
    date_date             DATE,
    time_from_integer     DECIMAL(6,0),                  -- TIME_FROM_INTEGER (HHMMSS)
    time_from_string      VARCHAR2(8),                   -- TIME_FROM_STRING ('HH:MM:SS')
    time_from_timestamp   TIMESTAMP,                     -- TIME_FROM_TIMESTAMP
    time_from_date        DATE,                          -- TIME_FROM_DATE
    date_epoch_day        DECIMAL(7,0),                  -- DATE_FROM_EPOCH_DAY
    bool_from_int         DECIMAL(1,0),                  -- BOOLEAN_FROM_INTEGER
    uuid_binary           RAW(16),                       -- UUID_FROM_BINARY
    uuid_string           VARCHAR2(36),                  -- UUID_FROM_STRING
    timestamp_zoned       TIMESTAMP WITH TIME ZONE,      -- INSTANT
    timestamp_unix_epoche DECIMAL(19,0),                 -- EPOCH:SECONDS
    timestamp_java_epoche DECIMAL(19,0),                 -- EPOCH:MILLIS
    timestamp_timestamp   TIMESTAMP,                     -- wall-clock UTC
    ts_diff_base          TIMESTAMP,                     -- elapsed-span anchor (2024-06-01 09:00:00)
    ts_diff_intraday      TIMESTAMP,                     --   anchor + 03:20:30 (< 1 day)
    ts_diff_multiday      TIMESTAMP,                     --   anchor + 50:00:00 (> 1 day)
    money_scaled          DECIMAL(19,0),                 -- SCALED:2 (minor units, 1299 -> 12.99)
    interval_seconds      INTEGER,                       -- INTERVAL:SECONDS
    interval_millis       NUMBER(19),                    -- INTERVAL:MILLIS (db.json BIGINT -> NUMBER(19))
    interval_year_month   INTERVAL YEAR TO MONTH,        -- calendar amount (Period)
    interval_day_second   INTERVAL DAY TO SECOND         -- exact-time amount (Duration)
);

INSERT INTO check_temporal (
    nr, time_date, time_sec_from_midnight, ts_new_york, date_new_york, date_date,
    time_from_integer, time_from_string, time_from_timestamp, time_from_date,
    date_epoch_day, bool_from_int, uuid_binary, uuid_string, timestamp_zoned,
    timestamp_unix_epoche, timestamp_java_epoche, timestamp_timestamp,
    ts_diff_base, ts_diff_intraday, ts_diff_multiday, money_scaled,
    interval_seconds, interval_millis, interval_year_month, interval_day_second
) VALUES (
    1,
    TO_DATE('1970-01-01 12:30:50', 'YYYY-MM-DD HH24:MI:SS'),   -- time 12:30:50
    48640,
    TIMESTAMP '2024-03-15 14:30:50',                           -- wall-clock America/New_York (EDT)
    DATE '2024-03-15',
    DATE '2024-04-12',
    123050,                                                    -- same 12:30:50 as HHMMSS
    '12:30:50',
    TIMESTAMP '1970-01-01 12:30:50',
    DATE '2024-04-12',                                         -- TIME_FROM_DATE (time part 00:00:00)
    19825,                                                     -- DATE_FROM_EPOCH_DAY (2024-04-12)
    1,                                                         -- BOOLEAN_FROM_INTEGER
    HEXTORAW('550E8400E29B41D4A716446655440000'),              -- UUID_FROM_BINARY
    '550e8400-e29b-41d4-a716-446655440000',                    -- UUID_FROM_STRING
    TIMESTAMP '2024-04-12 12:14:40 +00:00',                    -- INSTANT
    1712924080,                                                -- EPOCH:SECONDS  (2024-04-12 12:14:40Z)
    1712924080000,                                             -- EPOCH:MILLIS
    TIMESTAMP '2024-04-12 12:14:40',                           -- wall-clock UTC
    TIMESTAMP '2024-06-01 09:00:00',                           -- ts_diff_base
    TIMESTAMP '2024-06-01 12:20:30',                           -- ts_diff_intraday (base + 03:20:30)
    TIMESTAMP '2024-06-03 11:00:00',                           -- ts_diff_multiday (base + 50:00:00)
    1299,                                                      -- SCALED:2 (12.99)
    7384,                                                      -- INTERVAL:SECONDS (2h3m4s)
    7384000,                                                   -- INTERVAL:MILLIS
    INTERVAL '2-3' YEAR TO MONTH,                              -- 2 years 3 months
    INTERVAL '4 5:6:7' DAY TO SECOND                           -- 4 days 5 hours 6 minutes 7 seconds
);

INSERT INTO check_temporal (
    nr, time_date, time_sec_from_midnight, ts_new_york, date_new_york, date_date,
    time_from_integer, time_from_string, time_from_timestamp, time_from_date,
    date_epoch_day, bool_from_int, uuid_binary, uuid_string, timestamp_zoned,
    timestamp_unix_epoche, timestamp_java_epoche, timestamp_timestamp,
    ts_diff_base, ts_diff_intraday, ts_diff_multiday, money_scaled,
    interval_seconds, interval_millis, interval_year_month, interval_day_second
) VALUES (
    2,
    TO_DATE('1970-01-01 23:59:59', 'YYYY-MM-DD HH24:MI:SS'),
    86399,
    TIMESTAMP '2025-12-31 23:59:59',
    DATE '2025-12-31',
    DATE '2025-12-31',
    235959,
    '23:59:59',
    TIMESTAMP '1970-01-01 23:59:59',
    DATE '2025-12-31',
    20453,                                                     -- DATE_FROM_EPOCH_DAY (2025-12-31)
    0,                                                         -- BOOLEAN_FROM_INTEGER
    HEXTORAW('6BA7B8109DAD11D180B400C04FD430C8'),
    '6ba7b810-9dad-11d1-80b4-00c04fd430c8',
    TIMESTAMP '2025-12-31 23:59:59 +00:00',
    1767225599,                                                -- EPOCH:SECONDS
    1767225599000,                                             -- EPOCH:MILLIS
    TIMESTAMP '2025-12-31 23:59:59',
    NULL,                                                      -- ts_diff_base     (only nr=1 drives the fixture)
    NULL,                                                      -- ts_diff_intraday
    NULL,                                                      -- ts_diff_multiday
    9999,                                                      -- SCALED:2 (99.99)
    600,                                                       -- INTERVAL:SECONDS (10min)
    600000,                                                    -- INTERVAL:MILLIS
    INTERVAL '1-6' YEAR TO MONTH,                              -- 1 year 6 months
    INTERVAL '10 12:30:45' DAY TO SECOND                       -- 10 days 12 hours 30 minutes 45 seconds
);

-- DST fall-back, America/New_York 2024-11-03: 01:30 local occurs twice (EDT then EST) — same
-- ts_new_york wall-clock, two instants one hour apart. nr 5 has a NULL instant.
INSERT INTO check_temporal (nr, ts_new_york, date_new_york, timestamp_zoned)
    VALUES (3, TIMESTAMP '2024-11-03 01:30:00', DATE '2024-11-03', TIMESTAMP '2024-11-03 05:30:00 +00:00');
INSERT INTO check_temporal (nr, ts_new_york, date_new_york, timestamp_zoned)
    VALUES (4, TIMESTAMP '2024-11-03 01:30:00', DATE '2024-11-03', TIMESTAMP '2024-11-03 06:30:00 +00:00');
INSERT INTO check_temporal (nr) VALUES (5);
