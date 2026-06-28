SELECT
  c.interval_seconds AS interval_seconds
FROM
 check_temporal c
WHERE
  c.interval_seconds > 3723