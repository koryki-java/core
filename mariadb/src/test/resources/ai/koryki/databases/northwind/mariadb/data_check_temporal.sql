-- Translated from the DuckDB blueprint. MariaDB has no INTERVAL or TIMESTAMPTZ type:
--   * INTERVAL values are stored as ISO-8601 duration text (e.g. 'P2Y3M', 'PT2H3M4S').
--   * zone-aware instants (TIMESTAMPTZ '... +00') are stored as TIMESTAMP in UTC.
--   * the UUID binary is a hex literal (X'..') into a BLOB.
INSERT INTO check_temporal (
    nr,
    date_date,
    timestamp_timestamp,
    time_time,
    time_sec_from_midnight,
    ts_new_york,
    date_new_york,
    time_from_integer,
    time_from_string,
    time_from_timestamp,

    time_from_date,
    date_epoch_day,
    bool_from_int,
    bool_native,
    uuid_binary,
    uuid_string,
    timestamp_zoned,
    timestamp_unix_epoche,
    timestamp_java_epoche,
    ts_diff_base,
    ts_diff_intraday,
    ts_diff_multiday,
    money_scaled,

    interval_year_month,
    interval_day_second,
    interval_seconds,
    interval_millis,
    interval_char,
    interval_interval
)
VALUES (
   1,

   '2024-04-12',
   '2024-04-12 12:14:40',

   '12:30:50',
   48640,

   -- wall-clock America/New_York (EDT season)
   '2024-03-15 14:30:50',
   '2024-03-15',

   -- the same time of day, 12:30:50, in each encoding
   123050,
   '12:30:50',
   '1970-01-01 12:30:50',

   -- one column per remaining encoding
   '2024-04-12',                              -- TIME_FROM_DATE (time part = 00:00:00)
   19825,                                     -- DATE_FROM_EPOCH_DAY (days to 2024-04-12)
   1,                                         -- BOOLEAN_FROM_INTEGER
   TRUE,                                      -- bool_native
   X'550E8400E29B41D4A716446655440000',       -- UUID_FROM_BINARY
   '550e8400-e29b-41d4-a716-446655440000',    -- UUID_FROM_STRING
   '2024-04-12 12:14:40',                     -- INSTANT (UTC)
   1712924080,                                -- EPOCH:SECONDS  (Unix, 2024-04-12 12:14:40Z)
   1712924080000,                             -- EPOCH:MILLIS   (Java, 2024-04-12 12:14:40Z)
   '2024-06-01 09:00:00',                     -- ts_diff_base
   '2024-06-01 12:20:30',                     -- ts_diff_intraday (base + 03:20:30)
   '2024-06-03 11:00:00',                     -- ts_diff_multiday (base + 50:00:00)
   1299,                                      -- SCALED:2 (12.99)

   'P2Y3M',                                   -- calendar amount (Period), was INTERVAL '2 years 3 months'
   'P4DT5H6M7S',                              -- exact-time amount (Duration), was INTERVAL '4 days 5 hours 6 minutes 7 seconds'
   7384,                                      -- interval_seconds  (INTERVAL:SECONDS, 2h3m4s)
   7384000,                                   -- interval_millis   (INTERVAL:MILLIS)
   'PT2H3M4S',                                -- interval_char     (INTERVAL_CHAR, ISO-8601)
   'PT2H3M4S'                                 -- interval_interval, was INTERVAL '2 hours 3 minutes 4 seconds'
),
(
   2,

   '2025-12-31',
   '2025-12-31 23:59:59',

   '23:59:59',
   86399,

   '2025-12-31 23:59:59',
   '2025-12-31',

   235959,
   '23:59:59',
   '1970-01-01 23:59:59',

   '2025-12-31',                              -- TIME_FROM_DATE
   20453,                                     -- DATE_FROM_EPOCH_DAY (2025-12-31)
   0,                                         -- BOOLEAN_FROM_INTEGER
   FALSE,                                     -- bool_native
   X'6BA7B8109DAD11D180B400C04FD430C8',       -- UUID_FROM_BINARY
   '6ba7b810-9dad-11d1-80b4-00c04fd430c8',    -- UUID_FROM_STRING
   '2025-12-31 23:59:59',                     -- INSTANT (UTC)
   1767225599,                                -- EPOCH:SECONDS (2025-12-31 23:59:59Z)
   1767225599000,                             -- EPOCH:MILLIS
   NULL,                                      -- ts_diff_base     (only nr=1 drives the fixture)
   NULL,                                      -- ts_diff_intraday
   NULL,                                      -- ts_diff_multiday
   9999,                                      -- SCALED:2 (99.99)

   'P1Y6M',                                   -- was INTERVAL '1 year 6 months'
   'P10DT12H30M45S',                          -- was INTERVAL '10 days 12 hours 30 minutes 45 seconds'
   600,                                       -- interval_seconds  (10min, below the 1h2m3s filter)
   600000,                                    -- interval_millis
   'PT10M',                                   -- interval_char
   'PT10M'                                    -- interval_interval, was INTERVAL '10 minutes'
);

-- DST fall-back, America/New_York 2024-11-03: 01:30 local occurs twice (EDT then EST) — same
-- ts_new_york wall-clock, two instants one hour apart. nr 5 has a NULL instant.
INSERT INTO check_temporal (nr, ts_new_york, date_new_york, timestamp_zoned) VALUES
  (3, '2024-11-03 01:30:00', '2024-11-03', '2024-11-03 05:30:00'),
  (4, '2024-11-03 01:30:00', '2024-11-03', '2024-11-03 06:30:00');
INSERT INTO check_temporal (nr) VALUES (5);
