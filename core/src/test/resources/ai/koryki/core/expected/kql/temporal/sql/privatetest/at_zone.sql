-- at_zone(value, 'Zone'): read a model-zone wall-clock value and return its wall-clock in the named zone
-- (docs/TEMPORAL.md "Time zones") -- the value to feed date(...) for "the day in that zone". The algebra
-- stays zone-free; this is the explicit escape. nr 1 only (2024-06-01 09:00 UTC, unambiguous).
-- SQLite has no time-zone database, so zone crossing is impossible there and is permanently skipped.
SELECT
  c.ts_diff_base AS base
, ((c.ts_diff_base AT TIME ZONE 'UTC') AT TIME ZONE 'America/New_York') AS in_new_york
FROM
 check_temporal c
WHERE
  c.nr = 1