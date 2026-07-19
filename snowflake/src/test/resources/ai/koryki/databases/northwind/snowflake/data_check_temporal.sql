--
-- Self-contained (DROP + CREATE + data): snowflake tables.sql does not define check_temporal
-- and drop.sql does not drop it. Columns, order and types follow the temporal db.json
-- (ai/koryki/snowflake/databases/temporal/db.json); the fixture rows mirror the DuckDB blueprint
-- (temporal goldens are shared across dialects). Snowflake specifics:
--   * INSTANT -> TIMESTAMP_TZ; wall-clock timestamps -> TIMESTAMP_NTZ; native TIME / BOOLEAN.
--   * no native INTERVAL: interval_seconds / interval_millis only (INTEGER / BIGINT counts);
--     no interval_year_month / interval_day_second / time_from_date columns in the snowflake model.
--   * uuid_binary is BINARY; the hex string is converted implicitly (BINARY_INPUT_FORMAT=HEX).
--   * only nr is NOT NULL so the partial rows 3-5 load. Temporal/binary values are plain string
--     literals (implicit conversion), avoiding functions in the VALUES clause.

DROP TABLE IF EXISTS check_temporal;

CREATE TABLE check_temporal (
    nr                     SMALLINT NOT NULL PRIMARY KEY,
    time_date              TIME,                     -- native TIME
    time_sec_from_midnight DECIMAL(5,0),             -- TIME_SECONDS_FROM_MIDNIGHT
    ts_new_york            TIMESTAMP_NTZ,            -- TIMESTAMP_WALLCLOCK:America/New_York
    date_new_york          DATE,                     -- DATE_WALLCLOCK:America/New_York
    date_date              DATE,
    time_from_integer      DECIMAL(6,0),             -- TIME_FROM_INTEGER (HHMMSS)
    time_from_string       VARCHAR(8),               -- TIME_FROM_STRING ('HH:MM:SS')
    time_from_timestamp    TIMESTAMP_NTZ,            -- TIME_FROM_TIMESTAMP
    date_epoch_day         DECIMAL(7,0),             -- DATE_FROM_EPOCH_DAY
    bool_from_int          DECIMAL(1,0),             -- BOOLEAN_FROM_INTEGER
    uuid_binary            BINARY,                   -- UUID_FROM_BINARY
    uuid_string            VARCHAR(36),              -- UUID_FROM_STRING
    timestamp_zoned        TIMESTAMP_TZ,            -- INSTANT
    timestamp_unix_epoche  BIGINT,                   -- EPOCH:SECONDS
    timestamp_java_epoche  BIGINT,                   -- EPOCH:MILLIS
    timestamp_timestamp    TIMESTAMP_NTZ,           -- wall-clock UTC
    ts_diff_base           TIMESTAMP_NTZ,           -- elapsed-span anchor (2024-06-01 09:00:00)
    ts_diff_intraday       TIMESTAMP_NTZ,           --   anchor + 03:20:30 (< 1 day)
    ts_diff_multiday       TIMESTAMP_NTZ,           --   anchor + 50:00:00 (> 1 day)
    money_scaled           BIGINT,                   -- SCALED:2 (minor units, 1299 -> 12.99)
    interval_seconds       INTEGER,                  -- INTERVAL:SECONDS
    interval_millis        BIGINT                    -- INTERVAL:MILLIS
);

INSERT INTO check_temporal (
    nr, time_date, time_sec_from_midnight, ts_new_york, date_new_york, date_date,
    time_from_integer, time_from_string, time_from_timestamp, date_epoch_day, bool_from_int,
    uuid_binary, uuid_string, timestamp_zoned, timestamp_unix_epoche, timestamp_java_epoche,
    timestamp_timestamp, ts_diff_base, ts_diff_intraday, ts_diff_multiday, money_scaled,
    interval_seconds, interval_millis
) VALUES
(1, '12:30:50', 48640, '2024-03-15 14:30:50', '2024-03-15', '2024-04-12',
 123050, '12:30:50', '1970-01-01 12:30:50', 19825, 1,
 '550E8400E29B41D4A716446655440000', '550e8400-e29b-41d4-a716-446655440000',
 '2024-04-12 12:14:40 +00:00', 1712924080, 1712924080000,
 '2024-04-12 12:14:40', '2024-06-01 09:00:00', '2024-06-01 12:20:30', '2024-06-03 11:00:00', 1299,
 7384, 7384000),
(2, '23:59:59', 86399, '2025-12-31 23:59:59', '2025-12-31', '2025-12-31',
 235959, '23:59:59', '1970-01-01 23:59:59', 20453, 0,
 '6BA7B8109DAD11D180B400C04FD430C8', '6ba7b810-9dad-11d1-80b4-00c04fd430c8',
 '2025-12-31 23:59:59 +00:00', 1767225599, 1767225599000,
 '2025-12-31 23:59:59', NULL, NULL, NULL, 9999,
 600, 600000);

-- DST fall-back, America/New_York 2024-11-03: 01:30 local occurs twice (EDT then EST) — same
-- ts_new_york wall-clock, two instants one hour apart. nr 5 has a NULL instant.
INSERT INTO check_temporal (nr, ts_new_york, date_new_york, timestamp_zoned) VALUES
(3, '2024-11-03 01:30:00', '2024-11-03', '2024-11-03 05:30:00 +00:00'),
(4, '2024-11-03 01:30:00', '2024-11-03', '2024-11-03 06:30:00 +00:00');
INSERT INTO check_temporal (nr) VALUES (5);
