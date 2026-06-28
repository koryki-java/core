-- wall-clock(zone) DATE storage: a column declared in a named zone, converted declared-zone -> model
-- zone at the storage boundary (docs/TEMPORAL.md). A wall-clock date is anchored at start-of-day in its
-- zone, so it can land on the adjacent model-zone day. KQL stays zone-free; the conversion is the
-- engine's job. Only unambiguous instants (nr 1,2) are asserted (see zoned_wallclock_timestamp).
-- SQLite has no time-zone database, so wall-clock(zone) is impossible there and is permanently skipped.
SELECT
  CAST(((CAST(c.date_new_york AS TIMESTAMP) AT TIME ZONE 'America/New_York') AT TIME ZONE 'UTC') AS DATE) AS date_new_york
, CAST(((CAST(c.date_new_york AS TIMESTAMP) AT TIME ZONE 'America/New_York') AT TIME ZONE 'UTC') AS DATE) + INTERVAL '3 month' AS date_new_york_plus1q
, CAST(((CAST(c.date_new_york AS TIMESTAMP) AT TIME ZONE 'America/New_York') AT TIME ZONE 'UTC') AS DATE) + INTERVAL '1 hour' AS date_new_york_plus1h
FROM
 check_temporal c
WHERE
  c.nr <= 2