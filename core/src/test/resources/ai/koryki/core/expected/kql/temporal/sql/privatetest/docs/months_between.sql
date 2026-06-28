-- months_between: whole completed months between two dates.
SELECT
  (CAST(EXTRACT(YEAR FROM age(DATE '2023-03-31', DATE '2023-01-31')) * 12 + EXTRACT(MONTH FROM age(DATE '2023-03-31', DATE '2023-01-31')) AS INTEGER)) AS whole_two
FROM
 check_temporal c