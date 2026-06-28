-- TIME stored as seconds-from-midnight: arithmetic is integer seconds, wrapped mod 86400 so that
-- crossing midnight in either direction stays a valid time-of-day.
--   overflow:  23:30 + 2h  -> 01:30
--   underflow: 00:30 - 3h  -> 21:30
-- A calendar unit (d, w, mo, q, y) on a TIME is a validation error — a time-of-day
-- has no calendar context (docs/TEMPORAL.md, "Applying a duration -> TIME").
SELECT
  c.time_sec_from_midnight AS base_time
, c.time_sec_from_midnight + 7200 AS plus_two_hours
, c.time_sec_from_midnight + 5400 AS plus_ninety_minutes
, c.time_sec_from_midnight - 10800 AS minus_three_hours
, c.time_sec_from_midnight + 45 * 60 AS plus_interval
FROM
 check_temporal c