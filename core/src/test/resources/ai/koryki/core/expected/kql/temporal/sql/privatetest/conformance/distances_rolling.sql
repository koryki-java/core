-- TEMPORAL.md normative examples as result assertions (java.time reference).
-- Uniform-rendering subset: distances + end functions render identically across
-- dialects. Clamping (month_add) and begin (month_begin) are deferred until the
-- DATE->TIMESTAMP type promotion (task #22) is fixed, since their rendering diverges.
SELECT
  (CAST(EXTRACT(YEAR FROM age(DATE '2023-03-31', DATE '2023-01-31')) * 12 + EXTRACT(MONTH FROM age(DATE '2023-03-31', DATE '2023-01-31')) AS INTEGER)) AS whole_two
, (CAST(EXTRACT(YEAR FROM age(DATE '2023-03-30', DATE '2023-01-31')) * 12 + EXTRACT(MONTH FROM age(DATE '2023-03-30', DATE '2023-01-31')) AS INTEGER)) AS not_complete_one
, date_diff('day', DATE '2023-03-01', DATE '2023-02-01') AS signed_minus28
, last_day(DATE '2024-02-10') AS end_leap
, last_day(date_trunc('quarter', DATE '2024-05-20') + INTERVAL 2 MONTH) AS end_q2
, last_day(date_trunc('year', DATE '2024-05-20') + INTERVAL 11 MONTH) AS end_year
FROM
 check_temporal c