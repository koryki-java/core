-- EPOCH-encoded timestamp column ± duration: the epoch integer is materialized to a TIMESTAMP
-- (epochToTimestamp) before the duration is applied, instead of `<bigint> + INTERVAL ...`.
-- duckdb-first.
SELECT
  make_timestamp((c.timestamp_unix_epoche * 1000000)) + INTERVAL '1 day' AS unix_plus_one_day
, make_timestamp((c.timestamp_java_epoche * 1000)) - INTERVAL '2 hour' AS java_minus_two_hours
FROM
 check_temporal c
WHERE
  c.nr = 1