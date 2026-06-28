-- time_from_date: read a TIME held in the time part of a DATE column (TIME_FROM_DATE encoding).
SELECT
  c.time_from_date AS wall_time
FROM
 check_temporal c