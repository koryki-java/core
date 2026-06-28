SELECT
  c.interval_interval AS interval_interval
FROM
 check_temporal c
WHERE
  c.interval_interval > INTERVAL '1 hour 2 minute 3 second'