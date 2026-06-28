-- Business-facing duration rendering (WordedLocaleFormat WIDE, en): variable calendar fields as
-- words, the fixed clock remainder as HH:MM:SS. Contrast with the canonical koryki notation
-- (1h2min3s …) that StableFormat keeps for golden determinism.
SELECT
  INTERVAL '1 hour 2 minute 3 second' AS clock_only
, INTERVAL '1 year 2 month' AS year_month
, INTERVAL '1 year 2 month 3 day' AS year_month_day
, INTERVAL '1 day' + INTERVAL '1 hour 2 minute 3 second' AS day_and_clock
, INTERVAL '1 year 2 month 1 day' + INTERVAL '1 hour 2 minute 3 second' AS full
FROM
 check_temporal c
WHERE
  c.nr = 1