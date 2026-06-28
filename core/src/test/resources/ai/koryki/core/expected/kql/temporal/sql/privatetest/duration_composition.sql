-- Duration literals across the unit classes, including combined calendar+clock compositions, rendered
-- as the dialect's interval literal (no normalization across the calendar/clock boundary).
SELECT
  INTERVAL '1 hour 2 minute 3 second' AS time_duration
, INTERVAL '1 day' + INTERVAL '1 hour 2 minute 3 second' AS day_time_duration
, INTERVAL '1 year 2 month' AS year_month_duration
, INTERVAL '1 year 2 month 3 day' AS year_month_day_duration
, INTERVAL '1 year 2 month 1 day' + INTERVAL '1 hour 2 minute 3 second' AS mixed_duration
FROM
 check_temporal c