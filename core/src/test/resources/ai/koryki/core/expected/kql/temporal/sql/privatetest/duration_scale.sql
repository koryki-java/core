-- integer × DURATION → DURATION (component-wise scale). A constant factor × duration literal is folded
-- to a scaled duration literal, so it renders portably (no `n * INTERVAL`) and decodes as an Interval.
-- duckdb-first.
SELECT
  INTERVAL '2 hour 4 minute 6 second' AS twice_clock
, INTERVAL '3 year 6 month' AS thrice_year_month
, c.date_date + INTERVAL '2 day' AS date_plus_two_days
FROM
 check_temporal c
WHERE
  c.nr = 1