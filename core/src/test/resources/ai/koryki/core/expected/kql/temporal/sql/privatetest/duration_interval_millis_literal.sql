SELECT
  c.interval_millis AS interval_millis
FROM
 check_temporal c
WHERE
  c.interval_millis > 3723000