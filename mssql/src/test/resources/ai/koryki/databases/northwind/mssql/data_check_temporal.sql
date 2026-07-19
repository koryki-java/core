-- Same fixture data as the duckdb data_check_temporal.sql (temporal CSV goldens are shared
-- across all dialects), restricted to the columns SQL Server models (no native interval or
-- boolean columns; see the mssql temporal db.json). date/datetime2/datetimeoffset string
-- literals in 'YYYY-MM-DD …' form are language-neutral in T-SQL.
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

    date_epoch_day,
    bool_from_int,
    uuid_binary,
    uuid_string,
    timestamp_zoned,
    timestamp_unix_epoche,
    timestamp_java_epoche,
    ts_diff_base,
    ts_diff_intraday,
    ts_diff_multiday,
    money_scaled,

    interval_seconds,
    interval_millis
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
   19825,                                          -- DATE_FROM_EPOCH_DAY (days to 2024-04-12)
   1,                                              -- BOOLEAN_FROM_INTEGER
   0x550E8400E29B41D4A716446655440000,             -- UUID_FROM_BINARY
   '550e8400-e29b-41d4-a716-446655440000',         -- UUID_FROM_STRING
   '2024-04-12 12:14:40 +00:00',                   -- INSTANT
   1712924080,                                     -- EPOCH:SECONDS  (Unix, 2024-04-12 12:14:40Z)
   1712924080000,                                  -- EPOCH:MILLIS   (Java, 2024-04-12 12:14:40Z)
   '2024-06-01 09:00:00',                          -- ts_diff_base
   '2024-06-01 12:20:30',                          -- ts_diff_intraday (base + 03:20:30)
   '2024-06-03 11:00:00',                          -- ts_diff_multiday (base + 50:00:00)
   1299,                                           -- SCALED:2 (12.99)

   7384,                                           -- interval_seconds  (INTERVAL:SECONDS, 2h3m4s)
   7384000                                         -- interval_millis   (INTERVAL:MILLIS)
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

   20453,                                          -- DATE_FROM_EPOCH_DAY (2025-12-31)
   0,                                              -- BOOLEAN_FROM_INTEGER
   0x6BA7B8109DAD11D180B400C04FD430C8,             -- UUID_FROM_BINARY
   '6ba7b810-9dad-11d1-80b4-00c04fd430c8',         -- UUID_FROM_STRING
   '2025-12-31 23:59:59 +00:00',                   -- INSTANT
   1767225599,                                     -- EPOCH:SECONDS (2025-12-31 23:59:59Z)
   1767225599000,                                  -- EPOCH:MILLIS
   NULL,                                           -- ts_diff_base     (only nr=1 drives the fixture)
   NULL,                                           -- ts_diff_intraday
   NULL,                                           -- ts_diff_multiday
   9999,                                           -- SCALED:2 (99.99)

   600,                                            -- interval_seconds  (10min, below the 1h2m3s filter)
   600000                                          -- interval_millis
);

-- DST fall-back, America/New_York 2024-11-03: 01:30 local occurs twice (EDT then EST) — same
-- ts_new_york wall-clock, two instants one hour apart. nr 5 has a NULL instant.
INSERT INTO check_temporal (nr, ts_new_york, date_new_york, timestamp_zoned) VALUES
  (3, '2024-11-03 01:30:00', '2024-11-03', '2024-11-03 05:30:00 +00:00'),
  (4, '2024-11-03 01:30:00', '2024-11-03', '2024-11-03 06:30:00 +00:00');
INSERT INTO check_temporal (nr) VALUES (5);
