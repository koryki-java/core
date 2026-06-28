-- intentionally ignored in dialects not supporting required INTERVALs
SELECT
  c.interval_day_second AS interval_day_second
FROM
 check_temporal c
WHERE
  c.interval_day_second > INTERVAL '1 year 2 month'