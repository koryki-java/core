-- Naive DATE + duration: a calendar-only duration keeps DATE; a clock component promotes to TIMESTAMP.
SELECT
  c.date_date AS date_date
, c.date_date + INTERVAL '1 year 2 month 1 day 1 hour 30 minute' AS plus_1year2month1day90min
FROM
 check_temporal c