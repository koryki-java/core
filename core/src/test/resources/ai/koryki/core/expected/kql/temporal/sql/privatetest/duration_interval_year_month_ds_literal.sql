-- intentionally ignored in dialects not supporting required INTERVALs
SELECT
  c.interval_year_month AS interval_year_month
FROM
 check_temporal c
WHERE
  c.interval_year_month > INTERVAL '1 hour 2 minute 3 second'