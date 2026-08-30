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

   DATE '2024-04-12',
   TIMESTAMP '2024-04-12 12:14:40',

           -- integer
   TIME '12:30:50',
   48640,

   -- wall-clock America/New_York (EDT season)
   TIMESTAMP '2024-03-15 14:30:50',
   DATE '2024-03-15',

   -- the same time of day, 12:30:50, in each encoding
   123050,
   '12:30:50',
   TIMESTAMP '1970-01-01 12:30:50',

   -- one column per remaining encoding
   DATE '2024-04-12',                              -- TIME_FROM_DATE (time part = 00:00:00 in DuckDB)
   19825,                                          -- DATE_FROM_EPOCH_DAY (days to 2024-04-12)
   1,                                              -- BOOLEAN_FROM_INTEGER
   TRUE,                                           -- bool_native
   '\x55\x0E\x84\x00\xE2\x9B\x41\xD4\xA7\x16\x44\x66\x55\x44\x00\x00'::BLOB,  -- UUID_FROM_BINARY
   '550e8400-e29b-41d4-a716-446655440000',         -- UUID_FROM_STRING
   TIMESTAMPTZ '2024-04-12 12:14:40+00',           -- INSTANT
   1712924080,                                     -- EPOCH:SECONDS  (Unix, 2024-04-12 12:14:40Z)
   1712924080000,                                  -- EPOCH:MILLIS   (Java, 2024-04-12 12:14:40Z)
   TIMESTAMP '2024-06-01 09:00:00',                -- ts_diff_base
   TIMESTAMP '2024-06-01 12:20:30',                -- ts_diff_intraday (base + 03:20:30)
   TIMESTAMP '2024-06-03 11:00:00',                -- ts_diff_multiday (base + 50:00:00)
   1299,                                           -- SCALED:2 (12.99)

   INTERVAL '2 years 3 months',                    -- calendar amount (Period)
   INTERVAL '4 days 5 hours 6 minutes 7 seconds',  -- exact-time amount (Duration)
   7384,                                           -- interval_seconds  (INTERVAL:SECONDS, 2h3m4s)
   7384000,                                        -- interval_millis   (INTERVAL:MILLIS)
   'PT2H3M4S',                                     -- interval_char     (INTERVAL_CHAR, ISO-8601)
   INTERVAL '2 hours 3 minutes 4 seconds'          -- interval_interval (native INTERVAL)
),
(
   2,

   DATE '2025-12-31',
   TIMESTAMP '2025-12-31 23:59:59',

   TIME '23:59:59',
   86399,

   TIMESTAMP '2025-12-31 23:59:59',
   DATE '2025-12-31',

   235959,
   '23:59:59',
   TIMESTAMP '1970-01-01 23:59:59',

   DATE '2025-12-31',                              -- TIME_FROM_DATE
   20453,                                          -- DATE_FROM_EPOCH_DAY (2025-12-31)
   0,                                              -- BOOLEAN_FROM_INTEGER
   FALSE,                                          -- bool_native
   '\x6B\xA7\xB8\x10\x9D\xAD\x11\xD1\x80\xB4\x00\xC0\x4F\xD4\x30\xC8'::BLOB,  -- UUID_FROM_BINARY
   '6ba7b810-9dad-11d1-80b4-00c04fd430c8',         -- UUID_FROM_STRING
   TIMESTAMPTZ '2025-12-31 23:59:59+00',           -- INSTANT
   1767225599,                                     -- EPOCH:SECONDS (2025-12-31 23:59:59Z)
   1767225599000,                                  -- EPOCH:MILLIS
   NULL,                                           -- ts_diff_base     (only nr=1 drives the fixture)
   NULL,                                           -- ts_diff_intraday
   NULL,                                           -- ts_diff_multiday
   9999,                                           -- SCALED:2 (99.99)

   INTERVAL '1 year 6 months',
   INTERVAL '10 days 12 hours 30 minutes 45 seconds',
   600,                                            -- interval_seconds  (10min, below the 1h2m3s filter)
   600000,                                         -- interval_millis
   'PT10M',                                        -- interval_char
   INTERVAL '10 minutes'                           -- interval_interval
);

-- DST fall-back, America/New_York 2024-11-03: 01:30 local occurs twice (EDT then EST) — same
-- ts_new_york wall-clock, two instants one hour apart. nr 5 has a NULL instant.
INSERT INTO check_temporal (nr, ts_new_york, date_new_york, timestamp_zoned) VALUES
  (3, TIMESTAMP '2024-11-03 01:30:00', DATE '2024-11-03', TIMESTAMPTZ '2024-11-03 05:30:00+00'),
  (4, TIMESTAMP '2024-11-03 01:30:00', DATE '2024-11-03', TIMESTAMPTZ '2024-11-03 06:30:00+00');
INSERT INTO check_temporal (nr) VALUES (5);